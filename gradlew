#!/usr/bin/env bash
#
# Gradle start up script for UN*X
#

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Resolve the directory where this script lives
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Use the bundled Gradle distribution
GRADLE_HOME="$SCRIPT_DIR/gradle/dist/gradle-8.10"

# Kotlin 2.0.20 cannot parse JDK 24+ version strings ("25.0.3"), so prefer a
# supported JDK: the one bundled with the project first, then the Studio JBR.
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version >/dev/null 2>&1; then
    for candidate in "$SCRIPT_DIR/jdk" "$HOME/.jdks/jbr-21.0.11" "/opt/android-studio/jbr"; do
        [ -x "$candidate/bin/java" ] || continue
        major=$("$candidate/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
        if [ -n "$major" ] && [ "$major" -ge 17 ] && [ "$major" -le 23 ]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi

if [ -z "${JAVA_HOME:-}" ]; then
    echo "ERROR: no compatible JDK (17-23) found. Set JAVA_HOME." >&2
    exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    echo "ERROR: Gradle not found at $GRADLE_HOME/bin/gradle" >&2
    exit 1
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
