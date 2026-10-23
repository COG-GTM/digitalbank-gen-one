# Digital Bank (Gen One) modernisation plan: Java 8 / Spring Boot 2.1 -> Java 21 / Spring Boot 3.3

Integration branch: `devin/java21-boot3-digitalbank` (every ticket PR targets it; the umbrella PR targets `master`
and is never merged by the programme). **Functional behaviour must stay identical**: same URLs, context paths and
ports, same pages and form fields, same REST payloads and status codes, same seed data, same JMS flow. This is not a
mainframe migration.

Read first: [`baseline/`](baseline/) (legacy evidence) and [`../../tools/parity/README.md`](../../tools/parity/README.md)
(how to run legacy and migrated side by side and diff them).

---------------------------------------------------------------------------------------------------------------------

## 1. Inventory (legacy `master`)

### 1.1 Build
| item | legacy |
|---|---|
| root `pom.xml` | `digitalbank-gen-one:2.1.0`, parent `spring-boot-starter-parent:2.1.7.RELEASE`, modules `bank`, `credit`, **`test` (missing directory)** |
| `bank/pom.xml` | `digitalbank`, WAR (`finalName ${contextPathName}##${revision}`), Java 8; jjwt 0.9.1, springfox-swagger2/ui 2.9.2, h2, mysql-connector-java, mssql-jdbc, postgresql, javafaker 1.0.0, artemis-jms-client, spring-jms, httpclient 4, jaxb-api, jackson-module-kotlin; tests: JUnit 5.5.2 BOM + vintage, spring-security-test, Serenity 2.0.69 / serenity-cucumber4 / serenity-rest-assured, Selenium 3, webdrivermanager 3.8.1, **`com.broadcom.codesv:codesv-dist-jar:2.0.8` (test, dead bintray repo)**; build-info, spring-boot `start`/`stop` around failsafe (Serenity) |
| `credit/pom.xml` | `digitalcredit`, WAR, same Boot parent, jjwt, springfox, JPA + DB drivers, artemis |
| `*/mvnw*` | Maven wrapper (unused by the harness; `/opt/apache-maven-3.9.9/bin/mvn`) |

### 1.2 Bank (`bank/src/main`, package `io.digisic.bank`, 104 classes)
| package | classes |
|---|---|
| root | `DigitalBankApplication` (extends `SpringBootServletInitializer`, WAR) |
| `config` | `MultiHttpSecurityConfig` (2x `WebSecurityConfigurerAdapter`: `@Order(1)` `/api/**` stateless JWT, CSRF off; form chain), `RestClientCertificateConfig` (`RestTemplate` with mTLS from `classpath:keystore/digisic.p12` via `ResourceUtils.getFile` - fails inside a packed archive), `SwaggerConfig` (springfox `Docket`) |
| `config/data` | `DefaultData` (roles, admin + API users), `SampleData` (jsmith/nsmith + accounts) |
| `controller` (REST) | `AuthenticationController`, `UserController`, `AccountController`, `StaticDataController`, `PromotionController`, `SearchController`, `VisaController`, `CreditController`, `HealthCheckController`, `CommonController` |
| `controller/web` (Thymeleaf) | `WebHomeController`, `WebUserController`, `WebAccountController`, `WebCreditController`, `WebSearchController`, `WebVisaController`, `WebObpController`, `WebPromotions`, `WebCommonController` |
| `exception` | 6 `Rest*Exception` (`@ResponseStatus`) + `RestMethodArguementInvalidExceptionHandler` |
| `jms` | `CreditAppProducer`, `CreditAppConsumer` |
| `model` (+`enums`,`obp`,`security`) | 40 JPA entities / DTOs (`Account`, `AccountTransaction`, `Users`, `UserProfile`, `Role`, `Authority`, `CreditCard`, `CreditApplication`, `CreditReference`, OBP DTOs, ...) |
| `repository` | 16 Spring Data repositories |
| `security` | `JwtTokenProvider` (jjwt 0.9.1, HS256, fixed secret), `JwtTokenFilter` (`javax.servlet`), `JwtTokenFilterConfigurer` |
| `service` | `AccountService`, `UserService`, `UserSecurityService`, `SampleDataService` (Faker), `CreditCardService` (REST to credit), `SearchService` (ATM), `VisaService`, `ObpService` |
| `util` | `Constants` (all URIs, property names, external API paths), `Messages`, `Patterns` |
| resources | `application.properties`, `banner.txt`, `keystore/digisic.p12`, `server.xml` (Tomcat image only), `templates/` (29), `static/` (`css fonts images js scss favicon.ico`) |

