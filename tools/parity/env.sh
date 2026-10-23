# Shared configuration for the parity stack scripts. Source with: . tools/parity/env.sh <legacy|migrated>
# Every value can be overridden from the environment before sourcing.
PARITY_MODE="${1:-${PARITY_MODE:-legacy}}"
PARITY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$PARITY_DIR/../.." && pwd)"
PARITY_HOME="${PARITY_HOME:-$HOME/.parity}"
MVN="${MVN:-/opt/apache-maven-3.9.9/bin/mvn}"
ARTEMIS_USER="${ARTEMIS_USER:-admin}"
ARTEMIS_PASSWORD="${ARTEMIS_PASSWORD:-admin}"
CREDIT_PROCESS_TIME="${CREDIT_PROCESS_TIME:-5}"

case "$PARITY_MODE" in
  legacy)
    JAVA_HOME="${LEGACY_JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
    APP_SRC="${LEGACY_SRC:-$PARITY_HOME/src/legacy}"     # git worktree of origin/master (created on demand)
    LEGACY_REF="${LEGACY_REF:-origin/master}"
    BANK_PORT="${LEGACY_BANK_PORT:-8443}"
    CREDIT_PORT="${LEGACY_CREDIT_PORT:-3500}"
    BROKER_PORT="${LEGACY_BROKER_PORT:-61616}"
    MOCK_PORT="${LEGACY_MOCK_PORT:-8099}"
    ARTEMIS_VERSION="${LEGACY_ARTEMIS_VERSION:-2.17.0}"
    ARTEMIS_JAVA_HOME="${LEGACY_ARTEMIS_JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
    ;;
  migrated)
    JAVA_HOME="${MIGRATED_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
    APP_SRC="${MIGRATED_SRC:-$REPO_ROOT}"                # the checkout you are working in
    BANK_PORT="${MIGRATED_BANK_PORT:-9443}"
    CREDIT_PORT="${MIGRATED_CREDIT_PORT:-4500}"
    BROKER_PORT="${MIGRATED_BROKER_PORT:-61617}"
    MOCK_PORT="${MIGRATED_MOCK_PORT:-8199}"
    ARTEMIS_VERSION="${MIGRATED_ARTEMIS_VERSION:-2.31.2}"
    ARTEMIS_JAVA_HOME="${MIGRATED_ARTEMIS_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
    ;;
  *) echo "mode must be legacy or migrated" >&2; return 2 2>/dev/null || exit 2 ;;
esac
STATE_DIR="$PARITY_HOME/$PARITY_MODE"
ARTEMIS_HOME="$PARITY_HOME/artemis/apache-artemis-$ARTEMIS_VERSION"
BANK_URL="https://localhost:$BANK_PORT/bank"
CREDIT_URL="https://localhost:$CREDIT_PORT/credit"
export JAVA_HOME PARITY_MODE STATE_DIR BANK_URL CREDIT_URL
