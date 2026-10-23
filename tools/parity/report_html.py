#!/usr/bin/env python3
"""Render the self-contained parity HTML report (inline CSS, base64 screenshots).

  python3 tools/parity/report_html.py --raw docs/migration/parity/raw --shots docs/migration/parity/screenshots \
      --meta docs/migration/parity/raw/report-meta.json --out docs/migration/parity/parity-report.html

raw/ must contain legacy.json, migrated.json, diff.json (diff.py legacy vs migrated) and optionally
legacy-vs-baseline.json. report-meta.json holds environment, suite counts and classified differences.
"""
import argparse
import base64
import html
import json
import os

CSS = """
body{font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;margin:0;color:#1f2937;background:#f5f6f8}
main{max-width:1280px;margin:0 auto;padding:24px 32px 64px}
h1{font-size:26px;margin:0 0 4px}h2{font-size:20px;margin:36px 0 10px;border-bottom:2px solid #e5e7eb;padding-bottom:6px}
h3{font-size:16px;margin:22px 0 8px}
.sub{color:#6b7280;margin:0 0 18px}
table{border-collapse:collapse;width:100%;background:#fff;font-size:13px;margin:8px 0}
th,td{border:1px solid #e5e7eb;padding:5px 8px;text-align:left;vertical-align:top}
th{background:#f3f4f6;font-weight:600}
code,pre{font-family:SFMono-Regular,Menlo,Consolas,monospace;font-size:12px}
pre{background:#111827;color:#e5e7eb;padding:8px;border-radius:4px;overflow:auto;max-height:220px;margin:4px 0;white-space:pre-wrap}
.cards{display:flex;gap:12px;flex-wrap:wrap;margin:12px 0}
.card{background:#fff;border:1px solid #e5e7eb;border-radius:6px;padding:12px 16px;min-width:170px}
.card b{display:block;font-size:24px}
.pass{color:#047857;font-weight:600}.fail{color:#b91c1c;font-weight:600}.info{color:#1d4ed8;font-weight:600}.warn{color:#b45309;font-weight:600}
.ok{background:#ecfdf5}.bad{background:#fef2f2}.inf{background:#eff6ff}
.shots{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:18px}
.shots figure{margin:0;background:#fff;border:1px solid #e5e7eb;padding:6px}
.shots img{width:100%;display:block}
figcaption{font-size:12px;color:#6b7280;padding-top:4px}
.split{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.box{background:#fff;border:1px solid #e5e7eb;border-radius:6px;padding:12px 16px}
.box.green{border-left:5px solid #10b981}.box.amber{border-left:5px solid #f59e0b}
details summary{cursor:pointer}
"""


def esc(s):
    return html.escape(str(s))


def img(path):
    with open(path, "rb") as fh:
        return "data:image/png;base64," + base64.b64encode(fh.read()).decode()


def table(headers, rows, cls=None):
    out = ["<table><tr>%s</tr>" % "".join("<th>%s</th>" % esc(h) for h in headers)]
    for i, r in enumerate(rows):
        c = (cls[i] if cls else "")
        out.append("<tr class='%s'>%s</tr>" % (c, "".join("<td>%s</td>" % v for v in r)))
    out.append("</table>")
    return "".join(out)


def snap_summary(v):
    if not isinstance(v, dict):
        return esc(v)
    j = v.get("json")
    if isinstance(j, list):
        extra = ""
        if j and isinstance(j[0], dict) and "runningBalance" in j[0]:
            extra = "; latest runningBalance %s" % j[0].get("runningBalance")
        if j and isinstance(j[0], dict) and "applicationStatus" in j[0]:
            extra = "; statuses %s" % sorted({x.get("applicationStatus") for x in j})
        if j and isinstance(j[0], dict) and "userProfile" in j[0]:
            extra = "; %s" % ", ".join(sorted(x["userProfile"].get("emailAddress", "?") for x in j))
        return "HTTP %s, %d rows%s" % (v.get("status"), len(j), esc(extra))
    if isinstance(j, dict):
        bits = ["%s=%s" % (k, j[k]) for k in ("accountNumber", "name", "currentBalance", "openingBalance") if k in j]
        return "HTTP %s, %s" % (v.get("status"), esc(", ".join(bits)))
    return "HTTP %s" % v.get("status")


