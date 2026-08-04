# 同类开源项目对比与路线优化

调研时间：2026-08-03。调研对象限定为公开 GitHub 仓库，重点查看项目说明、架构、公开 Issue 和失败案例。仓库活跃度会变化，本文重点保留可复用的工程结论。

## 1. 对比项目

| 项目 | 主要功能 | 架构与技术栈 | 优点 | 对 DevMate AI 的限制或不足 |
|---|---|---|---|---|
| [Alibaba Open Code Review](https://github.com/alibaba/open-code-review) | Diff/全文件审查、规则匹配、代码搜索、行级评论 | Go 为主；确定性流水线 + Agent；按文件匹配规则；拆分审查单元 | 对覆盖范围、规则路由、上下文和定位都有硬约束，适合借鉴生产级审查流水线 | 通用多语言 CLI，不是 Java/Spring 业务平台；持久化知识库和多用户业务不是重点 |
| [PR-Agent](https://github.com/The-PR-Agent/pr-agent) | PR 描述、Review、Improve、Ask，多 Git 平台 | Python；Provider 抽象；PR 压缩；结构化 Prompt；单工具尽量单次模型调用 | 多平台适配成熟，Token 预算和大 PR 压缩值得借鉴 | 公开 Issue 表明压缩可能跳过文件却没有显式覆盖报告；部分静态分析能力属于商业版本 |
| [Vercel OpenReview](https://github.com/vercel-labs/openreview) | GitHub 评论触发、行级建议、运行测试、自动修复 | TypeScript/Next.js；GitHub App；Durable Workflow；隔离 Sandbox | Webhook、可恢复工作流和不可信代码隔离设计清晰 | 项目仍标注 Beta；偏 Vercel/Claude；需要较高 GitHub 写权限，第一版直接自动提交风险过高 |
| [Calimero AI Code Reviewer](https://github.com/calimero-network/ai-code-reviewer) | 安全/性能/逻辑等多 Agent 并行审查、共识评分、增量追踪 | Python；并行专用 Agent；GitHub Contents API；聚类、共识和收敛状态机 | 展示了去重、跨轮次追踪、置信度和专业分工的做法 | 多 Agent 增加 Token、延迟和状态复杂度；仓库规模和验证程度远小于前两者，不适合 MVP 直接照搬 |

## 2. 公开问题暴露的真实坑

### 2.1 “显示审查成功”不等于覆盖完整

PR-Agent 的 [Issue #2565](https://github.com/The-PR-Agent/pr-agent/issues/2565) 指出，大 PR 压缩可能裁剪 Patch 或跳过文件，但结果看起来仍像完成了全部审查。Alibaba OCR 的 [Issue #650](https://github.com/alibaba/open-code-review/issues/650) 也出现过 `.gitignore` 否定规则处理错误，最终静默审查 0 个文件。

**DevMate AI 决定：** 每次审查必须保存覆盖清单：总变更文件、完整审查、部分审查、跳过文件、跳过原因和 Token 截断情况。“没有发现问题”只能针对已审查范围，不能伪装成整个 PR 安全。

### 2.2 只给 Diff Hunk 会制造假问题

Calimero 的 [Issue #17](https://github.com/calimero-network/ai-code-reviewer/issues/17) 记录了模型只看到局部 Diff 后，把完整文件误判成“代码截断”。

**DevMate AI 决定：** Diff 用于限定审查范围，但模型判断前必须可以读取目标符号的完整方法、必要时完整文件，以及直接调用方/被调用方。禁止仅根据不完整 Hunk 判断括号、流程或资源关闭是否缺失。

### 2.3 行号不能由模型自由生成

Alibaba OCR 明确把位置漂移列为通用 Agent 的问题，并使用独立定位模块。

**DevMate AI 决定：** LLM 输出 `finding` 时引用 `chunkId/symbolId` 和证据片段；Java 服务根据 AST 和 Diff 映射真实行号。无法映射到当前 revision 的结果降级为普通建议，不发布行级评论。

### 2.4 多 Agent 不是免费精度

并行安全、性能、逻辑 Agent 可以通过共识减少部分误报，但会线性增加模型成本，还会带来重复 Finding、跨轮次状态膨胀和超时处理。

**DevMate AI 决定：** MVP 只使用“确定性检查 + 一个语义审查模型 + 一次反思/校验”。先通过固定评测集证明单 Agent 的问题，再决定是否只对高风险类别启用第二审查器。

### 2.5 不可信仓库必须当成数据

源码、README 和代码注释可能包含针对模型的指令。OpenReview 选择在隔离 Sandbox 中克隆并运行工具，同时要求 GitHub App 权限和 Webhook Secret。

**DevMate AI 决定：**

- 仓库文本在 Prompt 中标记为不可信证据，不能覆盖系统规则或触发工具。
- 默认只读源码，不执行仓库内脚本。
- 静态工具必须使用白名单命令、隔离工作目录、超时和资源限制。
- 第一版不自动修改、提交或合并代码。
- GitHub 接入优先采用 GitHub App 的细粒度权限，不在数据库明文保存个人 PAT。

## 3. 优化后的核心架构

```text
Git Revision / Pull Request
          ↓
确定性 Diff 与 Rename/Delete 识别
          ↓
审查覆盖清单（不能静默跳过）
          ↓
Java AST 符号定位 + 文件/模块分组
          ↓
静态规则和项目路径规则匹配
          ↓
按变更符号检索完整方法、调用链、配置、SQL、测试
          ↓
受 Token 预算约束的单 Agent 语义审查
          ↓
Finding 去重、证据校验、服务端行号映射
          ↓
覆盖报告 + 结构化审查结果 + 用户反馈
```

## 4. 开发路线调整

原计划先构建通用 RAG，再实现 Git Diff。调研后改为 Diff-first：

1. 完成当前 Java AST 解析和符号元数据。
2. 立即实现 Git Diff、Rename/Delete、变更方法和覆盖清单。
3. 接入第一种确定性 Java 静态规则，并完成服务端行号映射。
4. 围绕“变更符号需要什么上下文”建设检索，不做宽泛的全库聊天优先。
5. 增加按文件路径、注解和框架特征匹配的项目规则，例如事务、缓存、MQ 和 Mapper 规则。
6. 使用一个模型生成结构化 Finding，再做证据/位置校验和去重。
7. 建立固定缺陷集和反馈闭环后，再考虑 Agent Tool Calling、MQ 和多 Agent。

## 5. DevMate AI 的差异化

不与成熟项目竞争“支持最多 Git 平台”或“模型数量”，而是聚焦：

- Java/Spring 专项：理解 `@Transactional`、代理失效、线程池、Redis、RabbitMQ、MyBatis 和数据库边界。
- 证据优先：位置、符号、调用链、风险场景和验证方法由结构化数据支撑。
- 可学习、可解释：使用 Spring Boot、MySQL、JGit、AST、RAG 和 Agent 形成完整 Java 后端项目。
- 可评测：明确覆盖率、命中、漏报、误报、耗时和 Token，而不是只展示一段“看起来聪明”的回答。

## 6. 明确暂缓的功能

- 多 Git 平台适配。
- 多模型并行共识。
- 自动修复、提交和合并。
- 为所有语言建立规则库。
- 在没有评测前接入复杂向量数据库集群。
- 让模型执行仓库提供的任意构建或测试命令。
