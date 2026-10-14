#!/usr/bin/env sh
# Small bootstrap wrapper for environments where the standard wrapper JAR is unavailable.
# GitHub Actions generates the canonical Gradle wrapper before running build tasks.
set -eu
GRADLE_VERSION="8.9"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_DIR="$GRADLE_USER_HOME/file-navigator-gradle/gradle-$GRADLE_VERSION"
GRADLE_BIN="$INSTALL_DIR/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$(dirname "$INSTALL_DIR")"
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM
  DIST_ZIP="$TMP_DIR/gradle.zip"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 \
      "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
      --output "$DIST_ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$DIST_ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else
    echo "Error: curl or wget is required to download Gradle $GRADLE_VERSION." >&2
    exit 1
  fi
  if ! command -v unzip >/dev/null 2>&1; then
    echo "Error: unzip is required to extract Gradle." >&2
    exit 1
  fi
  unzip -q "$DIST_ZIP" -d "$TMP_DIR"
  rm -rf "$INSTALL_DIR"
  mv "$TMP_DIR/gradle-$GRADLE_VERSION" "$INSTALL_DIR"
fi
exec "$GRADLE_BIN" "$@"