**Runtime config** (`bank/src/main/resources/application.properties`): HTTPS `8443`, context `/bank`, PKCS12
`classpath:keystore/digisic.p12` alias `digitalbank`, session timeout 5m, `io.digisic.max.sessions=10000`, H2 in-memory,
`ddl-auto=update`; commented-out blocks for MySQL / MSSQL / Postgres, `io.digisic.credit.*` (bank->credit REST),
`spring.artemis.*`, `io.digisic.partner.credit.app.{request,response}`, `io.digisic.bank.{atm,visa,obp}.*`.

**UI routes** (`/bank` prefix; GET page + POST form unless noted): `/` -> 302 `/home`; `/login` (Spring Security
form, fields `username`, `password`, `remember-me`; failure -> `/login?error`; success -> `/home`); `/logout`
(GET, -> `/login?logout`, deletes `JSESSIONID`); `/signup` (title, firstName, lastName, gender, dob, ssn,
emailAddress, password) -> `/register` (address, locality, region, postalCode, country, homePhone, mobilePhone,
workPhone, agreeTerms); `/home`; `/user/profile`; `/user/password` (currentPassword, newPassword, confirmPassword);
`/account/checking-add`, `/account/savings-add` (accountType radio, ownershipType radio, name, openingBalance);
`/account/checking-view`, `/account/savings-view` (`?selectSwitch=<id>`); `/account/deposit`, `/account/withdraw`
(`id` select, amount); `/account/xfer-between` (fromAccount, toAccount, amount); `/account/update` (AJAX JSON rename);
`/search/atm` (POST `zipcode` from the menu form); `/external/xfer-visa` + `/external/xfer-visa-process` (extAccount,
extAmount); `/obp/obp-add` (bankId, username, password) -> `/obp/obp-add-acct` (bankId, accountId) -> `/obp/obp-view`,
`/obp/obp-delete`; `/credit/credit-app` (profile + employmentStatus, annualIncome, monthly*, bankStatus, cashAdvance,
balanceTransfer, agreeTerms) -> `/credit/credit-app-status`, `/credit/credit-view`, `/credit/credit-delete`;
`/promotions` (template exists, route returns 404); `/h2-console/**` (permitted); error pages `error/{400,401,403,404,405,500}.html`.

**REST API** (`/bank/api/v1`, JWT `Authorization: Bearer`): `POST /auth?username&password` -> `{"authToken"}`;
`GET /health`; users `GET /users`, `GET|POST /user`, `GET /user/find?username`, `GET|PUT /user/profile`,
`GET /user/role`, `GET|POST /user/account` (POST JSON `accountName, openingDeposit, accountTypeCode, ownerTypeCode`),
`/user/{id}/...` admin variants; accounts `GET /account`, `GET /account/checking`, `GET|PUT|DELETE /account/{id}`,
`GET /account/{id}/owner`, `GET|POST /account/{id}/transaction` (`amount, transactionTypeCode, description`),
`POST /account/{id}/transfer` (`toAccountId, amount`); static data `GET /data/account/{type,type/checking,type/savings,
standing,ownership/type,transaction/state,transaction/type,transaction/category}`; `POST /promotions`
(`balance, age, rating, years, accountType` Blue/Gold/Platinum); `GET /search/atm?zipcode`;
`GET /external/visa?account&amount`; `GET /credit/references`, `GET /credit/reference`. Docs: `/swagger-ui.html`,
`/v2/api-docs` (springfox, public). Error bodies are the Boot `DefaultErrorAttributes` JSON
(`timestamp,status,error,message,path`); bean-validation failures return a JSON array of messages (400).

### 1.3 Credit (`credit/src/main`, package `io.digisic.credit`, 63 classes, no UI)
`DigitalCreditApplication`; `config/{SecurityConfig,SwaggerConfig}`, `config/data/DefaultData`; controllers
`Authentication, User, CreditApplication, CreditCard, HealthCheck, Root, Common`; services `Account, CreditApplication`
(random credit score via `Math.random()`, processing delay `io.digisic.credit.app.process.time`, default 20s),
`CreditCard` (random card numbers), `UserSecurity, User`; `security/` (jjwt), `jms/{CreditConsumer,CreditProducer}`,
`exception/`, `util/`. HTTPS `3500`, context `/credit`, same keystore, H2. REST under `/credit/api/v1`: `auth`,
`health`, `users`, `user/**` (profile, role, password, enable, unlock, unexpire), `credit/application[/{id}]`,
`credit/card[/{id}]`.

