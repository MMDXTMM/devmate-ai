import type { GuideAppearance, GuideModule, Project } from '../types/project'

// The model chooses meaning from README/source evidence. The UI only validates
// presentation; it never guesses a project's domain from a keyword dictionary.
export function safeEmoji(value: unknown): string | undefined {
  if (typeof value !== 'string' || value.length > 32) return undefined
  const graphemes = [...new Intl.Segmenter('und', { granularity: 'grapheme' }).segment(value)]
  if (graphemes.length !== 1) return undefined
  return /[\u{1F000}-\u{1FAFF}\u2600-\u27BF\u2300-\u23FF]/u.test(value) ? value : undefined
}

export function projectEmoji(project: Pick<Project, 'icon'>, appearance?: GuideAppearance | null): string {
  return safeEmoji(appearance?.projectIcon) ?? safeEmoji(project.icon) ?? '📁'
}

export function moduleEmoji(module: Pick<GuideModule, 'id'>, appearance?: GuideAppearance | null): string {
  const chosen = appearance?.modules?.find(item => item.moduleId === module.id)
  if (chosen) return safeEmoji(chosen.icon) ?? '🧩'
  const icons = appearance?.moduleIcons
  return safeEmoji(icons && Object.hasOwn(icons, module.id) ? icons[module.id] : undefined) ?? '🧩'
}
