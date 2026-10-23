#!/usr/bin/env python3
"""Drive the main Digital Bank UI flows in headless Chrome and screenshot each step.
usage: screens.py <label> <bank_base_url> <outdir>"""
import sys, os, re, time
from playwright.sync_api import sync_playwright

label, base, out = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3]
os.makedirs(out, exist_ok=True)
USER, PW = "jsmith@demo.io", "Demo123!"
log = []

def submit(page, field):
    page.locator(field).locator("xpath=ancestor::form").locator("button[type=submit]").first.click()
    page.wait_for_load_state("networkidle")

def shot(page, name):
    page.wait_for_load_state("networkidle")
    p = os.path.join(out, "%s.png" % name)
    page.screenshot(path=p, full_page=False)
    log.append((name, page.url.replace(base, ""), page.title()))

with sync_playwright() as pw:
    b = pw.chromium.launch(headless=True, executable_path=os.path.expanduser("~/.local/bin/google-chrome"))
    ctx = b.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 860})
    page = ctx.new_page()
    page.goto(base + "/login"); shot(page, "01-login")
    page.fill("#username", USER); page.fill("#password", PW)
    page.click("button[type=submit]"); page.wait_for_url(re.compile(r".*/home.*"))
    shot(page, "02-home")
    page.goto(base + "/account/checking-add")
    page.locator("input[name=accountType]").first.check()
    page.locator("input[name=ownershipType]").first.check()
    page.fill("#name", "Screens Checking"); page.fill("#openingBalance", "250.00")
    shot(page, "03-open-account-form")
    page.click("#newCheckingSubmit"); page.wait_for_load_state("networkidle")
    shot(page, "04-open-account-result")
    page.goto(base + "/account/deposit")
    sel = page.locator("#selectedAccount")
    val = [o for o in sel.locator("option").all() if "Screens Checking" in o.inner_text()][0].get_attribute("value")
    sel.select_option(val); page.fill("#amount", "75.25"); shot(page, "05-deposit-form")
    submit(page, "#amount"); shot(page, "06-deposit-result")
    page.goto(base + "/account/withdraw")
    page.locator("#selectedAccount").select_option(val); page.fill("#amount", "10.00"); shot(page, "07-withdraw-form")
    submit(page, "#amount"); shot(page, "08-withdraw-result")
    page.goto(base + "/account/xfer-between")
    fr = page.locator("#fromAccount")
    src = [o.get_attribute("value") for o in fr.locator("option").all() if o.get_attribute("value") and o.get_attribute("value") != val][0]
    fr.select_option(src); page.locator("#toAccount").select_option(val); page.fill("#amount", "5.00")
    shot(page, "09-transfer-form")
    submit(page, "#amount"); shot(page, "10-transfer-result")
    page.goto(base + "/account/checking-view?selectSwitch=" + val); shot(page, "11-history")
    page.goto(base + "/credit/credit-app")
    page.select_option("#employmentStatus", index=1)
    for f, v in (("annualIncome", "250000"), ("monthlyMortgage", "0"), ("monthlyAutoLoan", "0"),
                 ("monthlyOtherLoan", "0"), ("minimumCreditCard", "0"), ("monthlySpend", "500")):
        page.fill("#" + f, v)
    page.select_option("#bankStatus", index=1)
    page.locator("input[name=cashAdvance][value=No]").check()
    page.locator("input[name=balanceTransfer][value=No]").check()
    page.locator("#agreeTerms").check()
    shot(page, "12-credit-app-form")
    submit(page, "#annualIncome"); shot(page, "13-credit-app-submitted")
    deadline = time.time() + 60
    while time.time() < deadline:
        page.goto(base + "/credit/credit-app-status")
        if re.search(r">\s*(Approved|Declined)\s*<", page.content()):
            break
        time.sleep(3)
    shot(page, "14-credit-status")
    b.close()
for n, u, t in log:
    print("%s\t%s\t%s" % (n, u, t))
