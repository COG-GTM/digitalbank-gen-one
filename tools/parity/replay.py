#!/usr/bin/env python3
"""Scripted replayer of every Digital Bank UI + REST flow (and the bank<->credit JMS round trip).

  python3 tools/parity/replay.py --bank https://localhost:8443/bank --credit https://localhost:3500/credit \
      --mock http://127.0.0.1:8099 --out /tmp/legacy.json

Run against a FRESHLY started stack (start_stack.sh): the flows register a new user, open accounts, move money,
change the password and apply for credit, so a second run against the same instance will diverge.
Output: JSON {meta, steps:[{id, flow, request, response{status, location, content_type, json|html}}], db_snapshot}.
"""
import argparse
import json
import re
import sys
import time

import requests
import urllib3

from normalize import Normalizer, redact, summarize_html

urllib3.disable_warnings()

PARITY_USER = {
    "title": "Mr.", "firstName": "Parity", "lastName": "Tester", "gender": "M", "dob": "01/15/1980",
    "ssn": "123-45-6789", "emailAddress": "parity.tester@demo.io", "password": "Demo123!",
    "address": "1 Parity Way", "locality": "Austin", "region": "TX", "postalCode": "78701", "country": "US",
    "homePhone": "(512) 555-0100", "mobilePhone": "(512) 555-0101", "workPhone": "(512) 555-0102",
}
NEW_PASSWORD = "Parity123!"
SEED_USER = ("jsmith@demo.io", "Demo123!")
ADMIN = ("admin@demo.io", "Demo123!")
OBP_ACCOUNT = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"


