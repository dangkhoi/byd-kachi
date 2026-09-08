#!/bin/bash -p
set -euo pipefail
IFS=$'\n\t'
umask 077

if (( $# != 0 )); then
    printf '%s\n' 'This fixed T10 wrapper accepts no arguments.' >&2
    exit 64
fi

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd -P)"
readonly AUTHORIZATION_FILE="${REPO_ROOT}/.t10-local/session-n-read-only-authorization.json"

if [[ ! -x "${REPO_ROOT}/gradlew" || ! -f "${REPO_ROOT}/settings.gradle.kts" ]]; then
    printf '%s\n' 'Refusing to run outside the fixed ClusterNav repository layout.' >&2
    exit 78
fi

# This wrapper does not authorize Session N. A separate exact READ_ONLY authorization must exist
# at this fixed local, ignored location. The Gradle task is responsible for validating its bytes.
if [[ ! -f "${AUTHORIZATION_FILE}" ]]; then
    printf '%s\n' 'Session N is not authorized: exact local READ_ONLY authorization is required.' >&2
    exit 77
fi

cd -- "${REPO_ROOT}"
exec "${REPO_ROOT}/gradlew" --offline --no-daemon --console=plain :car-integration:runHudSignT10
