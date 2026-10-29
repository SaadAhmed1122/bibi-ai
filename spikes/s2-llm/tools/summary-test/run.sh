#!/usr/bin/env bash
# Compiles Summary.kt, Prompts.kt and Metrics.kt with SummaryTest.kt on a plain JVM (no Android)
# and runs the tests against synthetic run logs and the shipped assets/prompts.tsv.
#   spikes/s2-llm/tools/summary-test/run.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APP="$HERE/../../app/src/main"
PKGDIR="$APP/java/io/github/saadahmed1122/bibi/spike/s2"
KLIB="$(ls -d /opt/gradle-*/lib | head -1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

CP="$KLIB/$(cd "$KLIB" && ls kotlin-compiler-embeddable-*.jar)"
for j in kotlin-stdlib kotlin-script-runtime kotlin-reflect kotlin-daemon-embeddable trove4j annotations kotlinx-coroutines-core-jvm; do
  CP="$CP:$(ls "$KLIB"/$j-*.jar | head -1)"
done
STDLIB="$(ls "$KLIB"/kotlin-stdlib-*.jar | head -1)"

java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-reflect -jvm-target 17 \
  -classpath "$STDLIB" -d "$WORK/out" \
  "$PKGDIR/Summary.kt" "$PKGDIR/Prompts.kt" "$PKGDIR/Metrics.kt" "$HERE/SummaryTest.kt" 2>&1 \
  | grep -v -e 'JAVA_TOOL_OPTIONS' -e 'Kotlin home directory' || true
test -f "$WORK/out/io/github/saadahmed1122/bibi/spike/s2/SummaryTestKt.class" || { echo "COMPILE FAILED"; exit 1; }

java -cp "$WORK/out:$STDLIB" io.github.saadahmed1122.bibi.spike.s2.SummaryTestKt "$APP/assets/prompts.tsv"
