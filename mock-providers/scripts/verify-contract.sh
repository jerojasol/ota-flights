#!/usr/bin/env bash
# Checks a running mock-providers instance against docs/provider-contracts.md.
#
# Starts nothing. Expects the service on MOCK_URL (default http://localhost:8081) with chaos disabled
# (MOCK_CHAOS_ENABLED=false), otherwise random 500s and latency will make checks fail.
# Requires: curl, jq, xmllint, awk. Exits non-zero if any check fails.
set -uo pipefail

MOCK_URL="${MOCK_URL:-http://localhost:8081}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

failures=0

check() {
  local name="$1"; shift
  if "$@" >/dev/null 2>&1; then
    printf 'PASS  %s\n' "$name"
  else
    printf 'FAIL  %s\n' "$name"
    failures=$((failures + 1))
  fi
}

# fetch <name> <path>: writes $WORK/<name>.body, $WORK/<name>.status, $WORK/<name>.type
fetch() {
  local name="$1" path="$2"
  : >"$WORK/$name.body"
  curl -s -o "$WORK/$name.body" -D "$WORK/$name.headers" -w '%{http_code}' "$MOCK_URL$path" >"$WORK/$name.status" \
    || echo 000 >"$WORK/$name.status"
  awk -F': *' 'tolower($1) == "content-type" { sub(/\r$/, "", $2); print tolower($2) }' \
    "$WORK/$name.headers" 2>/dev/null >"$WORK/$name.type"
}

status_is() { [[ "$(cat "$WORK/$1.status")" == "$2" ]]; }
type_starts() { [[ "$(cat "$WORK/$1.type")" == "$2"* ]]; }
body_matches() { grep -Eq "$2" "$WORK/$1.body"; }

# ---------------------------------------------------------------- Alpha (JSON)
echo "== Alpha"
fetch alpha '/alpha/flights?from=MAD&to=JFK&date=2026-11-20'
A="$WORK/alpha.body"
check "alpha: HTTP 200" status_is alpha 200
check "alpha: Content-Type application/json" type_starts alpha application/json
check "alpha: non-empty JSON array" jq -e 'type == "array" and length > 0' "$A"
check "alpha: itinerary fields are id, price, currency, segments" \
  jq -e 'all(.[]; (keys == ["currency","id","price","segments"]) and (.id | test("^ALP-[0-9a-f]{6}$")) and (.price | type == "number"))' "$A"
check "alpha: segment fields, digits-only flightNo, UTC timestamps with Z" \
  jq -e 'all(.[].segments[]; (keys == ["arr","carrier","dep","flightNo","from","to"])
         and (.flightNo | test("^[0-9]+$"))
         and (.dep | test("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"))
         and (.arr | test("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")))' "$A"
check "alpha: price written with two decimals" body_matches alpha '"price":[0-9]+\.[0-9]{2}[,}]'

fetch alpha400 '/alpha/flights?from=mad&to=JFK&date=2026-11-20'
check "alpha: lowercase from -> HTTP 400" status_is alpha400 400
check "alpha: 400 body error=BAD_REQUEST" jq -e '.error == "BAD_REQUEST" and (.message | length > 0)' "$WORK/alpha400.body"

# ----------------------------------------------------------------- Beta (XML)
echo "== Beta"
fetch beta '/beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20'
B="$WORK/beta.body"
x() { xmllint --xpath "$1" "$B" 2>/dev/null; }
check "beta: HTTP 200" status_is beta 200
check "beta: Content-Type application/xml" type_starts beta application/xml
check "beta: root SearchResponse in urn:beta-gds:search:v2" \
  test "$(x "concat(local-name(/*), '|', namespace-uri(/*))")" = "SearchResponse|urn:beta-gds:search:v2"
check "beta: Itineraries/@count > 0 and equals number of Itinerary elements" \
  test "$(x "string(/*/*[local-name()='Itineraries']/@count = count(//*[local-name()='Itinerary']) and count(//*[local-name()='Itinerary']) > 0)")" = true
check "beta: every Total/@amount has two decimals" \
  test "$(x "count(//*[local-name()='Total'][not(translate(@amount, '0123456789', '') = '.' and string-length(substring-after(@amount, '.')) = 2)])")" = 0
check "beta: LocalDateTime has no offset or Z" \
  test "$(x "count(//*[local-name()='LocalDateTime'][string-length(.) != 19 or contains(., 'Z') or contains(., '+')])")" = 0
check "beta: every LocalDateTime has a non-empty TimeZone sibling" \
  test "$(x "string(count(//*[local-name()='LocalDateTime']) > 0 and count(//*[local-name()='LocalDateTime'][not(following-sibling::*[local-name()='TimeZone'][string-length(.) > 0])]) = 0)")" = true
check "beta: Number has no leading zeros" \
  test "$(x "count(//*[local-name()='Number'][starts-with(., '0')])")" = 0

fetch beta400 '/beta/search?destination=JFK&departureDate=2026-11-20'
check "beta: missing origin -> HTTP 400" status_is beta400 400
check "beta: 400 Fault/Code=INVALID_REQUEST" \
  test "$(xmllint --xpath "string(/*[local-name()='Fault']/*[local-name()='Code'])" "$WORK/beta400.body" 2>/dev/null)" = INVALID_REQUEST

# ---------------------------------------------------------------- Gamma (CSV)
echo "== Gamma"
fetch gamma '/gamma/export?o=MAD&d=JFK&dt=20261120'
G="$WORK/gamma.body"
check "gamma: HTTP 200" status_is gamma 200
check "gamma: Content-Type text/csv; charset UTF-8" type_starts gamma 'text/csv;charset=utf-8'
check "gamma: first line is the header" test "$(head -n 1 "$G")" = 'ref,price_cents,currency,segments'
check "gamma: last line is #END" test "$(tail -n 1 "$G")" = '#END'
check "gamma: data lines are ref,cents,currency,segments with positional segment fields" \
  awk -F',' '
    NR == 1 || $0 == "#END" { next }
    NF != 4 || $1 !~ /^GAM-[0-9a-f]{4}$/ || $2 !~ /^[0-9]+$/ || $3 !~ /^[A-Z]{3}$/ { bad = 1 }
    {
      data++
      n = split($4, segs, "|")
      if (n < 1 || n > 3) bad = 1
      for (i = 1; i <= n; i++) {
        if (split(segs[i], f, ";") != 6) { bad = 1; continue }
        if (f[2] !~ /^[0-9][0-9][0-9][0-9]$/ || f[5] !~ /^[0-9]{12}$/ || f[6] !~ /^[0-9]{12}$/) bad = 1
      }
    }
    END { exit bad || data == 0 }' "$G"

fetch gamma400 '/gamma/export?o=MAD&d=JFK&dt=2026-11-20'
check "gamma: malformed dt -> HTTP 400" status_is gamma400 400
check "gamma: 400 is text/plain starting ERR 400" \
  bash -c "[[ \"\$(cat '$WORK/gamma400.type')\" == text/plain* ]] && head -c 7 '$WORK/gamma400.body' | grep -qx 'ERR 400'"

echo
if (( failures > 0 )); then
  echo "$failures check(s) FAILED against $MOCK_URL"
  exit 1
fi
echo "All checks passed against $MOCK_URL"
