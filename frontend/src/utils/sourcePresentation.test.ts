import { describe, expect, it } from 'vitest'
import { sourceLanguage, sourceLabel } from './sourcePresentation'
describe('source presentation', () => {
  it('labels source, unexecuted tests, documentation and unknown languages honestly', () => {
    expect(sourceLanguage('src/signer.py')).toBe('python')
    expect(sourceLanguage('README.md')).toBe('markdown')
    expect(sourceLanguage('README.rst')).toBe('rst')
    expect(sourceLanguage('src/App.java')).toBe('java')
    expect(sourceLanguage('unknown.bin')).toBe('text')
    expect(sourceLabel('tests/test_signer.py', 'SOURCE_CODE')).toBe('测试预期（未执行）')
    expect(sourceLabel('src/FooTest.java', 'SOURCE_CODE')).toBe('测试预期（未执行）')
    expect(sourceLabel('README.rst')).toBe('文档描述')
    expect(sourceLabel('src/signer.py', 'SOURCE_CODE')).toBe('实现代码（静态阅读）')
    expect(sourceLabel('config/app.yaml')).toBe('配置线索')
  })
})
