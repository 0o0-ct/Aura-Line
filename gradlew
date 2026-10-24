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

# Use the JDK bundled with Android Studio
export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    echo "ERROR: Gradle not found at $GRADLE_HOME/bin/gradle" >&2
    exit 1
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
