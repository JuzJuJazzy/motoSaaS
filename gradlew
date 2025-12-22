#!/usr/bin/env sh
set -e
THIS_DIR="$(dirname "$0")"
if [ -z "$GRADLE_HOME" ]; then
  # Try to run gradle wrapper jar via Java - if jar exists
  if [ -f "$THIS_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
    exec java -jar "$THIS_DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
  fi
fi
echo "Gradle wrapper JAR not found. Please open the project in Android Studio (it can download Gradle), or install Gradle on your system (e.g., 'brew install gradle')."
exit 1
