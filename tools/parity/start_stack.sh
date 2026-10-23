#!/usr/bin/env bash
# Start broker + mock externals + credit + bank for one side of the parity comparison.
#   tools/parity/start_stack.sh legacy   [--build]   # JDK 8, :8443/:3500, broker :61616, mock :8099
#   tools/parity/start_stack.sh migrated [--build]   # JDK 21, :9443/:4500, broker :61617, mock :8199
# Each start is a FRESH stack: H2 is in-memory, the broker instance is recreated, so seed data is pristine.
set -euo pipefail
. "$(dirname "$0")/env.sh" "${1:-legacy}"
BUILD=0; [[ "${2:-}" == "--build" ]] && BUILD=1
"$PARITY_DIR/stop_stack.sh" "$PARITY_MODE" >/dev/null 2>&1 || true
mkdir -p "$STATE_DIR" "$PARITY_HOME/artemis"
log() { echo "[parity:$PARITY_MODE] $*"; }

# ---------- source + build ----------
if [[ "$PARITY_MODE" == legacy ]]; then
  if [[ ! -d "$APP_SRC/.git" && ! -f "$APP_SRC/.git" ]]; then
    log "creating legacy worktree $APP_SRC at $LEGACY_REF"
    git -C "$REPO_ROOT" fetch -q origin master || true
    git -C "$REPO_ROOT" worktree add -f --detach "$APP_SRC" "$LEGACY_REF"
    BUILD=1
  fi
  # Legacy build blockers are worked around OUTSIDE the sources:
  #  1. dead bintray repo -> settings.xml mirroring '*' to the Google Central mirror
  #  2. com.broadcom.codesv:codesv-dist-jar:2.0.8 (test scope, unobtainable) -> empty stub jar in ~/.m2
  #  3. root pom lists missing module 'test' -> build bank/ and credit/ poms individually
  #  4. the 2 CodeSV JUnit tests cannot compile against the stub -> -Dmaven.test.skip=true for packaging
  SETTINGS="$PARITY_HOME/legacy-settings.xml"
  cat > "$SETTINGS" <<XML
<settings><mirrors><mirror><id>central-all</id><mirrorOf>*</mirrorOf>
<url>https://maven-central.storage-download.googleapis.com/maven2/</url></mirror></mirrors></settings>
XML
  STUB="$HOME/.m2/repository/com/broadcom/codesv/codesv-dist-jar/2.0.8/codesv-dist-jar-2.0.8.jar"
  if [[ ! -f "$STUB" ]]; then
    log "installing empty codesv stub into ~/.m2"
    tmp=$(mktemp -d); echo 'Manifest-Version: 1.0' > "$tmp/M"
    "$JAVA_HOME/bin/jar" cfm "$tmp/codesv-stub.jar" "$tmp/M"
    "$MVN" -q -s "$SETTINGS" install:install-file -Dfile="$tmp/codesv-stub.jar" \
      -DgroupId=com.broadcom.codesv -DartifactId=codesv-dist-jar -Dversion=2.0.8 -Dpackaging=jar
  fi
  BANK_ART_GLOB="$APP_SRC/bank/target/digitalbank-*.war"
  CREDIT_ART_GLOB="$APP_SRC/credit/target/digitalcredit-*.war"
  if [[ $BUILD == 1 ]] || ! ls $BANK_ART_GLOB >/dev/null 2>&1 || ! ls $CREDIT_ART_GLOB >/dev/null 2>&1; then
    for m in credit bank; do
      log "building $m (JDK 8)"
      "$MVN" -B -q -s "$SETTINGS" -f "$APP_SRC/$m/pom.xml" -Dmaven.test.skip=true package > "$STATE_DIR/build-$m.log" 2>&1 \
        || { tail -40 "$STATE_DIR/build-$m.log"; exit 1; }
    done
  fi
else
  BANK_ART_GLOB="$APP_SRC/bank/target/*.jar"
  CREDIT_ART_GLOB="$APP_SRC/credit/target/*.jar"
  if [[ $BUILD == 1 ]] || ! ls $BANK_ART_GLOB >/dev/null 2>&1 || ! ls $CREDIT_ART_GLOB >/dev/null 2>&1; then
    log "building migrated estate (JDK 21)"
    "$MVN" -B -q -f "$APP_SRC/pom.xml" -DskipTests package > "$STATE_DIR/build.log" 2>&1 \
      || { tail -40 "$STATE_DIR/build.log"; exit 1; }
  fi
fi
pick() { ls $1 2>/dev/null | grep -v -- '-plain\.\|-sources\.\|-javadoc\.\|##' | head -1; }
BANK_ART=$(pick "$BANK_ART_GLOB"); CREDIT_ART=$(pick "$CREDIT_ART_GLOB")
[[ -n "$BANK_ART" && -n "$CREDIT_ART" ]] || { echo "artifacts not found ($BANK_ART_GLOB / $CREDIT_ART_GLOB)" >&2; exit 1; }
log "bank artifact:   $BANK_ART"; log "credit artifact: $CREDIT_ART"

# ---------- broker ----------
if [[ ! -x "$ARTEMIS_HOME/bin/artemis" ]]; then
  log "downloading Apache Artemis $ARTEMIS_VERSION"
  curl -sfL "https://maven-central.storage-download.googleapis.com/maven2/org/apache/activemq/apache-artemis/$ARTEMIS_VERSION/apache-artemis-$ARTEMIS_VERSION-bin.tar.gz" \
    | tar xz -C "$PARITY_HOME/artemis"
