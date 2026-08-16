#!/usr/bin/env bash
# Launch JavaFX Workbench without javafx-maven-plugin (works when Aliyun mirror lacks the plugin).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JFX_VERSION="${ANVIL_JFX_VERSION:-21.0.6}"

detect_jfx_os() {
  case "$(uname -s)-$(uname -m)" in
    Darwin-arm64) echo "mac-aarch64" ;;
    Darwin-x86_64) echo "mac" ;;
    Linux-aarch64|Linux-arm64) echo "linux-aarch64" ;;
    Linux-x86_64|Linux-amd64) echo "linux" ;;
    MINGW*|MSYS*|CYGWIN*) echo "win" ;;
    *) echo "mac-aarch64" ;;
  esac
}

build_module_path() {
  local os="$1"
  local repo="${HOME}/.m2/repository/org/openjfx"
  local mp=""
  for mod in base graphics controls; do
    local jar="$repo/javafx-$mod/$JFX_VERSION/javafx-$mod-$JFX_VERSION-$os.jar"
    if [[ ! -f "$jar" ]]; then
      echo "ERROR: JavaFX jar not found: $jar" >&2
      echo "Run: mvn -pl anvil-ui -am -DskipTests install" >&2
      exit 1
    fi
    mp="${mp:+$mp:}$jar"
  done
  echo "$mp"
}

echo "==> Building anvil-ui"
# clean compile avoids stale target/classes (e.g. RunTraceRow missing while WorkbenchView exists)
mvn -q -pl anvil-ui -am -DskipTests clean compile

REQUIRED=(
  anvil-ui/target/classes/com/anvil/ui/AnvilUiApp.class
  anvil-ui/target/classes/com/anvil/ui/RunTraceRow.class
  anvil-ui/target/classes/com/anvil/ui/RunDetailPanel.class
)
for f in "${REQUIRED[@]}"; do
  if [[ ! -f "$f" ]]; then
    echo "ERROR: build incomplete — missing $f" >&2
    exit 1
  fi
done

JFX_OS="$(detect_jfx_os)"
MODULE_PATH="$(build_module_path "$JFX_OS")"

CP="$(
  mvn -q -f anvil-ui/pom.xml \
    org.apache.maven.plugins:maven-dependency-plugin:3.7.1:build-classpath \
    -DincludeScope=runtime \
    -Dmdep.pathSeparator=: \
    -Dmdep.outputFile=/dev/stdout
)"

MAIN_CP="anvil-ui/target/classes:$CP"

echo "==> Launching JavaFX Workbench (server: http://127.0.0.1:7788)"
exec java \
  --enable-native-access=javafx.graphics \
  --module-path "$MODULE_PATH" \
  --add-modules javafx.controls \
  -cp "$MAIN_CP" \
  com.anvil.ui.AnvilUiApp
