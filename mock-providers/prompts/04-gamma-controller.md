# C4 — Gamma controller (streamed CSV)

Read `docs/provider-contracts.md` → "Gamma". Load the `contract-sync` skill. Work in `src/main/java/io/github/jerojasol/mockproviders/gamma/`.

## Task

### `GammaLineFormatter`
`String format(GeneratedItinerary it)` →

```
ref,price_cents,currency,segments
GAM-5e1f,51230,EUR,IB;6251;MAD;JFK;202611200735;202611201610
```
- `ref` = `"GAM-" + first 4 hex of sha256(canonicalId)`.
- `price_cents` integer.
- Segments joined with `|`; fields with `;` in order `carrier;flightNo;from;to;dep;arr`.
- `flightNo` **zero-padded to 4 digits**.
- `dep`/`arr` = `yyyyMMddHHmm` in **UTC**.
- Header line constant `ref,price_cents,currency,segments`; trailer constant `#END`.

### `GammaController`
`GET /gamma/export?o=&d=&dt=` (`dt` = `yyyyMMdd`) → `StreamingResponseBody`, `Content-Type: text/csv; charset=UTF-8`.
- Write header, then one line per itinerary, flushing every 200 lines so the client really receives a stream, then `#END`.
- Do **not** build the whole body in memory; iterate the generator's list and write line by line. (The list itself is in memory — acceptable; the point is the HTTP body streams.)
- Validation errors → 400, `text/plain`, body `ERR 400 missing parameter: o` (exact format from the contract).
- Empty → header + `#END`.

### Error writer for chaos
Package-private `GammaErrorWriter` producing `ERR 500 export failed ref=<6 hex>` as `text/plain`.

## Tests

`GammaControllerTest` (`@WebMvcTest` with `asyncDispatch`, since `StreamingResponseBody` is async):
- 200, `text/csv`, first line is the exact header, last line is `#END`.
- a line splits into 4 comma fields; the 4th splits on `|` into 1–3 segments; each segment splits on `;` into 6 fields; field 2 matches `\d{4}`; fields 5–6 match `\d{12}`.
- `dt=2026-11-20` (wrong format) → 400 `text/plain` starting with `ERR 400`.
- empty → exactly two lines.

`GammaLineFormatterTest` (unit): flight 117 → `0117`; `2026-11-20T07:35Z` → `202611200735`; 512.30 → `51230`.

## Acceptance

- `./mvnw -q verify` green.
- `curl -N 'localhost:8081/gamma/export?o=MAD&d=JFK&dt=20261120' | head -3` shows header + two lines; `| tail -1` shows `#END`.

## Closing

`/log-ai-decision`, show diff summary.