### 1.4 JMS flow (Artemis, core protocol, `admin/admin`)
1. bank `WebCreditController` -> `CreditAppProducer` (`io.digisic.credit.enabled=true`) sends the application as JSON
   text to `CREDIT.APP.REQUEST` with a new UUID `JMSCorrelationID`, stores a `CreditReference` (Submitted).
2. credit `CreditConsumer` (`io.digisic.credit.app.process.enabled=true`) stores the application + correlation id,
   replies immediately on `CREDIT.APP.RESPONSE` (status Pending) via `CreditProducer`, then a worker thread decides
   after `process.time` seconds and sends the decision (Approved + credit line / Declined) on the same queue with the
   same correlation id.
3. bank `CreditAppConsumer` updates the `CreditReference` by correlation id; approved apps are viewed through
   `CreditCardService` -> credit REST (mTLS `RestTemplate`).
Baseline evidence: request queue 1 added / 1 acked, response queue 2 added / 2 acked, decision visible in the UI
(`ui.credit.status.final`) and `GET /api/v1/credit/references`.

### 1.5 External integrations (unreachable; recorded fixtures)
| service | bank property prefix | path | fixtures |
|---|---|---|---|
| ATM search | `io.digisic.bank.atm.*` | `GET /v3.1/nodes/atms?zip&radius` | `bank/src/test/resources/spec/synapse/vs/atmsearch/` |
| VISA Direct | `io.digisic.bank.visa.*` | `POST/GET /visadirect/fundstransfer/v1/pullfundstransactions` | `spec/visa/vs/visa direct/` |
| Open Banking | `io.digisic.bank.obp.*` | `/my/logins/direct`, `/obp/v4.0.0/banks/...` | `spec/openbanking/vs/` |
`tools/parity/mock_external.py` serves all 13 request/response pairs (plain http, as the services build `http(s)://host:port`
from the `protocol` property).

### 1.6 Packaging / deployment
`bank/Dockerfile`, `credit/Dockerfile` (`tomcat:9.0.48-jdk11`, WAR `##revision`, `server.xml`, external
`digital*.properties`); `broker/Dockerfile` + `docker-run.sh` (Artemis 2.17.0 on openjdk:11);
`deploy/docker-compose/docker-compose-{h2,mysql,mssql,postgres}.yml` (images `digisic/digital{broker,credit,bank}`,
env-var config, ports 8161/61616, credit 3500->8080/3535->8443, bank 8080/8443 mapping) +
`deploy/docker-compose/scripts/{mysql,mssql,postgres}/init.*`; `deploy/k8s/{digitalbank,ingress}/*` (deployments for
broker/credit/bank, traefik ingress, namespace, scripts).

### 1.7 Tests
| suite | location | count | legacy status |
|---|---|---|---|
| JUnit (bank) | `test/junit/search/AtmLocationSearch{,TrnxRepo}Test` | 6 `@Test` | **cannot compile**: CodeSV classes unavailable (dead bintray) |
| JUnit (credit) | `credit/src/test/.../DigitalCreditApplicationTests` | 1 | see [`baseline/test-results.md`](baseline/test-results.md) |
| Serenity API (`ApiAcceptanceTests`) | `features/api/search/ATM Location Search.feature` | 4 scenario lines | **blocked**: steps virtualise the ATM service with CodeSV |
| Serenity UI (`UiAcceptanceTests`) | `features/ui/**` (login 14, logout 1, register 41, change password 26, savings 21, ATM 4 scenario lines) | 107 lines | see [`baseline/test-results.md`](baseline/test-results.md) |
| Taurus / Robot | `test-automation/` | - | not built by Maven; out of scope (paths documented only) |

---------------------------------------------------------------------------------------------------------------------

## 2. Legacy build/run workaround (no change to legacy sources)
1. **Missing `test` module** - never build the root POM; build `bank/pom.xml` and `credit/pom.xml` individually
   (`mvn -f bank/pom.xml ...`); the parent still resolves via `relativePath`.
2. **Dead bintray repo** - a settings file mirroring `*` to `https://maven-central.storage-download.googleapis.com/maven2/`
   (`$HOME/.parity/legacy-settings.xml`, written by `start_stack.sh`).
3. **`codesv-dist-jar:2.0.8`** - an empty stub jar + pom installed to
   `~/.m2/repository/com/broadcom/codesv/codesv-dist-jar/2.0.8/` (resolution only).
4. The 2 CodeSV JUnit classes cannot compile against the stub -> package with `-Dmaven.test.skip=true`.
5. `RestClientCertificateConfig` cannot read the keystore from a packed WAR -> run the WARs **exploded** with
   `org.springframework.boot.loader.WarLauncher` (as Tomcat does in the Docker images).
