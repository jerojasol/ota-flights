#!/usr/bin/env bash
# Smoke test against a running stack. Needs curl and jq.
# Usage: ./scripts/smoke.sh [base-url] [date]
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
DATE="${2:-2026-11-20}"
MARKUP="${MARKUP_PERCENTAGE:-5}"

failures=0
pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; failures=$((failures + 1)); }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# 1. Liveness
up=false
for _ in $(seq 1 60); do
  if [ "$(curl -fs "$BASE_URL/actuator/health/liveness" 2>/dev/null | jq -r '.status' 2>/dev/null)" = "UP" ]; then
    up=true
    break
  fi
  sleep 2
done
if $up; then pass "liveness UP"; else fail "liveness not UP after 120 s"; echo "1 or more checks failed"; exit 1; fi

# 2. First page (retried: with chaos on, every provider can fail in one run)
SEARCH="$BASE_URL/api/itineraries?origin=MAD&destination=JFK&date=$DATE&limit=5"
page1_ok=false
for attempt in 1 2 3; do
  code="$(curl -s -o "$tmp/p1.json" -w '%{http_code}' "$SEARCH")"
  statuses="$(jq -c '.providers // {} | map_values(.status)' "$tmp/p1.json" 2>/dev/null || echo '?')"
  echo "      attempt $attempt: HTTP $code, providers $statuses"
  if [ "$code" = "200" ] \
     && jq -e '(.items | length > 0) and (.nextCursor != null) and ([.providers[]?.status] | index("OK") != null)' "$tmp/p1.json" >/dev/null; then
    page1_ok=true
    break
  fi
  sleep 2
done
if $page1_ok; then pass "first page: 200, items, nextCursor, a provider OK"; else fail "first page after 3 attempts"; fi

# 3. Second page
if $page1_ok; then
  cursor="$(jq -r '.nextCursor' "$tmp/p1.json")"
  code="$(curl -s -o "$tmp/p2.json" -G -w '%{http_code}' "$BASE_URL/api/itineraries" \
    --data-urlencode "origin=MAD" --data-urlencode "destination=JFK" --data-urlencode "date=$DATE" \
    --data-urlencode "limit=5" --data-urlencode "cursor=$cursor")"
  dupes="$(jq -n --slurpfile a "$tmp/p1.json" --slurpfile b "$tmp/p2.json" \
    '[$a[0].items[].id] as $x | [$b[0].items[].id | select(. as $i | $x | index($i))] | length')"
  if [ "$code" = "200" ] && [ "$dupes" = "0" ]; then pass "second page: 200, no repeated ids"
  else fail "second page: HTTP $code, $dupes repeated ids"; fi
else
  fail "second page: skipped (no first page)"
fi

# 4. Markup
if $page1_ok; then
  base="$(jq -r '.items[0].basePrice.amount' "$tmp/p1.json")"
  final="$(jq -r '.items[0].finalPrice.amount' "$tmp/p1.json")"
  expected="$(awk -v b="$base" -v m="$MARKUP" 'BEGIN { printf "%.2f", int(b * (100 + m) + 0.5) / 100 }')"
  # The domain rounds HALF_EVEN and this rounds half up: exact ties differ by one cent.
  if awk -v e="$expected" -v f="$final" 'BEGIN { d = e - f; exit !(d <= 0.0101 && d >= -0.0101) }'; then
    pass "markup: $base x (1 + $MARKUP%) = $final"
  else
    fail "markup: $base x (1 + $MARKUP%) expected $expected, got $final"
  fi
else
  fail "markup: skipped (no first page)"
fi

# 5. Validation error
ctype="$(curl -s -o /dev/null -w '%{http_code} %{content_type}' "$BASE_URL/api/itineraries?origin=MAD")"
case "$ctype" in
  "400 application/problem+json"*) pass "missing params: 400 problem+json" ;;
  *) fail "missing params: got '$ctype'" ;;
esac

if [ "$failures" -gt 0 ]; then echo "$failures check(s) failed"; exit 1; fi
echo "all checks passed"
