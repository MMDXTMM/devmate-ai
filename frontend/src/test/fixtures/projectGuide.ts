import type { ProjectGuide } from '../../types/project'

export function guideFixture(): ProjectGuide {
  return {
    schemaVersion: 'project-guide-v1',
    languages: ['Python'],
    snapshot: {
      revision: 'abc',
      contentDigest: 'digest',
      readPolicy: 'sample-v1',
    },
    coverage: {
      indexedFiles: 10,
      readFiles: 2,
      readChunks: 2,
      readCharacters: 100,
      maxChunks: 40,
      maxCharacters: 48000,
      limitations: ['尚未运行'],
    },
    content: {
      overview: {
        highlights: [
          {
            title: '先检查，再汇总',
            moduleIds: ['convert'],
            problem: '输入文件缺失时无法继续汇总',
            solution: '读取前检查文件，失败时给出错误',
            learningValue: '学习如何在处理前验证输入',
            suggestedExercise: '阅读输入校验实现，再设计一个缺少文件的测试案例',
            limitation: '尚未运行目标项目',
            conclusionStatus: 'OBSERVED',
            implementationStatus: 'PARTIAL',
            evidenceIds: ['42'],
          },
        ],
      },
      project: {
        name: '表格助手',
        type: 'CLI',
        purpose: '帮助财务人员整理表格并输出汇总。',
        audience: '财务人员',
        input: '明细表',
        output: '汇总表',
        scenario: '帮助理解的示例：整理一周记录。',
        conclusionStatus: 'OBSERVED',
        implementationStatus: 'PARTIAL',
        evidenceIds: ['42'],
      },
      modules: [
        {
          id: 'convert',
          name: '整理记录',
          responsibility: '将明细整理成汇总',
          capabilities: ['读取表格', '按月合并'],
          input: '明细表',
          output: '汇总表',
          relatedModuleIds: [],
          conclusionStatus: 'INFERRED',
          implementationStatus: 'PARTIAL',
          evidenceIds: ['42'],
        },
      ],
      flows: [
        {
          id: 'run',
          name: '整理一次表格',
          goal: '得到月度汇总',
          actors: ['使用者'],
          trigger: '提供表格',
          moduleIds: ['convert'],
          steps: [
            {
              action: '检查输入',
              input: '文件',
              result: '可读才继续整理',
              moduleIds: ['convert'],
              conclusionStatus: 'OBSERVED',
              evidenceIds: ['42'],
            },
          ],
          rules: [
            {
              text: '文件不存在就返回错误',
              conclusionStatus: 'OBSERVED',
              evidenceIds: ['42'],
            },
          ],
          outcomes: [
            {
              text: '输出结果或错误原因',
              conclusionStatus: 'OBSERVED',
              evidenceIds: ['42'],
            },
          ],
          unknowns: ['大文件性能未知'],
        },
      ],
      glossary: [],
      openQuestions: ['当前是部分实现'],
    },
    evidence: [
      {
        id: '42',
        sourceKind: 'SOURCE_CODE',
        revision: 'abc',
        symbolName: 'main:1',
        filePath: 'src/main.py',
        startLine: 1,
        endLine: 5,
        code: 'print("<script>example</script>")',
        truncated: true,
      },
    ],
  }
}
