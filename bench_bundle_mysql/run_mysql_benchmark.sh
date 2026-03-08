#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PLAN="${PLAN:-5000x20,10000x50,12000x80}"
REPEATS="${REPEATS:-3}"

python3 "$SCRIPT_DIR/bench_runner.py" --plan "$PLAN" --repeats "$REPEATS" "$@"
