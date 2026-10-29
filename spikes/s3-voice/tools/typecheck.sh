#!/usr/bin/env bash
# Type-checks the spike's Kotlin against Android framework classes without the Android SDK.
# Used where Google's Maven repo is unreachable. It does NOT build an APK or run anything.
#
#   ANDROID_ALL_JAR=/path/to/android-all.jar spikes/s3-voice/tools/typecheck.sh
#
# The jar is Robolectric's android-all for API 36 (Maven Central: org.robolectric:android-all).
# R is stubbed from res/values/strings.xml.
#
# The sherpa-onnx adapter (SherpaEngines.kt) needs the AAR, which isn't available here, so by
# default it is EXCLUDED and replaced by a generated stub with the same public surface.
# Optional extra check: set SHERPA_API_DIR to a folder with the sherpa-onnx v2.0.1 Kotlin API
# sources (sherpa-onnx/kotlin-api/*.kt at tag v2.0.1; the AAR is compiled from these files) to
# compile the real adapter against them instead of the stub. Native code is never loaded.
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$HERE/app/src/main"
JAR="${ANDROID_ALL_JAR:?set ANDROID_ALL_JAR to the android-all jar}"
KLIB="$(ls -d /opt/gradle-*/lib | head -1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PKG="io.github.saadahmed1122.bibi.spike.s3"
PKGDIR="$SRC/java/${PKG//.//}"
ADAPTER="$PKGDIR/SherpaEngines.kt"
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

SOURCES=()
while IFS= read -r f; do
  [ "$f" = "$ADAPTER" ] && continue
  SOURCES+=("$f")
done < <(find "$SRC/java" -name '*.kt' | sort)

if [ -n "${SHERPA_API_DIR:-}" ]; then
  echo "Mode: real adapter against sherpa-onnx Kotlin API sources in $SHERPA_API_DIR"
  SOURCES+=("$ADAPTER")
  while IFS= read -r f; do SOURCES+=("$f"); done < <(find "$SHERPA_API_DIR" -maxdepth 1 -name '*.kt' | sort)
else
  echo "Mode: adapter EXCLUDED (SherpaEngines.kt replaced by a stub)"
  cat > "$WORK/SherpaEnginesStub.kt" <<EOF
package $PKG
object SherpaEngines : EngineFactory {
    const val VERSION = "stub"
    override val runtimeName = "stub"
    override fun tts(spec: ModelSpec, files: Map<String, String>, threads: Int): TtsEngine = error("stub")
    override fun stt(spec: ModelSpec, files: Map<String, String>, threads: Int): SttEngine = error("stub")
}
EOF
  SOURCES+=("$WORK/SherpaEnginesStub.kt")
fi

CP="$KLIB/$(cd "$KLIB" && ls kotlin-compiler-embeddable-*.jar)"
for j in kotlin-stdlib kotlin-script-runtime kotlin-reflect kotlin-daemon-embeddable trove4j annotations kotlinx-coroutines-core-jvm; do
  CP="$CP:$(ls "$KLIB"/$j-*.jar | head -1)"
done
STDLIB="$(ls "$KLIB"/kotlin-stdlib-*.jar | head -1)"

echo "Files: ${#SOURCES[@]} (+ R stub)"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-reflect -jvm-target 17 \
  -classpath "$JAR:$STDLIB" -d "$WORK/out" \
  "$WORK/R.kt" "${SOURCES[@]}" 2>&1 \
  | grep -v -e 'JAVA_TOOL_OPTIONS' -e 'Kotlin home directory' || true
for c in MainActivity InferenceService Recorder SherpaEngines; do
  test -n "$(find "$WORK/out" -name "$c.class" 2>/dev/null)" || { echo "TYPECHECK FAILED ($c.class missing)"; exit 1; }
done
echo "TYPECHECK OK"
