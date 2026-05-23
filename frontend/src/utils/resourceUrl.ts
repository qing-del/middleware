const BASE = (import.meta.env.VITE_SOURCE_REQUEST_BASE_URL as string | undefined) ?? ''

export function buildResourceUrl(path?: string): string {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  const base = BASE.replace(/\/+$/, '')
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${base}${suffix}`
}
