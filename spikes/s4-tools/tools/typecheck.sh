#!/usr/bin/env bash
# Type-checks the spike's Kotlin against Android framework classes without the Android SDK.
# Used where Google's Maven repo is unreachable. It does NOT build an APK.
#
#   ANDROID_ALL_JAR=/path/to/android-all-16-robolectric-*.jar spikes/s4-tools/tools/typecheck.sh
#
# The jar is Robolectric's android-all for API 36 (Maven Central: org.robolectric:android-all).
# R is stubbed from res/values/strings.xml and the drawable file names (if any).
# Since the real-LLM path (Decisions 2026-10-30) the app uses LiteRT-LM, MediaPipe tasks-decision
# and kotlinx-coroutines: put their classes.jar files on EXTRA_CP (colon-separated), or use Gradle.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$HERE/app/src/main"
JAR="${ANDROID_ALL_JAR:?set ANDROID_ALL_JAR to the android-all jar}"
KLIB="$(ls -d /opt/gradle-*/lib | head -1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PKG="io.github.saadahmed1122.bibi.spike.s4"
{
  echo "package $PKG"
  echo "object R {"
  echo "    object drawable {"
  i=1
  for f in "$SRC"/res/drawable*/*; do
    [ -e "$f" ] || continue
    n="$(basename "$f")"; n="${n%%.*}"
    echo "        const val $n = $i"; i=$((i + 1))
  done
  echo "    }"
  echo "    object string {"
  grep -o '<string name="[^"]*"' "$SRC/res/values/strings.xml" | sed 's/<string name="//; s/"$//' | while read -r n; do
    echo "        const val $n = $i"; i=$((i + 1))
  done
  echo "    }"
  echo "}"
} > "$WORK/R.kt"

CP="$KLIB/$(cd "$KLIB" && ls kotlin-compiler-embeddable-*.jar)"
for j in kotlin-stdlib kotlin-script-runtime kotlin-reflect kotlin-daemon-embeddable trove4j annotations kotlinx-coroutines-core-jvm; do
  CP="$CP:$(ls "$KLIB"/$j-*.jar | head -1)"
done
STDLIB="$(ls "$KLIB"/kotlin-stdlib-*.jar | head -1)"

java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-reflect -jvm-target 17 \
  -classpath "$JAR:$STDLIB${EXTRA_CP:+:$EXTRA_CP}" -d "$WORK/out" \
  "$WORK/R.kt" $(find "$SRC/java" -name '*.kt') 2>&1 \
  | grep -v -e 'JAVA_TOOL_OPTIONS' -e 'Kotlin home directory' || true
test -n "$(find "$WORK/out" -name 'MainActivity.class' 2>/dev/null)" && echo "TYPECHECK OK" || { echo "TYPECHECK FAILED"; exit 1; }
