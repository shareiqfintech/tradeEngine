/** Drops undefined/empty-string values so filter objects serialize to clean query strings. */
export function cleanParams(params: object): Record<string, string | number | boolean> {
  const result: Record<string, string | number | boolean> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    result[key] = value as string | number | boolean
  }
  return result
}
