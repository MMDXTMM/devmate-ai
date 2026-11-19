export function sourceLanguage(path: string): string {
  const ext = path.toLowerCase().split('.').at(-1) ?? ''
  return ({ java: 'java', py: 'python', js: 'javascript', jsx: 'jsx', ts: 'typescript', tsx: 'tsx', vue: 'vue',
    go: 'go', rs: 'rust', c: 'c', h: 'c', cpp: 'cpp', cc: 'cpp', cs: 'csharp', php: 'php', rb: 'ruby',
    kt: 'kotlin', swift: 'swift', sql: 'sql', json: 'json', yaml: 'yaml', yml: 'yaml', toml: 'toml',
    md: 'markdown', rst: 'rst', sh: 'bash', html: 'html', css: 'css' } as Record<string, string>)[ext] ?? 'text'
}
export function sourceLabel(path: string, kind?: string): string {
  const lower = path.toLowerCase()
  if (/(^|\/)(tests?|specs?|__tests__)(\/|_)|(?:test|spec)\.[^.]+$|_test\.[^.]+$/.test(lower)) return '测试预期（未执行）'
  if (kind === 'DOCUMENTATION' || /\.(md|rst|adoc|txt)$/.test(lower)) return '文档描述'
  if (kind === 'CONFIGURATION' || /\.(ya?ml|toml|properties|json)$/.test(lower)) return '配置线索'
  if (kind === 'DATABASE_SCHEMA' || /\.sql$/.test(lower)) return '数据库定义'
  return '实现代码（静态阅读）'
}
