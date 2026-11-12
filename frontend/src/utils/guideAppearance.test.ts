import { describe, expect, it } from 'vitest'
import { moduleEmoji, projectEmoji, safeEmoji } from './guideAppearance'

describe('model-selected appearance', () => {
  it('renders arbitrary new project domains from saved analysis rather than name keywords', () => {
    const appearance = { projectIcon: '🔭', modules: [{ moduleId: 'spectral', icon: '🌈' }, { moduleId: 'tracking', icon: '🛰️' }] }
    expect(projectEmoji({}, appearance)).toBe('🔭')
    expect(moduleEmoji({ id: 'spectral' }, appearance)).toBe('🌈')
    expect(moduleEmoji({ id: 'tracking' }, appearance)).toBe('🛰️')
    expect(projectEmoji({ icon: '🔭' })).toBe('🔭')
    expect(projectEmoji({ icon: '🌱' }, appearance)).toBe('🔭')
  })
  it('uses stable neutral icons for old reports and missing or unknown module IDs', () => {
    expect(projectEmoji({})).toBe('📁')
    expect(projectEmoji({}, null)).toBe('📁')
    expect(moduleEmoji({ id: 'new' })).toBe('🧩')
    expect(moduleEmoji({ id: 'missing' }, { moduleIcons: { other: '🦋' } })).toBe('🧩')
    expect(moduleEmoji({ id: 'toString' }, { moduleIcons: {} })).toBe('🧩')
  })
  it.each(['🔬', '🛰️', '🧑‍💻', '🌈', '🇨🇳', '🦋'])('accepts a single Unicode emoji %s', value => {
    expect(safeEmoji(value)).toBe(value)
  })
  it.each(['', 'abc', '🌱🌼', '🔬 explanation', '<img src=x>', 'https://example.com/icon.svg', 'data:image/svg+xml,test', '🔭'.repeat(50), ' 🔭', '\n🔭'])('rejects invalid model presentation %s', value => {
    expect(safeEmoji(value)).toBeUndefined()
    expect(projectEmoji({ icon: value })).toBe('📁')
    expect(moduleEmoji({ id: 'x' }, { moduleIcons: { x: value } })).toBe('🧩')
  })
})
