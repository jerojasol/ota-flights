#!/usr/bin/env bash
# Keyset vs. offset pagination timings against the 1M-row seed (scripts/seed-1m.sql).
#
#   DB_URL=postgres://flights:flights@localhost:5432/flights scripts/measure-pagination.sh
#
# Without DB_URL (or without a local psql) it runs psql inside the compose container ($PG_CONTAINER).
# Each query runs RUNS times; the reported time is the median "Execution Time" of EXPLAIN (ANALYZE, BUFFERS)
# and the buffers are the shared buffers (hit + read) of the top plan node in the last run.
# Every plan is written to scripts/results/.
set -euo pipefail

RUNS="${RUNS:-5}"
PG_CONTAINER="${PG_CONTAINER:-ota-flights-postgres-1}"
RESULTS_DIR="$(cd "$(dirname "$0")" && pwd)/results"
mkdir -p "$RESULTS_DIR"
rm -f "$RESULTS_DIR"/explain-*.txt

if [[ -n "${DB_URL:-}" ]] && command -v psql >/dev/null; then
  run_psql() { psql "$DB_URL" -X -q -A -t -v ON_ERROR_STOP=1 "$@"; }
else
  run_psql() { docker exec -i "$PG_CONTAINER" psql -U "${POSTGRES_USER:-flights}" -d "${POSTGRES_DB:-flights}" -X -q -A -t -v ON_ERROR_STOP=1 "$@"; }
fi

ORDER="ORDER BY final_price, itinerary_key"
filter() { echo "origin = '$1' AND destination = '$2' AND departure_date = DATE '$3'"; }
median() { sort -n | awk '{a[NR]=$1} END {print (NR%2 ? a[(NR+1)/2] : (a[NR/2]+a[NR/2+1])/2)}'; }
slugify() { echo "$1" | tr 'A-Z' 'a-z' | tr -c 'a-z0-9\n' '-' | tr -s '-' | sed 's/^-//;s/-$//'; }

# measure <name> <sql>: prints a table row and stores the last plan.
measure() {
  local name="$1" sql="$2" settings="${3:-}" plan t times=() file
  for _ in $(seq "$RUNS"); do
    plan=$(run_psql -c "$settings EXPLAIN (ANALYZE, BUFFERS) $sql")
    t=$(sed -n 's/^Execution Time: \([0-9.]*\) ms/\1/p' <<<"$plan")
    times+=("$t")
  done
  file="$RESULTS_DIR/explain-$(slugify "$name").txt"
  { echo "-- $name"; [[ -n "$settings" ]] && echo "$settings"; echo "$sql;"; echo; echo "$plan"; } > "$file"
  local node buffers
  node=$(grep -oE '(Index Only Scan|Index Scan|Bitmap Heap Scan|Parallel Seq Scan|Seq Scan)( using [a-z_]+)?' <<<"$plan" | head -1)
  buffers=$(grep -m1 'Buffers: shared' <<<"$plan" | awk '{h=0;r=0; for(i=1;i<=NF;i++){if($i~/^hit=/){sub("hit=","",$i);h=$i} if($i~/^read=/){sub("read=","",$i);r=$i}} print h+r}')
  echo "| $name | $(printf '%s\n' "${times[@]}" | median) | $buffers | $node |"
}

header() {
  echo "| Query | Median execution time (ms, $RUNS runs) | Shared buffers | Scan node |"
  echo "|---|---|---|---|"
}

# keyset_after <filter> <row>: "price, 'key'" of the given 1-based row in the filtered ordering.
keyset_after() {
  local c
  c=$(run_psql -F '|' -c "SELECT final_price, itinerary_key FROM itineraries WHERE $1 $ORDER OFFSET $(( $2 - 1 )) LIMIT 1")
  echo "${c%%|*}, '${c#*|}'"
}

TOTAL=$(run_psql -c "SELECT count(*) FROM itineraries")
echo "Rows: $TOTAL"
echo

# 1. A real route: a few hundred rows per origin, destination and date.
F=$(filter MAD BCN 2026-12-01)
N=$(run_psql -c "SELECT count(*) FROM itineraries WHERE $F")
D=$(( N - 20 ))
echo "### Real route MAD-BCN 2026-12-01 ($N rows)"
header
measure "Real route, keyset, page 1"            "SELECT * FROM itineraries WHERE $F $ORDER LIMIT 20"
measure "Real route, keyset, row $D"            "SELECT * FROM itineraries WHERE $F AND (final_price, itinerary_key) > ($(keyset_after "$F" "$D")) $ORDER LIMIT 20"
measure "Real route, offset, page 1"            "SELECT * FROM itineraries WHERE $F $ORDER LIMIT 20 OFFSET 0"
measure "Real route, offset, row $D"            "SELECT * FROM itineraries WHERE $F $ORDER LIMIT 20 OFFSET $D"
echo

# 2. Synthetic deep route: same query shape, same index, at depth.
F=$(filter ZZA ZZB 2026-12-01)
N=$(run_psql -c "SELECT count(*) FROM itineraries WHERE $F")
D=$(( N * 95 / 100 ))
echo "### Synthetic deep route ZZA-ZZB 2026-12-01 ($N rows)"
header
measure "Deep route, keyset, page 1"            "SELECT * FROM itineraries WHERE $F $ORDER LIMIT 20"
measure "Deep route, keyset, row $D"            "SELECT * FROM itineraries WHERE $F AND (final_price, itinerary_key) > ($(keyset_after "$F" "$D")) $ORDER LIMIT 20"
measure "Deep route, offset, page 1"            "SELECT * FROM itineraries WHERE $F $ORDER LIMIT 20 OFFSET 0"
measure "Deep route, offset, row $D"            "SELECT * FROM itineraries WHERE $F $ORDER LIMIT 20 OFFSET $D"
# The planner prefers a bitmap scan + sort for a large OFFSET; force the ordered index walk too.
measure "Deep route, offset, row $D, ordered index scan forced" "SELECT * FROM itineraries WHERE $F $ORDER LIMIT 20 OFFSET $D" \
  "SET enable_bitmapscan = off; SET enable_seqscan = off; SET enable_sort = off;"
echo

# 3. Not a query the API issues (origin, destination and date are required); shown only for scale.
G=$(( TOTAL * 9 / 10 ))
echo "### Unfiltered (not issued by the API: no usable index, cost is mostly a full-table sort)"
header
measure "Unfiltered, offset, row $G"           "SELECT * FROM itineraries $ORDER LIMIT 20 OFFSET $G"
