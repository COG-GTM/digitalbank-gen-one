#!/usr/bin/env python3
"""Diff two replay.py result files (normally legacy vs migrated) and write a JSON report.

  python3 tools/parity/diff.py /tmp/parity/legacy.json /tmp/parity/migrated.json --out /tmp/parity/diff.json

Each step is compared according to its `compare` mode:
  full             status, redirect location(s), content type, normalised JSON body / HTML summary (title, forms,
                   alerts, visible text)
  seed             same as full; money values were masked at capture time (Faker-random seed transactions)
  credit-decision  same as full with money masked; the Approved/Declined decision itself is random in Digital
                   Credit (Math.random credit score), so the decision word is masked before comparing
  status           status code (after following redirects) only
  info             reported, never fails
Exit code 0 = parity, 1 = differences, 2 = usage / missing steps.
"""
import argparse
import difflib
import json
import re
import sys

from normalize import MASK_MONEY_RE, stabilize

DECISION = re.compile(r"\b(Approved|Declined|Submitted|Pending)\b")


def view(step):
    r = dict(step["response"])
    mode = step.get("compare", "full")
    if mode == "status":
        last = r.get("status")
        return {"status": last}
    if mode in ("seed", "credit-decision"):
        r = json.loads(MASK_MONEY_RE.sub("{MONEY}", json.dumps(r)))
    r = stabilize(r, mode)
    if mode == "credit-decision":
        r = mask_strings(r)
    return r


def mask_strings(o):
    """Mask the random credit decision and credit/risk scores inside string values."""
    if isinstance(o, dict):
        return {k: mask_strings(v) for k, v in o.items()}
    if isinstance(o, list):
        return [mask_strings(v) for v in o]
    if isinstance(o, str):
        return re.sub(r"\b\d{1,4}\b", "{N}", DECISION.sub("{DECISION}", o))
    return o


def flat(o, prefix=""):
    if isinstance(o, dict):
        for k, v in o.items():
            yield from flat(v, "%s.%s" % (prefix, k) if prefix else k)
    elif isinstance(o, list):
        for i, v in enumerate(o):
            yield from flat(v, "%s[%d]" % (prefix, i))
    else:
        yield prefix, o
        return
    if not o:
        yield prefix, "{EMPTY_%s}" % type(o).__name__.upper()


def compare(a, b):
    fa, fb = dict(flat(a)), dict(flat(b))
    out = []
    for k in sorted(set(fa) | set(fb)):
        if fa.get(k, "<missing>") != fb.get(k, "<missing>"):
            va, vb = fa.get(k, "<missing>"), fb.get(k, "<missing>")
            d = {"field": k, "legacy": va, "migrated": vb}
            if isinstance(va, str) and isinstance(vb, str) and (len(va) > 200 or len(vb) > 200):
                d["legacy"], d["migrated"] = va[:200] + "...", vb[:200] + "..."
                d["text_diff"] = list(difflib.unified_diff(re.split(r"(?<=[.!?×]) ", va), re.split(r"(?<=[.!?×]) ", vb),
                                                           "legacy", "migrated", lineterm="", n=0))[:40]
            out.append(d)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("legacy")
    ap.add_argument("migrated")
    ap.add_argument("--out", default="parity-diff.json")
    ap.add_argument("--only", help="regex of step ids to compare")
    a = ap.parse_args()
    L, M = json.load(open(a.legacy)), json.load(open(a.migrated))
    ls, ms = {s["id"]: s for s in L["steps"]}, {s["id"]: s for s in M["steps"]}
    results, failed = [], 0
    for sid, step in ls.items():
        if a.only and not re.search(a.only, sid):
            continue
        if sid not in ms:
            results.append({"id": sid, "result": "MISSING"}); failed += 1; continue
        diffs = compare(view(step), view(ms[sid]))
        mode = step.get("compare", "full")
        res = "PASS" if not diffs else ("INFO" if mode == "info" else "FAIL")
        failed += res == "FAIL"
        results.append({"id": sid, "flow": step.get("flow"), "compare": mode, "result": res, "diffs": diffs})
    extra = sorted(set(ms) - set(ls))
    snap = compare(mask_strings(stabilize(L.get("db_snapshot", {}), "credit-decision")),
                   mask_strings(stabilize(M.get("db_snapshot", {}), "credit-decision")))
    failed += bool(snap) + len(extra)
    failed += bool(L["meta"].get("error")) + bool(M["meta"].get("error"))
    summary = {"legacy": a.legacy, "migrated": a.migrated, "steps": len(results),
               "pass": sum(r["result"] == "PASS" for r in results), "fail": failed,
               "info": sum(r["result"] == "INFO" for r in results), "missing_in_migrated": [r["id"] for r in results if r["result"] == "MISSING"],
               "extra_in_migrated": extra, "db_snapshot_diffs": len(snap),
               "legacy_errors": L["meta"].get("error"), "migrated_errors": M["meta"].get("error")}
    json.dump({"summary": summary, "steps": results, "db_snapshot": snap}, open(a.out, "w"), indent=1)
    for r in results:
        if r["result"] not in ("PASS",):
            print("%-8s %s  %s" % (r["result"], r["id"], "; ".join(d["field"] for d in r.get("diffs", []))[:160]))
    print(json.dumps(summary, indent=1))
    sys.exit(2 if summary["missing_in_migrated"] else (1 if failed else 0))


if __name__ == "__main__":
    main()
