import type { Price } from './api/types'

const DURATION = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/

export function formatDuration(iso: string): string {
  const match = DURATION.exec(iso)
  if (!match) return iso
  const [, days = '0', hours = '0', minutes = '0'] = match
  const totalHours = Number(days) * 24 + Number(hours)
  return `${totalHours}h ${Number(minutes)}m`
}

export function formatPrice(price: Price): string {
  return new Intl.NumberFormat('en-GB', { style: 'currency', currency: price.currency }).format(price.amount)
}

const UTC_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'UTC',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

export function formatUtc(instant: string): string {
  const date = new Date(instant)
  if (Number.isNaN(date.getTime())) return instant
  const parts = Object.fromEntries(UTC_FORMAT.formatToParts(date).map((p) => [p.type, p.value]))
  return `${parts.day} ${parts.month} ${parts.hour}:${parts.minute} UTC`
}
