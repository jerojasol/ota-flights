import { describe, expect, it } from 'vitest'
import { formatDuration, formatPrice, formatUtc } from './format'

describe('formatDuration', () => {
  it('formats hours and minutes', () => {
    expect(formatDuration('PT8H35M')).toBe('8h 35m')
  })

  it('folds days into hours', () => {
    expect(formatDuration('P1DT2H')).toBe('26h 0m')
  })

  it('handles minutes only', () => {
    expect(formatDuration('PT45M')).toBe('0h 45m')
  })
})

describe('formatPrice', () => {
  it('formats with currency symbol and two decimals', () => {
    expect(formatPrice({ amount: 512.3, currency: 'EUR' })).toBe('€512.30')
  })
})

describe('formatUtc', () => {
  it('formats an instant in UTC and labels it', () => {
    expect(formatUtc('2026-11-20T07:35:00Z')).toBe('20 Nov 07:35 UTC')
  })

  it('does not shift to another zone', () => {
    expect(formatUtc('2026-11-20T23:50:00Z')).toBe('20 Nov 23:50 UTC')
  })
})
