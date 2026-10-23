# Legacy test baseline (master @ 59d94a6, JDK 8)

All runs were made from an unmodified `origin/master` worktree; workarounds live outside the sources
(see `../MIGRATION_PLAN.md` section 2). Numbers are what Maven/Failsafe reported.

| suite | how it was run | tests | pass | fail | not run | reason |
|---|---|---|---|---|---|---|
| bank JUnit `AtmLocationSearchTest`, `AtmLocationSearchTrnxRepoTest` | `mvn -f bank/pom.xml test` | 6 `@Test` | 0 | 0 | 6 | **cannot compile**: they import `com.ca.codesv.*` (CodeSV 2.0.8, only on the dead bintray repo); the stub jar satisfies dependency resolution only. Packaging therefore uses `-Dmaven.test.skip=true`. |
| credit JUnit `DigitalCreditApplicationTests` | `mvn -f credit/pom.xml test -DskipTests=false` | 1 | 0 | 0 | 1 | `credit/pom.xml` hard-codes `maven-surefire-plugin <skip>true</skip>`; it is never executed by the legacy build. |
| Serenity API `ApiAcceptanceTests` (`features/api/search/ATM Location Search.feature`) | copy of `bank/` outside the repo with the 2 CodeSV JUnit classes removed, `mvn verify` (Boot `start` -> Failsafe -> `stop`) | 4 | 2 | 2 | 0 | Invalid/empty zip pass. "Valid Zip Code with/without Results" fail with 503: the legacy suite expected the ATM upstream to be virtualised by CodeSV; with the app started by the POM no upstream is configured. Against the parity stack (mock externals) the same calls return 200 - see `api.atm.search*` in `transcripts.md`. |
| Serenity UI `UiAcceptanceTests` (login 14, logout 1, register 41, change password 26, savings 21, ATM 4 scenario lines) | same copy; run 1 default driver, run 2 `-Ddynamic.webdriver.driver=chrome -Dwebdriver.chrome.driver=<chromedriver 137>` | 107 | 0 | 107 | 0 | **environment-blocked, not application failures**: every scenario fails while Cucumber instantiates `SharedScenarioTests` because no browser session can be created. Run 1: Firefox/geckodriver not installed and Chrome driver path unset. Run 2: Serenity 2.0.69's `ProvidedDriver`/webdrivermanager 3.8.1 resolves ChromeDriver 114 (`SessionNotCreatedException: This version of ChromeDriver only supports Chrome version 114`) while the VM has Chrome for Testing 137. The UI flows these scenarios cover (login, logout, register, change password, savings, ATM) are all covered by the scripted replay (`ui.*` steps). T-08 re-enables the suite with Selenium 4 Selenium Manager + headless Chrome. |
| Taurus / Robot (`test-automation/`) | not run | - | - | - | - | not part of the Maven build; needs BlazeMeter/Robot tooling. |

## Behavioural baseline used for parity
Because the legacy automated suites are largely un-runnable, the authoritative functional baseline is the scripted
replay: **121 steps, captured twice on fresh stacks, 121/121 identical after normalisation, 0 DB-snapshot diffs**
(`legacy-run.json`, `legacy-self-diff.json`, `transcripts.md`, `db-snapshot.json`).

JMS round trip evidence (Artemis `queue stat` after a run): `CREDIT.APP.REQUEST` consumers 1, added 1, acked 1;
`CREDIT.APP.RESPONSE` consumers 1, added 2 (pending + decision), acked 2. Decision `Approved` shown on
`/bank/credit/credit-app-status` and in `GET /bank/api/v1/credit/references`.

## DB-state snapshot method
After the flows `replay.py` reads the database through the admin REST API (`admin@demo.io` JWT):
`GET /api/v1/users`, `GET /api/v1/account`, `GET /api/v1/account/{id}` + `/transaction` for the accounts created by
the run, `GET /api/v1/credit/references`, and Digital Credit `GET /credit/api/v1/users`; stored under `db_snapshot`
in the run JSON (`db-snapshot.json` here). For ad-hoc inspection the H2 console is enabled by the harness at
`https://localhost:8443/bank/h2-console` (JDBC URL `jdbc:h2:mem:testdb`, user `sa`, empty password). The legacy security config permits `/h2-console/**` without login, so the harness binds both
apps to `127.0.0.1` (`--server.address`) and sets `web-allow-others=false`; nothing is reachable off the VM.
