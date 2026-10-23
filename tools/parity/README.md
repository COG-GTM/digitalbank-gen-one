# Digital Bank parity harness

Proves that the Java 21 / Spring Boot 3 build behaves exactly like the legacy Java 8 / Boot 2.1 build by replaying
every UI + REST flow (and the bank -> Artemis -> credit -> Artemis -> bank credit-application round trip) against
both stacks and diffing the normalised results.

| file | purpose |
|---|---|
| `env.sh` | ports / JDKs / paths per side (`legacy`, `migrated`), all overridable via env vars |
| `start_stack.sh <legacy\|migrated> [--build]` | fresh Artemis broker + `mock_external.py` + credit + bank (H2 in-memory => pristine seed data every start) |
| `stop_stack.sh [legacy\|migrated]` | stop one side, or both with no argument |
| `mock_external.py` | serves the recorded CodeSV fixtures (`bank/src/test/resources/spec/**/vs/`) for ATM search, VISA Direct and Open Banking |
| `replay.py` | runs the scripted flows against one stack and writes normalised JSON |
| `normalize.py` | capture-time normalisation (JWT, UUID, timestamps, session ids, base URLs, surrogate-id aliases) + compare-time `stabilize()` |
| `diff.py` | compares two replay files, writes a JSON report, exit 0 = parity |
| `run_parity.sh` | one-shot: start legacy, replay, start migrated, replay, diff |

## Side-by-side defaults

| | legacy | migrated |
|---|---|---|
| JDK | `/usr/lib/jvm/java-8-openjdk-amd64` | `/usr/lib/jvm/java-21-openjdk-amd64` |
| source | `$HOME/.parity/src/legacy` (git worktree of `origin/master`, created on demand; override `LEGACY_SRC`) | this checkout (override `MIGRATED_SRC`) |
| bank | `https://localhost:8443/bank` | `https://localhost:9443/bank` |
| credit | `https://localhost:3500/credit` | `https://localhost:4500/credit` |
| Artemis (core, user admin/admin) | 2.17.0 on :61616 | 2.31.2 on :61617 |
| mock externals | `http://127.0.0.1:8099` | `http://127.0.0.1:8199` |
| artifacts | `bank/target/digitalbank-*.war`, `credit/target/digitalcredit-*.war` (run **exploded** via `WarLauncher`) | `bank/target/*.jar`, `credit/target/*.jar` (`java -jar`, `-plain` jars ignored) |

Both sides receive the same configuration as command-line properties: `io.digisic.credit.*` (bank -> credit REST over
https), `spring.artemis.{mode,host,port,broker-url,user,password}`, `io.digisic.partner.credit.app.{request,response}`
= `CREDIT.APP.REQUEST` / `CREDIT.APP.RESPONSE`, `io.digisic.credit.app.process.{enabled,time}` (credit, time defaults to
5s via `CREDIT_PROCESS_TIME`), `io.digisic.bank.{atm,visa,obp}.{protocol,host,port}` -> mock (http),
`io.digisic.bank.obp.{enabled,version,consumer_key}`, and the H2 console enabled at `/bank/h2-console`.
The migrated build must keep accepting these property names (or the start script is updated in the same PR).

## Usage

```bash
cd <repo root>
# legacy (JDK 8) - first run creates the worktree, installs the codesv stub and builds
tools/parity/start_stack.sh legacy
python3 tools/parity/replay.py --label legacy --bank https://localhost:8443/bank \
    --credit https://localhost:3500/credit --mock http://127.0.0.1:8099 --out /tmp/parity/legacy.json

# migrated (JDK 21) - --build runs `mvn -DskipTests package` on this checkout
tools/parity/start_stack.sh migrated --build
python3 tools/parity/replay.py --label migrated --bank https://localhost:9443/bank \
    --credit https://localhost:4500/credit --mock http://127.0.0.1:8199 --out /tmp/parity/migrated.json

python3 tools/parity/diff.py /tmp/parity/legacy.json /tmp/parity/migrated.json --out /tmp/parity/diff.json
tools/parity/stop_stack.sh            # both sides

# or all of the above:
tools/parity/run_parity.sh            # exit code of diff.py; artefacts in /tmp/parity
# compare against the committed baseline instead of a live legacy stack:
python3 tools/parity/diff.py docs/migration/baseline/legacy-run.json /tmp/parity/migrated.json --out /tmp/parity/diff.json
```

`replay.py` must run against a **freshly started** stack (it registers `parity.tester@demo.io`, opens accounts, moves
money, changes the password and applies for credit). `--only <regex>` on `diff.py` narrows the comparison to a ticket's
steps (e.g. `--only '^api\.'`). Requires Python 3.8+ and `requests`.

## What is normalised (and why)

* capture time: JWTs, UUIDs (JMS correlation ids), timestamps/dates in the 2020s, epoch values, `JSESSIONID` /
  `remember-me`, base URL + host:port, charset in `Content-Type` (`application/json;charset=UTF-8` vs
  `application/json` is not treated as a difference; the media type is), surrogate keys (`id`, `*Id`) replaced by stable
  aliases such as `{ACCT:parity-checking}` - account ids come from the shared Hibernate sequence and the Faker seed data
  (random number of transactions) makes absolute ids differ run to run. Business numbers (`accountNumber`, transaction
  numbers) are **not** normalised and must match.
* on write: the demo passwords used by the replay are replaced by `{PASSWORD}` everywhere in the run JSON
  (requests and echoed form values) so no credential is stored in artifacts.
* compare time (`normalize.stabilize`, applied to both sides): seed users' Faker profiles, seeded transaction rows and
  money on `compare=seed` steps; role lists and validation-message lists (Java `Set` order); the random credit decision
  terms on `compare=credit-decision` steps (Digital Credit uses `Math.random()` for the credit score and random card
  numbers).

Determinism check: two fresh legacy runs diff clean (see `docs/migration/baseline/legacy-self-diff.json`). Anything
`diff.py` reports for legacy vs migrated is therefore a real behavioural difference.

## Known legacy behaviour that must be preserved (not bugs to fix)

* `ui.password.mismatch`: the server does not check `confirmPassword`; the password changes (the follow-up
  `ui.password.change` then fails with "Current Password does not match").
* A JWT without the required role gets **405** (not 403) from the API; no token -> 403; malformed token -> 400.
* `/api/v1/search/atm?zipcode=abc` -> 500 (constraint violation); `/api/v1/external/visa` ignores the account pattern.
* `/bank/promotions` and unknown pages -> Boot JSON 404 body; `/v2/api-docs` (springfox) is public.
