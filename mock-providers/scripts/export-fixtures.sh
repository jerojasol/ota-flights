#!/usr/bin/env bash
# Exports mock-providers responses as test fixtures for flight-aggregator's mapper tests.
#
# This is the ONLY sanctioned way for mock-providers output to reach flight-aggregator: as test fixture
# files, written by this script and committed to the aggregator. Never by a code, Maven or classpath
# dependency between the two projects (see ../AGENTS.md: "Projects never share source code").
#
# The script starts its own short-lived instances of the packaged jar on MOCK_EXPORT_PORT (default 18081),
# chaos disabled:
#   - two-itineraries.*  MOCK_SEED=17, MOCK_INVENTORY_SIZE=2, MOCK_OVERLAP_RATIO=1.0, MOCK_INVALID_RATIO=0
#                        Seed 17 is chosen so both itineraries are sold by all three providers, one is
#                        non-stop and one has three legs (so Beta shows out-of-order legs). The script
#                        fails if a provider does not return exactly two itineraries.
#   - empty.*            MOCK_INVENTORY_SIZE=0
# Query: MAD -> JFK on 2026-11-20. Existing fixtures are overwritten; a summary of changes is printed.
# Requires: java 21, curl, jq. Builds the jar with ./mvnw if target/ has none.
set -euo pipefail

cd "$(dirname "$0")/.."
PORT="${MOCK_EXPORT_PORT:-18081}"
BASE="http://localhost:$PORT"
FIXTURES="../flight-aggregator/infrastructure/src/test/resources/fixtures"
WORK="$(mktemp -d)"
PID=""

cleanup() {
  [[ -n "$PID" ]] && kill "$PID" 2>/dev/null && wait "$PID" 2>/dev/null
  rm -rf "$WORK"
}
trap cleanup EXIT

JAR="$(ls target/mock-providers-*.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "$JAR" ]]; then
  echo "Building jar..."
  ./mvnw -q -B -DskipTests package
  JAR="$(ls target/mock-providers-*.jar | head -n 1)"
fi

start() {
  echo "Starting mock-providers ($*) on :$PORT"
  env SERVER_PORT="$PORT" MOCK_CHAOS_ENABLED=false MOCK_INVALID_RATIO=0 "$@" \
    java -jar "$JAR" >"$WORK/server.log" 2>&1 &
  PID=$!
  for _ in $(seq 1 60); do
    curl -sf "$BASE/actuator/health" >/dev/null 2>&1 && return 0
    kill -0 "$PID" 2>/dev/null || break
    sleep 1
  done
  echo "mock-providers did not become healthy; log:" >&2
  cat "$WORK/server.log" >&2
  exit 1
}

stop() {
  kill "$PID" 2>/dev/null || true
  wait "$PID" 2>/dev/null || true
  PID=""
}

# write <relative-target> <url>
write() {
  local target="$FIXTURES/$1" tmp="$WORK/new"
  curl -sf "$BASE$2" -o "$tmp"
  mkdir -p "$(dirname "$target")"
  if [[ ! -f "$target" ]]; then
    printf '  new        %s\n' "$1"
  elif cmp -s "$tmp" "$target"; then
    printf '  unchanged  %s\n' "$1"
  else
    local added removed
    added=$(diff "$target" "$tmp" | grep -c '^>' || true)
    removed=$(diff "$target" "$tmp" | grep -c '^<' || true)
    printf '  changed    %s (+%s -%s lines)\n' "$1" "$added" "$removed"
  fi
  cp "$tmp" "$target"
}

ALPHA='/alpha/flights?from=MAD&to=JFK&date=2026-11-20'
BETA='/beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20'
GAMMA='/gamma/export?o=MAD&d=JFK&dt=20261120'

start MOCK_SEED=17 MOCK_INVENTORY_SIZE=2 MOCK_OVERLAP_RATIO=1.0
alpha_count=$(curl -sf "$BASE$ALPHA" | jq length)
beta_count=$(curl -sf "$BASE$BETA" | grep -o '<Itinerary ' | wc -l | tr -d ' ')
gamma_count=$(curl -sf "$BASE$GAMMA" | grep -c '^GAM-' || true)
if [[ "$alpha_count" != 2 || "$beta_count" != 2 || "$gamma_count" != 2 ]]; then
  echo "Expected 2 itineraries per provider, got alpha=$alpha_count beta=$beta_count gamma=$gamma_count." >&2
  echo "The generator changed; pick a new seed and update this script." >&2
  exit 1
fi
stop
# Restart so Beta's RequestId counter starts at 0000 for the exported document.
start MOCK_SEED=17 MOCK_INVENTORY_SIZE=2 MOCK_OVERLAP_RATIO=1.0
echo "Fixtures in $FIXTURES:"
write alpha/two-itineraries.json "$ALPHA"
write beta/two-itineraries.xml "$BETA"
write gamma/two-itineraries.csv "$GAMMA"
stop

start MOCK_INVENTORY_SIZE=0
write alpha/empty.json "$ALPHA"
write beta/empty.xml "$BETA"
write gamma/empty.csv "$GAMMA"
stop
