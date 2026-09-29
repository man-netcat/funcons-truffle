#!/usr/bin/env bash
# Regenerate the funcon node classes and prove they are byte-identical to a snapshot.
#
# Usage: verify.sh [-c] [snapshot-dir]
#   -c, --create   write the current output as the snapshot instead of comparing
#
# `trufflegen` writes fctlang/src/main/kotlin/generated, which is gitignored, so
# there is nothing in git to diff against. This runs the generator and compares
# every file byte for byte, which is a stronger check than the test suite: a
# refactor that changes the generator's output at all fails here, whether or not
# the change is a bug.
#
# The snapshot is deliberately not committed. It is 43 files of generated Kotlin
# that nothing reads except this script, and a versioned copy would go stale
# without anyone noticing. Keep it outside the tree, and regenerate it with
# --create when a change to the output is intended.
set -euo pipefail

cd "$(dirname "$0")/.."
snapshot="${1:-/tmp/trufflegen-snapshot}"
out=fctlang/src/main/kotlin/generated

./gradlew :trufflegen:run --console=plain -q >/dev/null

if [[ "${1:-}" == "-c" || "${1:-}" == "--create" ]]; then
    snapshot="$2"
    rm -rf "$snapshot"
    mkdir -p "$snapshot"
    cp "$out"/* "$snapshot"/
    echo "SNAPSHOT WRITTEN to $snapshot ($(ls "$snapshot" | wc -l) files)"
    exit 0
fi

if diff -r "$snapshot" "$out" >/dev/null 2>&1; then
    echo "IDENTICAL to $snapshot ($(ls "$out" | wc -l) files)"
else
    echo "OUTPUT CHANGED:"
    diff -r "$snapshot" "$out" | head -60
    exit 1
fi
