#!/usr/bin/env bash
# Validates the ITGC-CM-07 change-management controls (Jira EM-137):
#   - .github/CODEOWNERS assigns dedicated owners to money-movement and security code
#   - .github/pull_request_template.md has change-risk and reviewer-attestation sections
#   - docs/governance/branch-protection.md lists the required branch-protection settings
#
# Runs GovernanceControlsTest on its own with the JUnit console launcher, so it
# needs only a JDK (8+) and curl, not a full Maven build.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JUNIT_VERSION="1.5.2"
JAR_NAME="junit-platform-console-standalone-${JUNIT_VERSION}.jar"
JAR="${JUNIT_CONSOLE_JAR:-${HOME}/.m2/repository/org/junit/platform/junit-platform-console-standalone/${JUNIT_VERSION}/${JAR_NAME}}"
TEST_CLASS="io.digisic.bank.test.junit.governance.GovernanceControlsTest"
TEST_SRC="${ROOT}/bank/src/test/java/io/digisic/bank/test/junit/governance/GovernanceControlsTest.java"

OUT="$(mktemp -d)"
trap 'rm -rf "${OUT}"' EXIT

if [ ! -f "${JAR}" ] && command -v mvn >/dev/null 2>&1 && [ -z "${JUNIT_CONSOLE_JAR:-}" ]; then
  (cd "${OUT}" && mvn -B -q dependency:get -Dtransitive=false \
    -Dartifact="org.junit.platform:junit-platform-console-standalone:${JUNIT_VERSION}") || true
fi
if [ ! -f "${JAR}" ]; then
  mkdir -p "$(dirname "${JAR}")"
  curl -fsSL --retry 3 -o "${JAR}.part" \
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/${JUNIT_VERSION}/${JAR_NAME}"
  mv "${JAR}.part" "${JAR}"
fi

javac -encoding UTF-8 -d "${OUT}" -cp "${JAR}" "${TEST_SRC}"
java -Drepo.root="${ROOT}" -jar "${JAR}" \
  --class-path "${OUT}" \
  --select-class "${TEST_CLASS}" \
  --details=tree --disable-banner