class Replayer:
    def __init__(self, bank, credit, mock, credit_timeout):
        self.bank, self.credit, self.mock = bank.rstrip("/"), credit.rstrip("/"), (mock or "").rstrip("/")
        self.norm = Normalizer({"{BANK}": self.bank, "{CREDIT}": self.credit})
        self.steps, self.flow = [], None
        self.credit_timeout = credit_timeout
        self.ui = self._session()
        self.tokens = {}

    @staticmethod
    def _session():
        s = requests.Session()
        s.verify = False
        s.headers["User-Agent"] = "digitalbank-parity/1.0"
        return s

    # ---------------- recording ----------------
    def call(self, sid, method, path, *, session=None, base=None, data=None, json_body=None, params=None,
             token=None, compare="full", follow=False, mask_money=False, note=None):
        s = session or self._session()
        url = (base or self.bank) + path
        headers = {"Accept": "application/json"} if token is not None or json_body is not None else {}
        if token:
            headers["Authorization"] = "Bearer " + token
        t0 = time.time()
        r = s.request(method, url, data=data, json=json_body, params=params, headers=headers,
                      allow_redirects=False, timeout=60)
        chain = []
        while follow and r.is_redirect and len(chain) < 5:
            chain.append({"status": r.status_code, "location": self.norm.url(r.headers.get("Location"))})
            r = s.get(requests.compat.urljoin(url, r.headers["Location"]), allow_redirects=False, timeout=60)
        self.norm.mask_money = mask_money
        ctype = (r.headers.get("Content-Type") or "").split(";")[0].strip().lower() or None
        resp = {"status": r.status_code, "location": self.norm.url(r.headers.get("Location")), "content_type": ctype}
        if chain:
            resp["redirects"] = chain
        cookies = sorted({c.split("=", 1)[0].strip() for c in r.headers.get("Set-Cookie", "").split(",") if "=" in c})
        if cookies:
            resp["set_cookie_names"] = [c for c in cookies if c in ("JSESSIONID", "remember-me")]
        body = None
        if ctype and "json" in ctype:
            try:
                body = r.json()
                resp["json"] = self.norm.json(body)
            except ValueError:
                resp["text"] = self.norm.text(r.text[:2000])
        elif ctype and "html" in ctype:
            resp["html"] = summarize_html(r.text, self.norm)
        elif r.text:
            resp["text"] = self.norm.text(r.text[:2000])
        self.norm.mask_money = False
        req = {"method": method, "path": self.norm.url(path), "base": "{CREDIT}" if base == self.credit else "{BANK}"}
        if params:
            req["params"] = {k: self.norm.aliases.get(str(v), v) for k, v in params.items()}
        if data:
            req["form"] = {k: self.norm.aliases.get(str(v), v) for k, v in data.items()}
        if json_body is not None:
            req["json"] = self.norm.json(json_body)
        self.steps.append({"id": sid, "flow": self.flow, "compare": compare, "note": note, "request": req,
                           "response": resp, "elapsed_ms": int((time.time() - t0) * 1000)})
        return r, body

    def form_options(self, path, select_name, session=None):
        r = (session or self.ui).get(self.bank + path, timeout=60)
        m = re.search(r'<select[^>]*name="%s"[^>]*>(.*?)</select>' % re.escape(select_name), r.text, re.S)
        return re.findall(r'<option[^>]*value="([^"]+)"[^>]*>\s*([^<]*)', m.group(1)) if m else []

    def radio_values(self, path, name):
        r = self.ui.get(self.bank + path, timeout=60)
        return re.findall(r'<input[^>]*type="radio"[^>]*name="%s"[^>]*value="([^"]+)"' % name, r.text) or \
            re.findall(r'<input[^>]*name="%s"[^>]*type="radio"[^>]*value="([^"]+)"' % name, r.text) or \
            re.findall(r'<input[^>]*value="([^"]+)"[^>]*name="%s"' % name, r.text)

    # ---------------- flows ----------------
    def run(self):
        self.flow_ui_auth()
        self.flow_ui_accounts()
        self.flow_ui_profile_password()
        self.flow_ui_external()
        self.flow_ui_credit()
        self.flow_ui_logout()
        self.flow_ui_seed_user()
        self.flow_api()
        self.flow_docs()
        snap = self.db_snapshot()
        self._realias()
        return {"meta": {"bank": "{BANK}", "credit": "{CREDIT}", "aliases": self.norm.aliases,
                         "mock_requests": self.mock_requests()}, "steps": self.steps, "db_snapshot": snap}

    def flow_ui_auth(self):
        self.flow = "ui.auth"
        anon = self._session()
        self.call("ui.root.anonymous", "GET", "/", session=anon)
        self.call("ui.home.anonymous", "GET", "/home", session=anon)
        self.call("ui.login.page", "GET", "/login", session=self.ui)
        self.call("ui.login.bad", "POST", "/login", session=self.ui,
                  data={"username": SEED_USER[0], "password": "WrongPassword1!"})
        self.call("ui.login.error.page", "GET", "/login?error", session=self.ui)
        self.flow = "ui.register"
        self.call("ui.signup.page", "GET", "/signup", session=self.ui)
        signup = {k: PARITY_USER[k] for k in ("title", "firstName", "lastName", "gender", "dob", "ssn", "emailAddress", "password")}
        self.call("ui.signup.submit", "POST", "/signup", session=self.ui, data=signup)
        reg = dict(PARITY_USER)
        reg["agreeTerms"] = "true"
        self.call("ui.register.submit", "POST", "/register", session=self.ui, data=reg, follow=True)
        self.call("ui.signup.duplicate_email", "POST", "/signup", session=self._session(), data=signup)
        self.flow = "ui.auth"
        self.call("ui.login.good", "POST", "/login", session=self.ui,
                  data={"username": PARITY_USER["emailAddress"], "password": PARITY_USER["password"]})
        self.call("ui.home.no_accounts", "GET", "/home", session=self.ui)

    def flow_ui_accounts(self):
        self.flow = "ui.accounts"
        self.call("ui.checking.add.page", "GET", "/account/checking-add", session=self.ui)
        chk_types = self.radio_values("/account/checking-add", "accountType")
        own_types = self.radio_values("/account/checking-add", "ownershipType")
        for i, v in enumerate(chk_types):
            self.norm.alias(v, "ACCT_TYPE:checking-%d" % i)
        for i, v in enumerate(own_types):
            self.norm.alias(v, "OWN_TYPE:%d" % i)
        self.call("ui.checking.add.below_min", "POST", "/account/checking-add", session=self.ui,
                  data={"accountType": chk_types[0], "ownershipType": own_types[0], "name": "Too Small", "openingBalance": "1.00"})
        self.call("ui.checking.add", "POST", "/account/checking-add", session=self.ui, follow=True,
                  data={"accountType": chk_types[0], "ownershipType": own_types[0], "name": "Parity Checking", "openingBalance": "500.00"})
        self.call("ui.savings.add.page", "GET", "/account/savings-add", session=self.ui)
        sav_types = self.radio_values("/account/savings-add", "accountType")
        for i, v in enumerate(sav_types):
            self.norm.alias(v, "ACCT_TYPE:savings-%d" % i)
        self.call("ui.savings.add", "POST", "/account/savings-add", session=self.ui, follow=True,
                  data={"accountType": sav_types[0], "ownershipType": own_types[-1], "name": "Parity Savings", "openingBalance": "2500.00"})
        accts = self.form_options("/account/deposit", "id")
        by_name = {label.strip().split(" ")[0] + label: v for v, label in accts}
        chk = next(v for v, label in accts if "Parity Checking" in label)
        sav = next(v for v, label in accts if "Parity Savings" in label)
        self.norm.alias(chk, "ACCT:parity-checking")
        self.norm.alias(sav, "ACCT:parity-savings")
        self.chk, self.sav = chk, sav
        self.call("ui.checking.view", "GET", "/account/checking-view", session=self.ui)
        self.call("ui.savings.view", "GET", "/account/savings-view", session=self.ui)
        self.call("ui.deposit.page", "GET", "/account/deposit", session=self.ui)
        self.call("ui.deposit", "POST", "/account/deposit", session=self.ui, follow=True, data={"id": chk, "amount": "125.50"})
        self.call("ui.deposit.zero", "POST", "/account/deposit", session=self.ui, follow=True, data={"id": chk, "amount": "0"})
        self.call("ui.withdraw.page", "GET", "/account/withdraw", session=self.ui)
        self.call("ui.withdraw", "POST", "/account/withdraw", session=self.ui, follow=True, data={"id": chk, "amount": "20.25"})
        self.call("ui.withdraw.insufficient_funds", "POST", "/account/withdraw", session=self.ui, follow=True,
                  data={"id": chk, "amount": "100000.00"})
        self.call("ui.xfer.page", "GET", "/account/xfer-between", session=self.ui)
        self.call("ui.xfer", "POST", "/account/xfer-between", session=self.ui, follow=True,
                  data={"fromAccount": sav, "toAccount": chk, "amount": "300.00"})
        self.call("ui.xfer.insufficient_funds", "POST", "/account/xfer-between", session=self.ui, follow=True,
                  data={"fromAccount": chk, "toAccount": sav, "amount": "999999.00"})
        self.call("ui.checking.history", "GET", "/account/checking-view", session=self.ui, params={"selectSwitch": chk})
        self.call("ui.savings.history", "GET", "/account/savings-view", session=self.ui, params={"selectSwitch": sav})
        self.call("ui.account.rename", "POST", "/account/update", session=self.ui, follow=True,
                  data={"id": chk, "newName": "Parity Checking Renamed"})
        self.call("ui.home.accounts", "GET", "/home", session=self.ui)

    def flow_ui_profile_password(self):
        self.flow = "ui.user"
        self.call("ui.profile.page", "GET", "/user/profile", session=self.ui)
        prof = {k: PARITY_USER[k] for k in ("title", "firstName", "lastName", "homePhone", "mobilePhone", "workPhone",
                                            "address", "locality", "region", "postalCode", "country")}
        prof["mobilePhone"] = "(512) 555-0199"
        self.call("ui.profile.update", "POST", "/user/profile", session=self.ui, data=prof)
        self.call("ui.password.page", "GET", "/user/password", session=self.ui)
        self.call("ui.password.wrong_current", "POST", "/user/password", session=self.ui,
                  data={"currentPassword": "Nope123!", "newPassword": NEW_PASSWORD, "confirmPassword": NEW_PASSWORD})
        self.call("ui.password.mismatch", "POST", "/user/password", session=self.ui,
                  data={"currentPassword": PARITY_USER["password"], "newPassword": NEW_PASSWORD, "confirmPassword": "Other123!"})
        self.call("ui.password.change", "POST", "/user/password", session=self.ui,
                  data={"currentPassword": PARITY_USER["password"], "newPassword": NEW_PASSWORD, "confirmPassword": NEW_PASSWORD})

    def flow_ui_external(self):
        self.flow = "ui.atm"
        for zip_, label in (("11749", "results"), ("12345", "zero_results"), ("54321", "service_unavailable")):
            self.call("ui.atm.search." + label, "POST", "/search/atm", session=self.ui, data={"zipcode": zip_})
        self.flow = "ui.visa"
        self.call("ui.visa.page", "GET", "/external/xfer-visa", session=self.ui)
        for acct, amt, label in (("ABCD1234ABCD123", "100", "approved"), ("ABCD1234ABCD123", "20", "not_approved"),
                                 ("ABCD1234ABCD123", "1000", "insufficient_funds"), ("ABCD1234ABCD456", "100", "invalid_merchant")):
            self.call("ui.visa." + label, "POST", "/external/xfer-visa-process", session=self.ui,
                      data={"extAccount": acct, "extAmount": amt})
        self.flow = "ui.obp"
        self.call("ui.obp.add.page", "GET", "/obp/obp-add", session=self.ui)
        self.call("ui.obp.add", "POST", "/obp/obp-add", session=self.ui,
                  data={"bankId": "RBOSGB2L", "username": "parity.obp", "password": "parity"})
        r, _ = self.call("ui.obp.add_account", "POST", "/obp/obp-add-acct", session=self.ui, follow=True,
                         data={"bankId": "RBOSGB2L", "accountId": OBP_ACCOUNT})
        m = re.search(r"selectSwitch=(\d+)", json.dumps(self.steps[-1]["response"]))
        if m:
            self.norm.alias(m.group(1), "OBP_ACCT:RBOSGB2L")
        self.call("ui.obp.view", "GET", "/obp/obp-view", session=self.ui)
        self.call("ui.home.with_obp", "GET", "/home", session=self.ui)

    def flow_ui_credit(self):
        self.flow = "ui.credit"
        self.call("ui.credit.app.page", "GET", "/credit/credit-app", session=self.ui)
        app = {k: PARITY_USER[k] for k in ("title", "firstName", "lastName", "dob", "ssn", "gender", "homePhone",
                                           "mobilePhone", "workPhone", "address", "locality", "region", "postalCode",
                                           "country", "emailAddress")}
        app.update({"employmentStatus": "Employed", "annualIncome": "250000", "monthlyMortgage": "0",
                    "monthlyAutoLoan": "0", "monthlyOtherLoan": "0", "minimumCreditCard": "0", "monthlySpend": "500",
                    "bankStatus": "Checking and Savings", "cashAdvance": "No", "balanceTransfer": "No",
                    "agreeTerms": "true", "_agreeTerms": "on"})
        self.call("ui.credit.app.submit", "POST", "/credit/credit-app", session=self.ui, data=app)
        # wait for: bank -> CREDIT.APP.REQUEST -> credit -> CREDIT.APP.RESPONSE -> bank (Submitted/Pending),
        # then credit processes after io.digisic.credit.app.process.time seconds and sends the decision.
        deadline, final = time.time() + self.credit_timeout, None
        while time.time() < deadline:
            r = self.ui.get(self.bank + "/credit/credit-app-status", timeout=60)
            if re.search(r">\s*(Approved|Declined)\s*<", r.text):
                final = re.search(r">\s*(Approved|Declined)\s*<", r.text).group(1)
                break
            time.sleep(2)
        self.credit_decision = final
        # Credit score is Math.random() and card numbers are random in Digital Credit: mask the decision details.
        self.call("ui.credit.status.final", "GET", "/credit/credit-app-status", session=self.ui, mask_money=True,
                  compare="credit-decision", note="decision=%s" % final)
        if final == "Approved":
            self.call("ui.credit.view", "GET", "/credit/credit-view", session=self.ui, mask_money=True, compare="credit-decision")

    def flow_ui_logout(self):
        self.flow = "ui.auth"
        self.call("ui.logout", "GET", "/logout", session=self.ui)
        self.call("ui.logout.page", "GET", "/login?logout", session=self.ui)
        self.call("ui.home.after_logout", "GET", "/home", session=self.ui)
        self.call("ui.login.old_password", "POST", "/login", session=self.ui,
                  data={"username": PARITY_USER["emailAddress"], "password": PARITY_USER["password"]})
        self.call("ui.login.new_password", "POST", "/login", session=self.ui,
                  data={"username": PARITY_USER["emailAddress"], "password": NEW_PASSWORD})
        self.call("ui.error.404", "GET", "/no-such-page", session=self.ui)
        self.call("ui.promotions", "GET", "/promotions", session=self.ui)

    def flow_ui_seed_user(self):
        self.flow = "ui.seed"
        s = self._session()
        self.call("ui.seed.login", "POST", "/login", session=s, data={"username": SEED_USER[0], "password": SEED_USER[1]})
        # seed transactions/balances are Faker-random in SampleDataService -> money masked
        self.call("ui.seed.home", "GET", "/home", session=s, mask_money=True, compare="seed")
        self.call("ui.seed.checking.view", "GET", "/account/checking-view", session=s, mask_money=True, compare="seed")
        self.call("ui.seed.savings.view", "GET", "/account/savings-view", session=s, mask_money=True, compare="seed")
        self.call("ui.seed.h2_console", "GET", "/h2-console/", session=s, compare="status")

    def flow_api(self):
        self.flow = "api.auth"
        self.call("api.health", "GET", "/api/v1/health")
        self.call("api.auth.bad_password", "POST", "/api/v1/auth", params={"username": ADMIN[0], "password": "bad"})
        self.call("api.auth.missing_params", "POST", "/api/v1/auth")
        for who, (u, p) in (("admin", ADMIN), ("seed", SEED_USER), ("parity", (PARITY_USER["emailAddress"], NEW_PASSWORD))):
            _, body = self.call("api.auth." + who, "POST", "/api/v1/auth", params={"username": u, "password": p})
            self.tokens[who] = (body or {}).get("authToken")
        adm, usr = self.tokens["admin"], self.tokens["seed"]
        self.call("api.no_token", "GET", "/api/v1/users", token="")
        self.call("api.bad_token", "GET", "/api/v1/users", token="not.a.jwt")
        self.call("api.parity_user_no_api_role", "GET", "/api/v1/user", token=self.tokens["parity"] or "")
        self.call("api.users.forbidden_for_user", "GET", "/api/v1/users", token=usr)
        self.flow = "api.users"
        _, users = self.call("api.users", "GET", "/api/v1/users", token=adm, mask_money=True)
        for u in users or []:
            self.norm.alias(u.get("id"), "USER:" + str(u.get("username")))
        self.steps[-1]["response"]["json"] = self.norm.json(users)  # re-normalise with user aliases
        self.call("api.user.current", "GET", "/api/v1/user", token=usr)
        self.call("api.user.find", "GET", "/api/v1/user/find", token=adm, params={"username": SEED_USER[0]})
        self.call("api.user.profile.current", "GET", "/api/v1/user/profile", token=usr)
        self.call("api.user.role.current", "GET", "/api/v1/user/role", token=usr)
        self.flow = "api.static"
        for p in ("type", "type/checking", "type/savings", "standing", "ownership/type", "transaction/state",
                  "transaction/type", "transaction/category"):
            self.call("api.data." + p.replace("/", "."), "GET", "/api/v1/data/account/" + p, token=adm)
        self.call("api.data.type.as_user", "GET", "/api/v1/data/account/type", token=usr,
                  note="legacy answers a denied API call with 405")
        self.flow = "api.accounts"
        self.call("api.accounts.all", "GET", "/api/v1/account", token=adm, mask_money=True, compare="seed")
        self.call("api.accounts.checking", "GET", "/api/v1/account/checking", token=adm, mask_money=True, compare="seed")
        self.call("api.user.accounts.current", "GET", "/api/v1/user/account", token=usr, mask_money=True, compare="seed")
        _, new_acct = self.call("api.account.create", "POST", "/api/v1/user/account", token=usr, json_body={
            "accountName": "API Checking", "openingDeposit": 1000.00, "accountTypeCode": "SCK", "ownerTypeCode": "IND"})
        nid = (new_acct or {}).get("id")
        self.norm.alias(nid, "ACCT:api-checking")
        if nid:
            self.steps[-1]["response"]["json"] = self.norm.json(new_acct)
        self.call("api.account.create.invalid", "POST", "/api/v1/user/account", token=usr,
                  json_body={"accountName": "", "openingDeposit": 1, "accountTypeCode": "XXX", "ownerTypeCode": "XXX"})
        self.call("api.account.get", "GET", "/api/v1/account/%s" % nid, token=usr)
        self.call("api.account.not_found", "GET", "/api/v1/account/999999999", token=adm)
        self.call("api.account.transaction.deposit", "POST", "/api/v1/account/%s/transaction" % nid, token=usr,
                  json_body={"amount": 50.00, "transactionTypeCode": "DPT", "description": "API deposit"})
        self.call("api.account.transaction.withdraw_insufficient", "POST", "/api/v1/account/%s/transaction" % nid,
                  token=usr, json_body={"amount": 999999.00, "transactionTypeCode": "WTH", "description": "too much"})
        self.call("api.account.transfer", "POST", "/api/v1/account/%s/transfer" % nid, token=usr,
                  json_body={"toAccountId": int(self.chk), "amount": 25.00})
        self.call("api.account.transfer.insufficient", "POST", "/api/v1/account/%s/transfer" % nid, token=usr,
                  json_body={"toAccountId": int(self.chk), "amount": 999999.00})
        self.call("api.account.transactions", "GET", "/api/v1/account/%s/transaction" % nid, token=usr)
        self.call("api.account.parity_checking.transactions", "GET", "/api/v1/account/%s/transaction" % self.chk, token=adm)
        self.call("api.account.rename", "PUT", "/api/v1/account/%s" % nid, token=usr, params={"newName": "API Checking 2"})
        self.call("api.account.owner", "GET", "/api/v1/account/%s/owner" % nid, token=adm)
        self.flow = "api.promotions"
        self.call("api.promotions.eligible", "POST", "/api/v1/promotions", token=adm,
                  json_body={"balance": 10000, "age": 40, "rating": 800, "years": 10, "accountType": "Platinum"})
        self.call("api.promotions.millennial", "POST", "/api/v1/promotions", token=adm,
                  json_body={"balance": 5000, "age": 25, "rating": 700, "years": 2, "accountType": "Gold"})
        self.call("api.promotions.none", "POST", "/api/v1/promotions", token=adm,
                  json_body={"balance": 10, "age": 50, "rating": 300, "years": 1, "accountType": "Blue"})
        self.call("api.promotions.user_role", "POST", "/api/v1/promotions", token=usr,
                  json_body={"balance": 10000, "age": 40, "rating": 800, "years": 10, "accountType": "Platinum"},
                  note="admin-only endpoint called with a USER token (legacy quirk: 400 'username' missing)")
        self.call("api.promotions.invalid", "POST", "/api/v1/promotions", token=adm,
                  json_body={"balance": 10, "age": 18, "rating": 300, "years": 0, "accountType": "Checking"})
        self.flow = "api.external"
        self.call("api.atm.search", "GET", "/api/v1/search/atm", token=usr, params={"zipcode": "11749"})
        self.call("api.atm.search.zero", "GET", "/api/v1/search/atm", token=usr, params={"zipcode": "12345"})
        self.call("api.atm.search.unavailable", "GET", "/api/v1/search/atm", token=usr, params={"zipcode": "54321"})
        self.call("api.atm.search.invalid_zip", "GET", "/api/v1/search/atm", token=usr, params={"zipcode": "abc"})
        self.call("api.visa.approved", "GET", "/api/v1/external/visa", token=usr,
                  params={"account": "ABCD1234ABCD123", "amount": "100"})
        self.call("api.visa.invalid_account", "GET", "/api/v1/external/visa", token=usr, params={"account": "x", "amount": "1"})
        self.flow = "api.credit"
        self.call("api.credit.references", "GET", "/api/v1/credit/references", token=adm, mask_money=True,
                  compare="credit-decision")
        self.flow = "credit.api"
        _, cb = self.call("credit.api.health", "GET", "/api/v1/health", base=self.credit)
        _, cauth = self.call("credit.api.auth", "POST", "/api/v1/auth", base=self.credit,
                             params={"username": ADMIN[0], "password": ADMIN[1]})
        self.tokens["credit_admin"] = (cauth or {}).get("authToken")
        self.call("credit.api.users", "GET", "/api/v1/users", base=self.credit, token=self.tokens["credit_admin"])

    def flow_docs(self):
        self.flow = "docs"
        self.call("docs.swagger_ui", "GET", "/swagger-ui.html", follow=True, compare="status")
        self.call("docs.api_docs.v2", "GET", "/v2/api-docs", compare="status",
                  note="springfox; migrated serves /v3/api-docs (springdoc) - T-05 decides alias")
        self.call("docs.api_docs.v3", "GET", "/v3/api-docs", compare="info", note="informational: absent on legacy")

    def db_snapshot(self):
        """Admin-API view of the database after all flows (the DB-state snapshot method)."""
        adm = self.tokens.get("admin")
        h = {"Authorization": "Bearer " + adm, "Accept": "application/json"} if adm else {}
        snap = {}

        def get(path, base=None, mask=False, hdr=h):
            r = requests.get((base or self.bank) + path, headers=hdr, verify=False, timeout=60)
            self.norm.mask_money = mask
            try:
                return {"status": r.status_code, "json": self.norm.json(r.json())}
            except ValueError:
                return {"status": r.status_code}
            finally:
                self.norm.mask_money = False
        snap["bank.users"] = get("/api/v1/users", mask=True)
        snap["bank.accounts.seed_masked"] = get("/api/v1/account", mask=True)
        for name, aid in (("parity-checking", self.chk), ("parity-savings", self.sav)):
            snap["bank.account.%s" % name] = get("/api/v1/account/%s" % aid)
            snap["bank.account.%s.transactions" % name] = get("/api/v1/account/%s/transaction" % aid)
        snap["bank.credit.references"] = get("/api/v1/credit/references", mask=True)
        ct = self.tokens.get("credit_admin")
        if ct:
            snap["credit.users"] = get("/api/v1/users", base=self.credit, mask=True,
                                       hdr={"Authorization": "Bearer " + ct, "Accept": "application/json"})
        snap["credit_decision"] = getattr(self, "credit_decision", None)
        return snap

    def _realias(self):
        """Ids learned late (e.g. account ids shown in the redirect that created them) are aliased everywhere."""
        def fix(o):
            if isinstance(o, dict):
                return {k: fix(v) for k, v in o.items()}
            if isinstance(o, list):
                return [fix(v) for v in o]
            if isinstance(o, str):
                return re.sub(r"(?<=selectSwitch=)(\d+)\b", lambda m: self.norm.aliases.get(m.group(1), m.group(1)), o)
            return o
        self.steps = fix(self.steps)

    def mock_requests(self):
        if not self.mock:
            return None
        try:
            reqs = requests.get(self.mock + "/__mock/requests", timeout=10).json()
            return [{k: v for k, v in r.items()} for r in reqs]
        except Exception as ex:  # pragma: no cover
            return {"error": str(ex)}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--bank", default="https://localhost:8443/bank")
    ap.add_argument("--credit", default="https://localhost:3500/credit")
    ap.add_argument("--mock", default="http://127.0.0.1:8099")
    ap.add_argument("--credit-timeout", type=int, default=90, help="seconds to wait for the JMS credit decision")
    ap.add_argument("--label", default="run")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    rp = Replayer(a.bank, a.credit, a.mock, a.credit_timeout)
    try:
        result = rp.run()
    except Exception as ex:
        result = {"meta": {"error": repr(ex)}, "steps": rp.steps}
        print("replay aborted: %r (after %d steps)" % (ex, len(rp.steps)), file=sys.stderr)
    result["meta"]["label"] = a.label
    result = redact(result, sorted({PARITY_USER["password"], NEW_PASSWORD, SEED_USER[1], ADMIN[1]}, key=len, reverse=True))
    with open(a.out, "w") as fh:
        json.dump(result, fh, indent=1, sort_keys=False)
    print("%s: %d steps -> %s" % (a.label, len(result["steps"]), a.out))
    sys.exit(1 if "error" in result["meta"] else 0)


if __name__ == "__main__":
    main()
