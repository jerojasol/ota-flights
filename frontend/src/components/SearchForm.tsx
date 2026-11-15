import { useState, type FormEvent } from 'react'
import type { SearchState } from '../searchState'

export type FieldName = 'origin' | 'destination' | 'date' | 'maxPrice' | 'carrier'
export type FieldErrors = Partial<Record<FieldName, string>>

interface Props {
  initial: SearchState | null
  serverErrors: FieldErrors
  summaryErrors: string[]
  disabled: boolean
  onSubmit: (state: SearchState) => void
}

function tomorrow(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

interface Values {
  origin: string
  destination: string
  date: string
  maxPrice: string
  carrier: string
}

function validate(v: Values): FieldErrors {
  const errors: FieldErrors = {}
  if (!/^[A-Z]{3}$/.test(v.origin)) errors.origin = 'Enter a 3-letter airport code'
  if (!/^[A-Z]{3}$/.test(v.destination)) errors.destination = 'Enter a 3-letter airport code'
  else if (v.destination === v.origin) errors.destination = 'Destination must differ from origin'
  if (!v.date) errors.date = 'Choose a date'
  if (v.maxPrice !== '' && !(Number(v.maxPrice) > 0)) errors.maxPrice = 'Must be a positive amount'
  if (v.carrier !== '' && !/^[A-Z0-9]{2}$/.test(v.carrier)) errors.carrier = 'Enter a 2-character carrier code'
  return errors
}

export function SearchForm({ initial, serverErrors, summaryErrors, disabled, onSubmit }: Props) {
  const [values, setValues] = useState<Values>({
    origin: initial?.origin ?? '',
    destination: initial?.destination ?? '',
    date: initial?.date ?? tomorrow(),
    maxPrice: initial?.maxPrice?.toString() ?? '',
    carrier: initial?.carrier ?? '',
  })
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const errors = { ...serverErrors, ...clientErrors }

  function set(name: FieldName, value: string) {
    setValues((prev) => ({ ...prev, [name]: value }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const found = validate(values)
    setClientErrors(found)
    if (Object.keys(found).length > 0) return
    onSubmit({
      origin: values.origin,
      destination: values.destination,
      date: values.date,
      ...(values.maxPrice !== '' && { maxPrice: Number(values.maxPrice) }),
      ...(values.carrier !== '' && { carrier: values.carrier }),
    })
  }

  function field(name: FieldName) {
    const error = errors[name]
    return {
      id: name,
      name,
      value: values[name],
      'aria-invalid': error ? true : undefined,
      'aria-describedby': error ? `${name}-error` : undefined,
    }
  }

  function errorFor(name: FieldName) {
    const error = errors[name]
    return error ? (
      <span id={`${name}-error`} className="field-error">
        {error}
      </span>
    ) : null
  }

  return (
    <form className="search-form" onSubmit={handleSubmit} noValidate>
      {summaryErrors.length > 0 && (
        <ul className="error-summary" role="alert">
          {summaryErrors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      )}
      <div className="field">
        <label htmlFor="origin">Origin</label>
        <input {...field('origin')} required maxLength={3} autoComplete="off" placeholder="MAD"
          onChange={(e) => set('origin', e.target.value.toUpperCase())} />
        {errorFor('origin')}
      </div>
      <div className="field">
        <label htmlFor="destination">Destination</label>
        <input {...field('destination')} required maxLength={3} autoComplete="off" placeholder="JFK"
          onChange={(e) => set('destination', e.target.value.toUpperCase())} />
        {errorFor('destination')}
      </div>
      <div className="field">
        <label htmlFor="date">Date</label>
        <input {...field('date')} type="date" required onChange={(e) => set('date', e.target.value)} />
        {errorFor('date')}
      </div>
      <div className="field">
        <label htmlFor="maxPrice">Max price (EUR)</label>
        <input {...field('maxPrice')} type="number" min="0" step="any" inputMode="decimal"
          onChange={(e) => set('maxPrice', e.target.value)} />
        {errorFor('maxPrice')}
      </div>
      <div className="field">
        <label htmlFor="carrier">Carrier</label>
        <input {...field('carrier')} maxLength={2} autoComplete="off" placeholder="IB"
          onChange={(e) => set('carrier', e.target.value.toUpperCase())} />
        {errorFor('carrier')}
      </div>
      <button type="submit" disabled={disabled}>
        Search
      </button>
    </form>
  )
}
