export interface ReadingLocation {
  projectId?: string
  revision?: string
  item?: string
  conversation?: string
  chat?: string
  name?: string
  status?: string
}
export function parseReadingLocation(hash: string): ReadingLocation {
  const [path, query = ''] = hash.replace(/^#/, '').split('?')
  const match = /^\/projects(?:\/([A-Za-z0-9_-]{1,80}))?$/.exec(path || '')
  if (!match) return {}
  const params = new URLSearchParams(query)
  const result: ReadingLocation = { projectId: match[1] }
  for (const key of ['revision', 'item', 'conversation', 'chat', 'name', 'status'] as const) {
    const value = params.get(key)
    if (value && value.length <= (key === 'name' ? 100 : 120)) result[key] = value
  }
  return result
}
export function readReadingLocation() { return parseReadingLocation(window.location.hash) }
export function writeReadingLocation(patch: Partial<ReadingLocation>, replace = false) {
  const value = { ...readReadingLocation(), ...patch }
  if (!value.projectId) { value.revision = undefined; value.item = undefined; value.conversation = undefined; value.chat = undefined }
  const params = new URLSearchParams()
  for (const key of ['revision', 'item', 'conversation', 'chat', 'name', 'status'] as const) {
    if (value[key]) params.set(key, value[key])
  }
  const hash = '#/projects' + (value.projectId ? `/${encodeURIComponent(value.projectId)}` : '') + (params.size ? `?${params}` : '')
  if (hash === window.location.hash) return
  window.history[replace ? 'replaceState' : 'pushState'](null, '', hash)
  window.dispatchEvent(new Event('devmate-reading-change'))
}
export function subscribeReadingLocation(callback: () => void) {
  const events = ['hashchange', 'popstate', 'devmate-reading-change']
  events.forEach(event => window.addEventListener(event, callback))
  return () => events.forEach(event => window.removeEventListener(event, callback))
}
