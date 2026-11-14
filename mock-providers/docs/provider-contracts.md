# Provider Contracts

This document is the **interface contract** between `mock-providers` and `flight-aggregator`. The aggregator's provider adapters (`infrastructure/provider/{alpha,beta,gamma}`) are written against this file, and the fixture files used in the aggregator's mapper tests must match the examples here byte-for-byte in structure.

Change this document first, code second.

All three providers answer the same business question — *"which itineraries do you sell from X to Y on date D?"* — but each has its own URL shape, parameter names, content type, timestamp convention, price representation and error format. That inconsistency is deliberate.

---

## Alpha — JSON, flat

**Request**

```
GET /alpha/flights?from=MAD&to=JFK&date=2026-11-20
Accept: application/json
```

| Param | Format | Required |
|---|---|---|
| `from` | IATA 3-letter, uppercase | yes |
| `to` | IATA 3-letter, uppercase | yes |
| `date` | `yyyy-MM-dd` | yes |

**Response `200`** — `Content-Type: application/json`

A flat JSON array. Each element is one itinerary; segments are an ordered array inside it.

```json
[
  {
    "id": "ALP-7f3a9c",
    "price": 512.30,
    "currency": "EUR",
    "segments": [
      {
        "carrier": "IB",
        "flightNo": "6251",
        "from": "MAD",
        "to": "JFK",
        "dep": "2026-11-20T07:35:00Z",
        "arr": "2026-11-20T16:10:00Z"
      }
    ]
  },
  {
    "id": "ALP-b21e04",
    "price": 438.90,
    "currency": "EUR",
    "segments": [
      { "carrier": "IB", "flightNo": "3166", "from": "MAD", "to": "LHR", "dep": "2026-11-20T06:00:00Z", "arr": "2026-11-20T08:25:00Z" },
      { "carrier": "BA", "flightNo": "0117", "from": "LHR", "to": "JFK", "dep": "2026-11-20T10:40:00Z", "arr": "2026-11-20T18:55:00Z" }
    ]
  }
]
```

Conventions:
- Timestamps: ISO-8601, **always UTC** with `Z` suffix.
- `price`: JSON number with two decimals. `currency`: ISO 4217.
- `flightNo`: digits only, may have leading zeros; the carrier is separate.
- Empty result is `[]`, not `404`.

**Response `400`** — malformed parameters

```json
{ "error": "BAD_REQUEST", "message": "Parameter 'from' must be a 3-letter IATA code" }
```

**Response `500`** — chaos

```json
{ "error": "INTERNAL", "message": "Upstream inventory unavailable", "ref": "a1b2c3" }
```

---

## Beta — XML, nested

**Request**

```
GET /beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20
Accept: application/xml
```

| Param | Format | Required |
|---|---|---|
| `origin` | IATA 3-letter | yes |
| `destination` | IATA 3-letter | yes |
| `departureDate` | `yyyy-MM-dd` | yes |

**Response `200`** — `Content-Type: application/xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SearchResponse xmlns="urn:beta-gds:search:v2">
  <Meta>
    <RequestId>BET-20261120-0042</RequestId>
    <Currency>EUR</Currency>
  </Meta>
  <Itineraries count="2">
    <Itinerary ref="BET-91ac">
      <Pricing>
        <Total amount="519.00" currency="EUR"/>
      </Pricing>
      <Legs>
        <Leg sequence="1">
          <Flight>
            <Airline code="IB"/>
            <Number>6251</Number>
          </Flight>
          <Departure>
            <Airport code="MAD"/>
            <LocalDateTime>2026-11-20T08:35:00</LocalDateTime>
            <TimeZone>Europe/Madrid</TimeZone>
          </Departure>
          <Arrival>
            <Airport code="JFK"/>
            <LocalDateTime>2026-11-20T11:10:00</LocalDateTime>
            <TimeZone>America/New_York</TimeZone>
          </Arrival>
        </Leg>
      </Legs>
    </Itinerary>
    <Itinerary ref="BET-02fd">
      <Pricing>
        <Total amount="455.40" currency="EUR"/>
      </Pricing>
      <Legs>
        <Leg sequence="1">
          <Flight><Airline code="IB"/><Number>3166</Number></Flight>
          <Departure><Airport code="MAD"/><LocalDateTime>2026-11-20T07:00:00</LocalDateTime><TimeZone>Europe/Madrid</TimeZone></Departure>
          <Arrival><Airport code="LHR"/><LocalDateTime>2026-11-20T08:25:00</LocalDateTime><TimeZone>Europe/London</TimeZone></Arrival>
        </Leg>
        <Leg sequence="2">
          <Flight><Airline code="BA"/><Number>117</Number></Flight>
          <Departure><Airport code="LHR"/><LocalDateTime>2026-11-20T10:40:00</LocalDateTime><TimeZone>Europe/London</TimeZone></Departure>
          <Arrival><Airport code="JFK"/><LocalDateTime>2026-11-20T13:55:00</LocalDateTime><TimeZone>America/New_York</TimeZone></Arrival>
        </Leg>
      </Legs>
    </Itinerary>
  </Itineraries>
</SearchResponse>
```