6. Legacy sources come from a git worktree of `origin/master` (`$HOME/.parity/src/legacy` by default).
All of this is automated: `tools/parity/start_stack.sh legacy`.

## 3. Target stack decisions
| area | decision |
|---|---|
| JDK / build | Java 21 (`maven.compiler.release=21`), `spring-boot-starter-parent` **3.3.x** (latest 3.3 patch), Maven 3.9; drop `<module>test</module>` |
| packaging | **executable jars** (`spring-boot-maven-plugin` repackage), drop `SpringBootServletInitializer`, keep `finalName` stable (no `##`), keep HTTPS ports 8443/3500 and context paths `/bank` `/credit`, keystore loaded from classpath via `InputStream` (jar-safe) |
| namespaces | `javax.servlet/persistence/validation/annotation` -> `jakarta.*` (OpenRewrite `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta` or scripted) ; `javax.crypto`/`javax.net` stay |
| security | Spring Security 6 `SecurityFilterChain` beans (`@Order(1)` API chain with `securityMatcher("/api/**")`, form chain), `@EnableMethodSecurity`, `requestMatchers`, same permit lists, CSRF disabled, same redirects, same session max, remember-me; preserve 403 (no token) / 400 (bad JWT) / 405 (denied with token) observable behaviour |
| JWT | `io.jsonwebtoken:jjwt-api/impl/jackson` 0.12.x, HS256 with the same secret bytes (key padded only if the 0.12 key-length check demands it - token format may change, the harness masks JWTs) |
| persistence | Hibernate 6.5 / H2 2.x; keep `ddl-auto=update`; explicit `@GeneratedValue` strategy + `hibernate_sequence` compatible numbering, quote reserved words (`USER`, `VALUE`, ...) via `globally_quoted_identifiers` only if needed; `com.mysql:mysql-connector-j`; MSSQL/Postgres drivers from Boot BOM |
| web | Thymeleaf 3.1 (no `#request`/`#session`/`#servletContext` utility objects - pass via model), `thymeleaf-extras-springsecurity6`, trailing-slash matching disabled by default in Boot 3 -> configure `PathMatchConfigurer`/explicit mappings where legacy relied on it |
| API docs | `springdoc-openapi-starter-webmvc-ui` 2.x, `springdoc.swagger-ui.path=/swagger-ui.html` (still `/bank/swagger-ui.html`), `/v3/api-docs`; legacy `/v2/api-docs` availability only (status) is compared |
| HTTP clients | Apache HttpClient 5 (`httpclient5`) for the mTLS `RestTemplate`; ATM/VISA/OBP clients keep URL building from `io.digisic.bank.*` properties |
| JMS | `spring-boot-starter-artemis` (jakarta `artemis-jakarta-client`), same queue names / JSON payloads / correlation ids |
| tests | JUnit 5, Serenity 4.x + Cucumber 7 + Selenium 4 + REST Assured 5, headless Chrome; CodeSV -> WireMock driven by the `spec/**/vs` fixtures |
| deployment | `eclipse-temurin:21-jre` images running the jars; Artemis 2.3x broker image; one `docker-compose.yml` for broker + credit + bank (H2 default) + DB variants; k8s manifests updated |
| behaviour | **identical** - property names (`io.digisic.*`, `spring.artemis.*`) unchanged so the harness drives both sides with the same flags |

## 4. Tickets and merge waves
Owned paths are disjoint (a path belongs to exactly one ticket). **Wave 0 (T-01)** is the only ticket allowed to touch
files outside its owned list, and only for the mechanical `javax`->`jakarta` rewrite plus the minimal compile fixes
needed for `mvn -DskipTests package` on JDK 21 and both apps booting. T-01 merges into the integration branch first;
all wave 1 tickets branch from that merge and run in parallel. Each ticket's PR proves parity on its flows with
`tools/parity/diff.py --only '<regex>'` against `docs/migration/baseline/legacy-run.json` (or a live legacy stack).

