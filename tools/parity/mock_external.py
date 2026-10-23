#!/usr/bin/env python3
"""Deterministic mock of the external services Digital Bank calls.

Serves the recorded CodeSV fixtures under bank/src/test/resources/spec/**/vs/:
  * ATM search  (Synapse)   GET  /v3.1/nodes/atms?zip=..&radius=10
  * VISA Direct             GET  /visadirect/fundstransfer/v1/pullfundstransactions?idcode=..&amount=..
  * Open Banking (OBP)      POST /my/logins/direct, GET /obp/v4.0.0/banks[/..]

Point the bank at it with (plain http, the services use a default RestTemplate):
  IO_DIGISIC_BANK_ATM_PROTOCOL=http  IO_DIGISIC_BANK_ATM_HOST=localhost  IO_DIGISIC_BANK_ATM_PORT=<port>
  IO_DIGISIC_BANK_VISA_PROTOCOL=http IO_DIGISIC_BANK_VISA_HOST=localhost IO_DIGISIC_BANK_VISA_PORT=<port>
  IO_DIGISIC_BANK_OBP_ENABLED=true IO_DIGISIC_BANK_OBP_PROTOCOL=http IO_DIGISIC_BANK_OBP_HOST=localhost IO_DIGISIC_BANK_OBP_PORT=<port>

Usage: mock_external.py [--port 8099] [--spec-dir bank/src/test/resources/spec]
"""
import argparse
import json
import os
import re
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_SPEC = os.path.normpath(os.path.join(HERE, "..", "..", "bank", "src", "test", "resources", "spec"))


def load_fixtures(spec_dir):
    """Return list of (method, path, query-dict, status, headers, body) from *-req.txt/*-rsp.txt pairs."""
    fixtures = []
    for root, _dirs, files in os.walk(spec_dir):
        for f in sorted(files):
            if not f.endswith("-req.txt"):
                continue
            req_path = os.path.join(root, f)
            rsp_path = req_path[: -len("-req.txt")] + "-rsp.txt"
            if not os.path.exists(rsp_path):
                continue
            with open(req_path, encoding="utf-8") as fh:
                req_line = fh.readline().strip()
            method, target, _ = req_line.split(" ", 2)
            u = urlparse(target)
            query = {k: v[0] for k, v in parse_qs(u.query).items()}
            with open(rsp_path, encoding="utf-8") as fh:
                raw = fh.read()
            head, _, body = raw.partition("\n\n")
            lines = head.splitlines()
            status = int(lines[0].split(" ")[1])
            headers = {}
            for line in lines[1:]:
                if ":" in line:
                    k, v = line.split(":", 1)
                    if k.strip().lower() in ("content-type",):
                        headers[k.strip()] = v.strip()
            fixtures.append({
                "method": method, "path": u.path, "query": query, "status": status,
                "headers": headers, "body": body.encode("utf-8"),
                "source": os.path.relpath(rsp_path, spec_dir),
            })
    return fixtures


class Handler(BaseHTTPRequestHandler):
    fixtures = []
    log = []
    lock = threading.Lock()
    protocol_version = "HTTP/1.1"

    def _match(self, method):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        best = None
        for fx in self.fixtures:
            if fx["method"] != method or fx["path"] != u.path:
                continue
            # every recorded query param must match exactly
            if all(q.get(k) == v for k, v in fx["query"].items()):
                score = len(fx["query"])
                if best is None or score > best[0]:
                    best = (score, fx)
        return best[1] if best else None, u, q

    def _send(self, status, body, ctype="application/json;charset=utf-8"):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _handle(self, method):
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
        fx, u, q = self._match(method)
        if u.path == "/__mock/requests":
            with self.lock:
                return self._send(200, json.dumps(self.log).encode())
        if u.path == "/__mock/health":
            return self._send(200, b'{"status":"UP"}')
        with self.lock:
            self.log.append({"method": method, "path": u.path, "query": q, "matched": fx["source"] if fx else None})
        if fx is None:
            # VISA: unknown amount for a known merchant -> behave like 'funds approved' (legacy default branch)
            if u.path.startswith("/visadirect/"):
                fx = next((f for f in self.fixtures if "funds approved" in f["source"]), None)
            elif u.path.startswith("/v3.1/nodes/atms"):
                fx = next((f for f in self.fixtures if "AtmSearch_ZeroResults" in f["source"]), None)
        if fx is None:
            return self._send(404, json.dumps({"error": "no fixture", "path": u.path, "query": q}).encode())
        self._send(fx["status"], fx["body"], fx["headers"].get("Content-Type", "application/json;charset=utf-8"))

    def do_GET(self):
        self._handle("GET")

    def do_POST(self):
        self._handle("POST")

    def log_message(self, fmt, *args):
        sys.stderr.write("[mock_external] " + (fmt % args) + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=int(os.environ.get("MOCK_EXTERNAL_PORT", "8099")))
    ap.add_argument("--bind", default="127.0.0.1")
    ap.add_argument("--spec-dir", default=DEFAULT_SPEC)
    a = ap.parse_args()
    Handler.fixtures = load_fixtures(a.spec_dir)
    sys.stderr.write("[mock_external] loaded %d fixtures from %s, listening on %s:%d\n"
                     % (len(Handler.fixtures), a.spec_dir, a.bind, a.port))
    ThreadingHTTPServer((a.bind, a.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
