#!/usr/bin/env bash
# Type-checks the spike's Kotlin against Android framework classes without the Android SDK.
# Used where Google's Maven repo is unreachable. It does NOT build an APK. Adapted from S1.
#
#   ANDROID_ALL_JAR=/path/to/android-all-16-robolectric-*.jar spikes/s2-llm/tools/typecheck.sh
#
# The jar is Robolectric's android-all for API 36 (Maven Central: org.robolectric:android-all).
# R is stubbed from res/values/strings.xml.
#
# EXCLUDED: LiteRtLmEngine.kt. It needs the litertlm-android AAR from Google Maven, which isn't
# reachable here, so it is NOT type-checked. A generated stub with the same constructor and the
# LlmEngine interface stands in for it, so the rest of the app is checked against that interface.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$HERE/app/src/main"
JAR="${ANDROID_ALL_JAR:?set ANDROID_ALL_JAR to the android-all jar}"
KLIB="$(ls -d /opt/gradle-*/lib | head -1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PKG="io.github.saadahmed1122.bibi.spike.s2"
EXCLUDE="LiteRtLmEngine.kt"

{
  echo "package $PKG"
  echo "object R {"
  echo "    object string {"
  i=1
  grep -o '<string name="[^"]*"' "$SRC/res/values/strings.xml" | sed 's/<string name="//; s/"$//' | while read -r n; do
    echo "        const val $n = $i"; i=$((i + 1))
  done
  echo "    }"
  echo "}"
} > "$WORK/R.kt"

cat > "$WORK/LiteRtLmEngineStub.kt" <<STUB
package $PKG
// Typecheck stub for the excluded $EXCLUDE (same constructor, same interface).
class LiteRtLmEngine(private val cacheDir: String, private val systemPrompt: String, private val cpuThreads: Int? = null) : LlmEngine {
    override val label: String = "stub"
    override fun load(path: String) = Unit
    override fun generate(prompt: String, maxTokens: Int, onToken: (String) -> Unit): EngineCounts = EngineCounts()
    override fun cancel() = Unit
    override fun close() = Unit
    companion object { const val LIBRARY_VERSION = "stub" }
}
STUB

CP="$KLIB/$(cd "$KLIB" && ls kotlin-compiler-embeddable-*.jar)"
for j in kotlin-stdlib kotlin-script-runtime kotlin-reflect kotlin-daemon-embeddable trove4j annotations kotlinx-coroutines-core-jvm; do
  CP="$CP:$(ls "$KLIB"/$j-*.jar | head -1)"
done
STDLIB="$(ls "$KLIB"/kotlin-stdlib-*.jar | head -1)"

SOURCES=$(find "$SRC/java" -name '*.kt' ! -name "$EXCLUDE")
echo "Type-checking: $(echo "$SOURCES" | xargs -n1 basename | tr '\n' ' ')"
echo "Excluded (not checked): $EXCLUDE -> replaced by a stub"

java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-reflect -jvm-target 17 \
  -classpath "$JAR:$STDLIB" -d "$WORK/out" \
  "$WORK/R.kt" "$WORK/LiteRtLmEngineStub.kt" $SOURCES 2>&1 \
  | grep -v -e 'JAVA_TOOL_OPTIONS' -e 'Kotlin home directory' || true
test -n "$(find "$WORK/out" -name 'InferenceService.class' 2>/dev/null)" && echo "TYPECHECK OK" || { echo "TYPECHECK FAILED"; exit 1; }