fi
rm -rf "$STATE_DIR/broker"
JAVA_HOME="$ARTEMIS_JAVA_HOME" "$ARTEMIS_HOME/bin/artemis" create --silent --force --user "$ARTEMIS_USER" --password "$ARTEMIS_PASSWORD" \
  --require-login --no-web --no-amqp-acceptor --no-mqtt-acceptor --no-stomp-acceptor --no-hornetq-acceptor \
  --default-port "$BROKER_PORT" --http-port $((BROKER_PORT + 100)) "$STATE_DIR/broker" > "$STATE_DIR/broker-create.log" 2>&1
JAVA_HOME="$ARTEMIS_JAVA_HOME" nohup "$STATE_DIR/broker/bin/artemis" run > "$STATE_DIR/broker.log" 2>&1 &
echo $! > "$STATE_DIR/broker.pid"

# ---------- mock externals ----------
nohup python3 "$PARITY_DIR/mock_external.py" --port "$MOCK_PORT" --spec-dir "$APP_SRC/bank/src/test/resources/spec" \
  > "$STATE_DIR/mock.log" 2>&1 &
echo $! > "$STATE_DIR/mock.pid"

wait_for() { # name url seconds
  for _ in $(seq 1 "$3"); do curl -sk -o /dev/null "$2" && return 0; sleep 1; done
  echo "$1 did not come up at $2 (see $STATE_DIR)" >&2; return 1
}
for _ in $(seq 1 60); do grep -q 'AMQ221007' "$STATE_DIR/broker.log" 2>/dev/null && break; sleep 1; done
grep -q 'AMQ221007' "$STATE_DIR/broker.log" || { echo "broker failed, see $STATE_DIR/broker.log" >&2; exit 1; }
log "broker up on :$BROKER_PORT"
wait_for mock "http://127.0.0.1:$MOCK_PORT/__mock/health" 15

JMS_ARGS=(--spring.artemis.mode=native --spring.artemis.host=localhost --spring.artemis.port="$BROKER_PORT"
  --spring.artemis.broker-url="tcp://localhost:$BROKER_PORT"
  --spring.artemis.user="$ARTEMIS_USER" --spring.artemis.password="$ARTEMIS_PASSWORD"
  --io.digisic.partner.credit.app.request=CREDIT.APP.REQUEST --io.digisic.partner.credit.app.response=CREDIT.APP.RESPONSE)

# Legacy WARs are run EXPLODED (as Tomcat would): bank's RestClientCertificateConfig uses
# ResourceUtils.getFile("classpath:keystore/digisic.p12"), which fails inside a packed archive.
run_app() { # name artifact args...
  local name=$1 art=$2; shift 2
  if [[ "$art" == *.war ]]; then
    local dir="$STATE_DIR/$name-exploded"; rm -rf "$dir"; mkdir -p "$dir"
    (cd "$dir" && "$JAVA_HOME/bin/jar" xf "$art")
    nohup "$JAVA_HOME/bin/java" -cp "$dir" org.springframework.boot.loader.WarLauncher "$@" > "$STATE_DIR/$name.log" 2>&1 &
  else
    nohup "$JAVA_HOME/bin/java" -jar "$art" "$@" > "$STATE_DIR/$name.log" 2>&1 &
  fi
  echo $! > "$STATE_DIR/$name.pid"
}

# ---------- credit ----------
run_app credit "$CREDIT_ART" --server.port="$CREDIT_PORT" --server.address=127.0.0.1 \
  --io.digisic.credit.app.process.enabled=true --io.digisic.credit.app.process.time="$CREDIT_PROCESS_TIME" \
  "${JMS_ARGS[@]}"
wait_for credit "$CREDIT_URL/api/v1/health" 180
log "credit up at $CREDIT_URL"

# ---------- bank ----------
run_app bank "$BANK_ART" --server.port="$BANK_PORT" --server.address=127.0.0.1 \
  --io.digisic.credit.enabled=true --io.digisic.credit.protocol=https --io.digisic.credit.host=localhost \
  --io.digisic.credit.port="$CREDIT_PORT" --io.digisic.credit.context-path=/credit \
  --io.digisic.credit.username=admin@demo.io --io.digisic.credit.password='Demo123!' \
  --io.digisic.bank.atm.protocol=http --io.digisic.bank.atm.host=localhost --io.digisic.bank.atm.port="$MOCK_PORT" \
  --io.digisic.bank.visa.protocol=http --io.digisic.bank.visa.host=localhost --io.digisic.bank.visa.port="$MOCK_PORT" \
  --io.digisic.bank.obp.enabled=true --io.digisic.bank.obp.protocol=http --io.digisic.bank.obp.host=localhost \
  --io.digisic.bank.obp.port="$MOCK_PORT" --io.digisic.bank.obp.version=v4.0.0 --io.digisic.bank.obp.consumer_key=parity-consumer-key \
  --spring.h2.console.enabled=true --spring.h2.console.path=/h2-console --spring.h2.console.settings.web-allow-others=false \
  "${JMS_ARGS[@]}"
wait_for bank "$BANK_URL/api/v1/health" 240
log "bank up at $BANK_URL"
log "logs + pids in $STATE_DIR"
