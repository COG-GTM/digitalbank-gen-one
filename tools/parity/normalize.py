"""Normalisation of dynamic values so legacy and migrated runs can be diffed byte-for-byte."""
import json
import re
from html.parser import HTMLParser

MASK_MONEY_RE = re.compile(r"\$?-?\$?\d[\d,]*\.\d{1,2}")
RULES = [
    (re.compile(r"eyJ[\w-]+\.[\w-]+\.[\w-]+"), "{JWT}"),
    (re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", re.I), "{UUID}"),
    (re.compile(r"\b20[2-9]\d-\d{2}-\d{2}[T ]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?"), "{TS}"),
    (re.compile(r"\b20[2-9]\d-\d{2}-\d{2}\b"), "{DATE}"),
    (re.compile(r"\b\d{2}/\d{2}/20[2-9]\d(?: \d{2}:\d{2}(?::\d{2})?)?"), "{DATE}"),
    (re.compile(r"\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*,? [A-Z][a-z]{2} \d{1,2},? (?:\d{2}:\d{2}:\d{2} [A-Z]{3,4} )?20[2-9]\d\b"), "{DATE}"),
    (re.compile(r"\b[A-Z][a-z]{2,8} \d{1,2}, 20[2-9]\d\b"), "{DATE}"),
    (re.compile(r"\b1[6-9]\d{11}\b"), "{EPOCH_MS}"),
    (re.compile(r"\b1[6-9]\d{8}\b"), "{EPOCH_S}"),
    (re.compile(r"JSESSIONID=[^;,\s]+"), "JSESSIONID={SID}"),
    (re.compile(r"remember-me=[^;,\s]+"), "remember-me={RM}"),
    (re.compile(r"\b\d{2}:\d{2}:\d{2}(?:\.\d+)?\b"), "{TIME}"),
]
# keys whose numeric values are surrogate keys from the shared hibernate sequence (seed data is Faker-random,
# so absolute ids differ run to run); they are replaced by stable aliases learned during the run.
ID_KEY_RE = re.compile(r"(^id$|Id$|^userId$)")


class Normalizer:
    def __init__(self, base_urls):
        self.base_urls = base_urls  # {"{BANK}": "https://localhost:8443/bank", ...}
        self.aliases = {}           # str(id) -> "{ACCT:parity-checking}" etc.
        self.mask_money = False

    def alias(self, value, name):
        if value is not None and str(value) not in self.aliases:
            self.aliases[str(value)] = "{%s}" % name

    def text(self, s):
        if s is None:
            return None
        for token, url in self.base_urls.items():
            s = s.replace(url, token)
            hostport = url.split("://", 1)[-1].split("/", 1)[0]
            s = s.replace(hostport, token + "_HOSTPORT")
        for rx, rep in RULES:
            s = rx.sub(rep, s)
        if self.mask_money:
            s = MASK_MONEY_RE.sub("{MONEY}", s)
        return s

    def id_value(self, v):
        if isinstance(v, bool) or v is None:
            return v
        key = str(v)
        if key in self.aliases:
            return self.aliases[key]
        return "{ID}" if isinstance(v, int) or key.isdigit() else self.text(key)

    def json(self, obj, key=None):
        if isinstance(obj, dict):
            return {k: self.json(v, k) for k, v in sorted(obj.items())}
        if isinstance(obj, list):
            return [self.json(v, key) for v in obj]
        if key and ID_KEY_RE.search(key) and (isinstance(obj, int) or (isinstance(obj, str) and obj.isdigit())):
            return self.id_value(obj)
        if isinstance(obj, str):
            return self.text(obj)
        if self.mask_money and isinstance(obj, float):
            return "{MONEY}"
        if self.mask_money and key and re.search(r"(?i)balance|amount|limit", key) and isinstance(obj, (int, float)):
            return "{MONEY}"
        return obj

    def url(self, u):
        if u is None:
            return None
        u = self.text(u)
        return re.sub(r"(?<=[=/])(\d+)\b", lambda m: self.aliases.get(m.group(1), m.group(1)) if m.group(1) in self.aliases else m.group(1), u)


class _PageParser(HTMLParser):
    VOID = {"input", "br", "img", "meta", "link", "hr"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.title, self.forms, self.alerts, self.text = "", [], [], []
        self._skip = 0
        self._in_title = False
        self._form = None
        self._select = None
        self._alert_depth = []
        self._stack = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag in ("script", "style"):
            self._skip += 1
        if tag == "title":
            self._in_title = True
        cls = a.get("class") or ""
        if tag not in self.VOID:
            self._stack.append(tag)
            if re.search(r"\balert\b|\balert-|error-msg|success-msg", cls):
                self._alert_depth.append([len(self._stack), []])
        if tag == "form":
            self._form = {"action": a.get("action"), "method": (a.get("method") or "get").lower(), "fields": []}
            self.forms.append(self._form)
        if self._form is not None and tag in ("input", "textarea", "select", "button"):
            f = {"tag": tag, "type": a.get("type"), "name": a.get("name"), "id": a.get("id")}
            if a.get("type") in ("radio", "checkbox", "hidden", "submit"):
                f["value"] = a.get("value")
            if "required" in a:
                f["required"] = True
            if tag == "select":
                f["options"] = []
                self._select = f
            self._form["fields"].append(f)
        if tag == "option" and self._select is not None:
            self._select["options"].append(a.get("value"))

    def handle_endtag(self, tag):
        if tag in ("script", "style"):
            self._skip = max(0, self._skip - 1)
        if tag == "title":
            self._in_title = False
        if tag == "form":
            self._form = None
        if tag == "select":
            self._select = None
        if tag in self._stack:
            while self._stack:
                t = self._stack.pop()
                if self._alert_depth and self._alert_depth[-1][0] > len(self._stack):
                    depth, parts = self._alert_depth.pop()
                    txt = " ".join(" ".join(parts).split())
                    if txt:
                        self.alerts.append(txt)
                if t == tag:
                    break

    def handle_data(self, data):
        if self._skip:
            return
        if self._in_title:
            self.title += data
        for _, parts in self._alert_depth:
            parts.append(data)
        if data.strip():
            self.text.append(data.strip())


def summarize_html(html, norm):
    p = _PageParser()
    try:
        p.feed(html)
    except Exception:  # pragma: no cover - malformed html still yields partial summary
        pass
    forms = []
    for f in p.forms:
        fields = []
        for fld in f["fields"]:
            fld = dict(fld)
            if "options" in fld:
                fld["options"] = [norm.aliases.get(o, o) if o else o for o in fld["options"]]
            if fld.get("value") is not None:
                fld["value"] = norm.aliases.get(fld["value"], norm.text(fld["value"]))
            fields.append(fld)
        forms.append({"action": norm.url(f["action"]), "method": f["method"], "fields": fields})
    text = norm.text(" ".join(" ".join(p.text).split()))
    return {
        "title": " ".join(p.title.split()),
        "forms": forms,
        "alerts": [norm.text(a) for a in p.alerts],
        "text": text,
    }


def canonical(obj):
    return json.dumps(obj, sort_keys=True, ensure_ascii=False)


PARITY_EMAIL = "parity.tester@demo.io"
SEED_PROFILE_FIELDS = ("address", "dob", "firstName", "lastName", "gender", "title", "homePhone", "mobilePhone",
                       "workPhone", "locality", "postalCode", "region", "ssn", "country")
SEED_DESC_RE = re.compile(r"\(([A-Z]{3})\) - .*?(?= \{MONEY\}| \$|-?\{MONEY\}|$)")
CREDIT_DETAIL_RE = re.compile(r"Digital Credit has reviewed your application[^.]*\.")


def stabilize(o, mode):
    """Compare-time canonicalisation (applied by diff.py to BOTH runs):
    - seed users' profiles are Faker-random (SampleDataService/DefaultData) -> masked unless it is the parity user
    - role lists and 4xx validation-message lists come from Java Sets -> order-insensitive
    - mode 'seed': Faker merchant descriptions in seeded transaction tables masked
    - mode 'credit-decision': credit terms sentence (random intro/standard APR) masked
    """
    if isinstance(o, dict):
        out = {k: stabilize(v, mode) for k, v in o.items()}
        if "ssn" in out and "emailAddress" in out and out.get("emailAddress") != PARITY_EMAIL:
            for f in SEED_PROFILE_FIELDS:
                if f in out:
                    out[f] = "{SEED}"
        return out
    if isinstance(o, list):
        items = [stabilize(v, mode) for v in o]
        if items and all(isinstance(i, dict) and set(i) == {"authority"} for i in items):
            items = sorted(items, key=lambda i: i["authority"])
        elif items and all(isinstance(i, str) for i in items) and any(
                w in " ".join(items) for w in ("must", "required", "size ")):
            items = sorted(items)
        return items
    if isinstance(o, str):
        if mode in ("seed", "credit-decision"):
            if mode == "seed":
                o = SEED_DESC_RE.sub(lambda m: "(%s) - {DESC}" % m.group(1), o)
                o = re.sub(r"(Transactions Date Category Description Amount Balance).*", r"\1 {SEED_ROWS}", o)
            o = CREDIT_DETAIL_RE.sub("{CREDIT_TERMS}", o)
        return o
    return o


def redact(obj, secrets):
    """Replace every occurrence of a known credential with {PASSWORD} (applied to request and response data)."""
    if isinstance(obj, dict):
        return {k: redact(v, secrets) for k, v in obj.items()}
    if isinstance(obj, list):
        return [redact(v, secrets) for v in obj]
    if isinstance(obj, str):
        for sec in secrets:
            obj = obj.replace(sec, "{PASSWORD}")
    return obj
