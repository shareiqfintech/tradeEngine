import type { OptionType } from '@/types'

export interface ParsedOptionSymbol {
  underlying: string
  /** Raw middle segment of the symbol (expiry encoding) - NOT decoded into a date, since Groww's
   *  weekly/monthly symbol grammar isn't guaranteed and a wrong guess is worse than showing the raw code. */
  expiryCode: string
  strike: number
  optionType: OptionType
}

const OPTION_SYMBOL_PATTERN = /^([A-Z]+)(\d+[A-Z0-9]+?)(\d+)(CE|PE)$/

/**
 * Best-effort, DISPLAY-ONLY parse of a Groww F&O trading symbol
 * (e.g. "NIFTY25SEP25000CE") into underlying/strike/optionType for the
 * Positions page. This is cosmetic label-splitting of a symbol the backend
 * already fully resolved (OptionContractResolver) - it performs no trading
 * decision and is never used to compute quantity, risk, or order routing.
 * Returns null if the symbol doesn't match the expected option-contract shape
 * (e.g. an equity symbol), in which case callers should show the raw string.
 */
export function parseOptionSymbol(tradingSymbol: string): ParsedOptionSymbol | null {
  const match = OPTION_SYMBOL_PATTERN.exec(tradingSymbol.trim().toUpperCase())
  if (!match) return null
  const [, underlying, expiryCode, strike, optionType] = match
  return {
    underlying,
    expiryCode,
    strike: Number(strike),
    optionType: optionType as OptionType,
  }
}