def owner_key(field, keys):
    """Longest snapshot key the flattened diff field belongs to (x.transactions must not taint x)."""
    hits = [k for k in keys if field == k or field.startswith((k + ".", k + "["))]
    return max(hits, key=len) if hits else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", required=True)
    ap.add_argument("--shots", required=True)
    ap.add_argument("--meta", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    L = json.load(open(os.path.join(a.raw, "legacy.json")))
    M = json.load(open(os.path.join(a.raw, "migrated.json")))
    D = json.load(open(os.path.join(a.raw, "diff.json")))
    meta = json.load(open(a.meta))
    bpath = os.path.join(a.raw, "legacy-vs-baseline.json")
    B = json.load(open(bpath)) if os.path.exists(bpath) else None
    ls, ms = {s["id"]: s for s in L["steps"]}, {s["id"]: s for s in M["steps"]}
    S = D["summary"]
    identical = sum(1 for s in D["steps"] if s["result"] == "PASS")
    h = ["<!doctype html><html><head><meta charset='utf-8'><title>Digital Bank Java 21 parity report (round 1)</title>",
         "<style>%s</style></head><body><main>" % CSS,
         "<h1>Digital Bank: legacy vs Java 21 end-to-end parity report (round 1)</h1>",
         "<p class='sub'>%s</p>" % esc(meta["subtitle"])]
    h.append("<div class='cards'>")
    for label, val, c in (("flows replayed", S["steps"], ""), ("identical (normalised diff empty)", identical, "pass"),
                          ("different", S["fail"], "fail" if S["fail"] else "pass"), ("informational", S["info"], "info"),
                          ("DB snapshot diffs", S["db_snapshot_diffs"], "fail" if S["db_snapshot_diffs"] else "pass"),
                          ("migration regressions", len(meta["regressions"]), "fail" if meta["regressions"] else "pass")):
        h.append("<div class='card'><span>%s</span><b class='%s'>%s</b></div>" % (esc(label), c, val))
    h.append("</div>")

    h.append("<h2>1. Verdict</h2><div class='split'><div class='box green'><h3>Verified parity</h3><ul>%s</ul></div>"
             "<div class='box amber'><h3>Known limitations / pre-existing defects / environment</h3><ul>%s</ul></div></div>"
             % ("".join("<li>%s</li>" % esc(x) for x in meta["verified"]),
                "".join("<li>%s</li>" % esc(x) for x in meta["limitations"])))
    h.append("<h3>Migration regressions</h3><p>%s</p>" % (
        "<span class='pass'>None observed.</span>" if not meta["regressions"]
        else "<ul>%s</ul>" % "".join("<li class='fail'>%s</li>" % esc(x) for x in meta["regressions"])))

    h.append("<h2>2. Scope and method</h2>")
    h.append("".join("<p>%s</p>" % esc(p) for p in meta["scope"]))

    h.append("<h2>3. Environment</h2>")
    env = meta["environment"]
    h.append(table(["", "Legacy", "Migrated"], [[esc(k), esc(v[0]), esc(v[1])] for k, v in env]))

    h.append("<h2>4. Test-suite counts</h2>")
    h.append(table(["Suite", "Side", "Run", "Passed", "Failed", "Errors", "Skipped / not run", "Notes"],
                   [[esc(r["suite"]), esc(r["side"]), r["run"], r["passed"], r["failed"], r["errors"], r["skipped"], esc(r["notes"])]
                    for r in meta["suites"]]))

    h.append("<h2>5. Per-flow results (legacy vs migrated)</h2>")
    h.append("<p>Each step was replayed on both stacks; <b>PASS</b> = the normalised diff was empty. "
             "Compare modes: full = status + redirect + content type + normalised JSON/HTML summary; seed = full with "
             "Faker-random seed money masked; credit-decision = full with Math.random() credit terms masked; status = HTTP "
             "status only; info = reported, never fails.</p>")
    rows, cls = [], []
    for s in D["steps"]:
        sid = s["id"]
        l, m = ls.get(sid, {}), ms.get(sid, {})
        rq = l.get("request", {})
        res = s["result"]
        snippet = ""
        if s.get("diffs"):
            snippet = "<details><summary>%d field(s)</summary><pre>%s</pre></details>" % (
                len(s["diffs"]), esc("\n".join("%s\n  legacy:   %s\n  migrated: %s" % (d["field"], str(d["legacy"])[:160], str(d["migrated"])[:160])
                                               for d in s["diffs"][:12])))
        rows.append([esc(sid), esc(s.get("flow")), "<code>%s %s</code>" % (esc(rq.get("method", "")), esc(rq.get("path", ""))),
                     esc(s.get("compare")), esc(l.get("response", {}).get("status")), esc(m.get("response", {}).get("status")),
                     "<span class='%s'>%s</span>" % ({"PASS": "pass", "INFO": "info"}.get(res, "fail"), "PASS" if res == "PASS" else ("INFO (DIFF)" if res == "INFO" else "DIFF")),
                     snippet])
        cls.append({"PASS": "", "INFO": "inf"}.get(res, "bad"))
    h.append(table(["Step", "Flow", "Request", "Compare", "Legacy status", "Migrated status", "Result", "Diff"], rows, cls))
    if B:
        bs = B["summary"]
        h.append("<p>Determinism check: this round's live legacy run vs the committed baseline "
                 "<code>docs/migration/baseline/legacy-run.json</code>: %d steps, %d PASS, %d fail, %d DB snapshot diffs.</p>"
                 % (bs["steps"], bs["pass"], bs["fail"], bs["db_snapshot_diffs"]))

    h.append("<h2>6. DB-state comparison after the flows</h2>")
    h.append("<p>Read through the admin REST API after all flows (users, accounts, balances, transactions, credit "
             "applications, Digital Credit users), normalised the same way on both sides and diffed field by field: "
             "<b class='%s'>%d differing fields</b>.</p>" % ("pass" if not D["db_snapshot"] else "fail", len(D["db_snapshot"])))
    rows = []
    keys = list(L["db_snapshot"])
    diff_keys = {owner_key(d["field"], keys) for d in D["db_snapshot"]}
    for k in keys:
        rows.append([esc(k), snap_summary(L["db_snapshot"][k]), snap_summary(M["db_snapshot"].get(k)),
                     "<span class='fail'>DIFF</span>" if k in diff_keys else "<span class='pass'>identical</span>"])
    h.append(table(["Snapshot", "Legacy", "Migrated", "Result"], rows))
    lm, mm = L["meta"].get("mock_requests") or [], M["meta"].get("mock_requests") or []
    h.append("<p>Outbound calls to the external-service mock (ATM / VISA / OBP): legacy %d, migrated %d.</p>" % (len(lm), len(mm)))

    h.append("<h2>7. Screenshots (headless Chrome, side by side)</h2><p>%s</p>" % esc(meta["screens_note"]))
    names = sorted(f for f in os.listdir(os.path.join(a.shots, "legacy")) if f.endswith(".png"))
    for f in names:
        cap = meta["screen_captions"].get(f[:-4], f[:-4])
        h.append("<h3>%s</h3><div class='shots'>" % esc(cap))
        for side in ("legacy", "migrated"):
            p = os.path.join(a.shots, side, f)
            if os.path.exists(p):
                h.append("<figure><img alt='%s %s' src='%s'><figcaption>%s</figcaption></figure>" % (side, esc(cap), img(p), side))
        h.append("</div>")

    h.append("<h2>8. Differences and root cause</h2>")
    h.append(table(["Item", "Classification", "Root cause / evidence"],
                   [[esc(d["item"]), esc(d["class"]), esc(d["cause"])] for d in meta["differences"]]))
    h.append("<h2>9. Raw results</h2><ul>%s</ul>" % "".join("<li><code>%s</code></li>" % esc(x) for x in meta["raw_files"]))
    h.append("</main></body></html>")
    with open(a.out, "w") as fh:
        fh.write("\n".join(h))
    print("wrote", a.out, os.path.getsize(a.out), "bytes")


if __name__ == "__main__":
    main()