Conventions:
- Timestamps are **local time without offset** plus a separate IANA `TimeZone` element. The consumer must combine both to get an instant. This is the main trap of Beta.
- `Total/@amount` is a **string** with two decimals; currency is an attribute.
- `Number` has **no leading zeros** (`117`, not `0117`). Flight identity must be normalised by the consumer.
- `Leg/@sequence` is 1-based and legs may arrive **out of order** in the document; always sort by `sequence`.
- Empty result: `<Itineraries count="0"/>`.
- Namespace `urn:beta-gds:search:v2` is present on the root element.

**Response `400`**

```xml
<Fault xmlns="urn:beta-gds:search:v2">
  <Code>INVALID_REQUEST</Code>
  <Message>Parameter 'origin' is required</Message>
</Fault>
```

**Response `500`** — chaos

```xml
<Fault xmlns="urn:beta-gds:search:v2">
  <Code>SYSTEM_ERROR</Code>
  <Message>Inventory backend timed out</Message>
  <Reference>b4c5d6</Reference>
</Fault>
```

---

## Gamma — CSV, streamed

**Request**

```
GET /gamma/export?o=MAD&d=JFK&dt=20261120
Accept: text/csv
```

| Param | Format | Required |
|---|---|---|
| `o` | IATA 3-letter | yes |
| `d` | IATA 3-letter | yes |
| `dt` | `yyyyMMdd` | yes |

**Response `200`** — `Content-Type: text/csv; charset=UTF-8`, `Transfer-Encoding: chunked`

The body is streamed line by line. First line is a header. One line per itinerary. Segments are packed into a single field separated by `|`, and each segment's attributes separated by `;`.

```
ref,price_cents,currency,segments
GAM-5e1f,51230,EUR,IB;6251;MAD;JFK;202611200735;202611201610
GAM-8a77,43890,EUR,IB;3166;MAD;LHR;202611200600;202611200825|BA;0117;LHR;JFK;202611201040;202611201855
GAM-c003,47000,EUR,UX;0091;MAD;JFK;202611201200;202611202045
```

Segment field layout (positional): `carrier;flightNo;from;to;dep;arr`

Conventions:
- Timestamps: `yyyyMMddHHmm`, **UTC**, no separator, no seconds.
- `price_cents`: integer, minor units. `51230` means `512.30`.
- `flightNo`: zero-padded to 4 digits.
- No quoting, no embedded commas; fields never contain `,`, `;` or `|`.
- The response can be **large** (tens of thousands of lines). Consumers must stream it, not buffer it.
- Empty result: header line only.
- A trailing line `#END` marks a complete stream. If the consumer does not see it, the stream was truncated (chaos may cut streams mid-way in a future version; today it does not).

**Response `400`** — `Content-Type: text/plain`

```
ERR 400 missing parameter: o
```

A present but malformed parameter (e.g. `dt=2026-11-20`) uses the same shape: `ERR 400 invalid parameter: dt`.

**Response `500`** — chaos, `Content-Type: text/plain`

```
ERR 500 export failed ref=c7d8e9
```

---

## Cross-provider semantics

### Identity of an itinerary
Two itineraries from different providers represent the **same product** when they have the same ordered sequence of `(carrier, flightNo normalised without leading zeros, departure instant in UTC, from, to)`. Providers never share `id`/`ref`/`ref` values; those are provider-local.

### Overlap
By default ~30% of itineraries are present in two or three providers with identical flights and times but **different prices** (spread of roughly ±5%). The aggregator is expected to keep the cheapest.

### Deliberately invalid itineraries
By default ~5% of itineraries across all providers are invalid in one of these ways:

| Kind | Example |
|---|---|
| Dead leg — same airport | segment `MAD → MAD` |
| Dead leg — non-positive duration | `arr` ≤ `dep` |
| Connection airport mismatch | leg 1 arrives `LHR`, leg 2 departs `CDG` |
| Connection overlap | leg 2 departs before leg 1 arrives |
| Connection too short | 10-minute connection |

The aggregator is expected to drop these and count them per provider.

### Chaos
- Latency: uniform random in `[MOCK_LATENCY_MIN, MOCK_LATENCY_MAX]`, default `[100ms, 5000ms]`, applied to every provider request including errors.
- Errors: each request independently fails with probability `MOCK_ERROR_RATE`, default `0.10`.
- `/actuator/health` is never affected.

### Admin

```
PUT /admin/chaos
Content-Type: application/json

{ "enabled": true, "errorRate": 0.5, "latencyMin": "100ms", "latencyMax": "2000ms" }
```

Returns the effective configuration. Intended for live demos ("watch the aggregator survive Beta dying").

```
GET /admin/chaos
```

Returns the current configuration.