| id | wave | title | owned paths | parity steps (`--only`) |
|---|---|---|---|---|
| T-01 | 0 | Build foundation | `pom.xml`, `bank/pom.xml`, `credit/pom.xml`, `bank/mvnw*`, `credit/mvnw*`, `bank/src/main/resources/application.properties`, `credit/src/main/resources/application.properties`, `bank/src/main/java/io/digisic/bank/DigitalBankApplication.java`, `credit/src/main/java/io/digisic/credit/DigitalCreditApplication.java` | stacks boot; `api.health`, `credit.api.health` |
| T-02 | 1 | Bank security (Spring Security 6) | `bank/src/main/java/io/digisic/bank/security/**`, `bank/src/main/java/io/digisic/bank/config/MultiHttpSecurityConfig.java` | `^ui\.(auth\|login\|logout\|home)`, `^api\.(auth\|no_token\|bad_token\|parity_user\|users\.forbidden)` |
| T-03 | 1 | Bank persistence + seed data | `bank/src/main/java/io/digisic/bank/model/**`, `.../repository/**`, `.../config/data/**`, `.../service/{AccountService,UserService,UserSecurityService,SampleDataService}.java`, `deploy/docker-compose/scripts/**` | `^ui\.(accounts\|seed\|register)`, `^api\.(accounts\|users)`, db snapshot |
| T-04 | 1 | Bank web / Thymeleaf | `bank/src/main/java/io/digisic/bank/controller/web/**`, `bank/src/main/resources/templates/**`, `bank/src/main/resources/static/**` | all `^ui\.` steps (HTML forms, fields, alerts, text) |
| T-05 | 1 | Bank REST + OpenAPI + outbound HTTP | `bank/src/main/java/io/digisic/bank/controller/*.java`, `.../exception/**`, `.../config/SwaggerConfig.java`, `.../config/RestClientCertificateConfig.java`, `.../service/{SearchService,VisaService,ObpService}.java`, `.../util/**`, `bank/src/main/resources/{banner.txt,keystore/**}` | `^api\.`, `^docs\.`, `^ui\.(atm\|visa\|obp)` |
| T-06 | 1 | JMS + bank<->credit integration | `bank/src/main/java/io/digisic/bank/jms/**`, `credit/src/main/java/io/digisic/credit/jms/**`, `bank/src/main/java/io/digisic/bank/service/CreditCardService.java` | `^ui\.credit`, `^api\.credit`, broker queue stats |
| T-07 | 1 | Digital Credit module | `credit/src/main/**` except `credit/src/main/java/io/digisic/credit/jms/**`, `credit/src/main/resources/application.properties`, `DigitalCreditApplication.java` | `^credit\.`, `^ui\.credit` (decision side) |
| T-08 | 1 | Tests (JUnit 5, Serenity 4, WireMock) | `bank/src/test/**`, `credit/src/test/**`, `bank/serenity.properties`; plus test-scoped dependency/plugin hunks in `bank/pom.xml`/`credit/pom.xml` only | test counts vs `baseline/test-results.md` |
| T-09 | 1 | Packaging + deployment | `bank/Dockerfile`, `credit/Dockerfile`, `bank/src/main/resources/server.xml`, `credit/src/main/resources/server.xml`, `broker/**`, `deploy/**` except `deploy/docker-compose/scripts/**`, `README.md`, `bank/README.md`, `credit/README.md`, new top-level `docker-compose.yml` / run scripts | compose up -> `tools/parity/replay.py` against compose ports |

Not owned by any ticket (programme-owned, edited only by the integration stage): `docs/migration/**`, `tools/parity/**`,
`test-automation/**` (unchanged).

**Integration order:** T-01 -> (T-02 ... T-09 in any order; rebase on the integration branch before merge). After all
wave 1 merges the integration stage runs `tools/parity/run_parity.sh` (full diff must be 121/121 PASS, 0 snapshot
diffs) and updates the umbrella PR.

## 5. Parity method
* Both stacks start fresh (H2 in-memory => seed data recreated) with the same `io.digisic.*` properties and their own
  broker + mock (`start_stack.sh legacy|migrated`).
* `replay.py` executes 121 steps: registration, good/bad login, logout, home, open checking/savings (incl. below
  minimum), view, deposit (incl. zero), withdraw (incl. insufficient funds), transfer (incl. insufficient funds),
  history, rename, profile, change password (incl. wrong/mismatch), ATM search (results / zero / unavailable) UI + API,
  VISA (approved / not approved / insufficient / invalid merchant) UI + API, OBP link + view, credit application ->
  JMS -> decision, REST auth/users/accounts/transactions/transfer/static data/promotions/health/credit, Swagger, 404s,
  H2 console, Digital Credit REST, and a DB snapshot through the admin REST API.
* Captured per step: status, redirect chain, media type, normalised JSON body or HTML summary (title, every form with
  its fields/types/options, alerts, visible text).
* `diff.py` compares field by field; two fresh legacy runs diff clean (121/121, `baseline/legacy-self-diff.json`), so
  every reported difference is behavioural. Accepted non-functional differences must be listed in the ticket PR and
  approved by the integration stage; none are pre-approved except: JWT token bytes, `/v3/api-docs` existing on migrated.
