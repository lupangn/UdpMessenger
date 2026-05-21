#!/usr/bin/env sh
##############################################################################
# Gradle wrapper script for UN*X
##############################################################################
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`cd "$(dirname "$0")" && pwd -P`"
MAX_FD="maximum"
warn() { echo "$*"; }
die() { echo; echo "$*"; echo; exit 1; }
# OS specific support
cygwin=false; darwin=false; nonstop=false
case "`uname`" in CYGWIN*) cygwin=true;; Darwin*) darwin=true;; NONSTOP*) nonstop=true;; esac
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
