# DevMate AI 一次性账号切换恢复流程

更新时间：2026-08-06

文件名为兼容历史链接而保留。本文件不再提供需要反复复制的长提示词，只定义新账号第一次进入项目时执行一次的历史恢复流程。恢复完成后按正常开发流程工作，不在每个任务重复读取历史。

## 1. 结论

- 同一 Codex 环境中，可从任务列表继续保存的任务；Codex CLI 也可使用 `/resume` 或 `codex resume`。这只用于继续当前环境中可见的已保存任务。
- OpenAI 官方资料没有提供把两个 OpenAI 账号的 Codex 任务、ChatGPT 侧边栏和记忆原生合并为一个账号的机制。跨账号时不得假定新账号自动拥有旧账号上下文。
- ChatGPT 可从旧账号导出数据，再把 `conversations.json` 上传到新账号的一段对话中作为一次性历史参考；这不会重建原来的侧边栏对话，也不等于迁移记忆、设置、订阅或权限。
- Codex 的本地记忆与 ChatGPT 记忆不是项目事实。长期有效的规则和进度必须进入 Git 中的 `AGENTS.md`、`docs/CODEX_HANDOFF.md`、`docs/PROJECT_LOG.md` 和设计文档。
- `~/.codex/auth.json` 是登录凭证，不是历史记录。禁止复制到仓库、对话或交接文件。

官方参考：

- [Projects and chats](https://learn.chatgpt.com/docs/projects)
- [Memories](https://learn.chatgpt.com/docs/customization/memories)
- [Authentication](https://learn.chatgpt.com/docs/auth)
- [Transferring conversations between ChatGPT accounts](https://help.openai.com/en/articles/9106926-transferring-conversations-from-1-chatgpt-account-to-another-chatgpt-account)

## 2. 什么时候执行

只在以下任一情况执行一次：

1. 刚切换到没有本项目上下文的新 Codex 账号。
2. 换电脑或重新克隆仓库，当前任务无法确认项目暂停点。
3. 交接文档、工作区和聊天记录互相冲突，需要重新建立可信基线。

同一账号后续开发、普通新任务或功能切换不重复执行。完成一次恢复后，当前任务直接依据 `CODEX_HANDOFF.md` 的“下一小任务”继续。

## 3. 一次性恢复输入

新账号应读取：

1. 根目录 `AGENTS.md`。
2. `docs/CODEX_HANDOFF.md`。
3. `docs/ENGINEERING_STANDARDS.md`。
4. `docs/DEVELOPMENT_ROADMAP.md`。
5. `docs/PROJECT_LOG.md`。
6. 当前小任务对应的设计文档和测试。
7. 必要时读取用户提供的旧账号 `conversations.json`，但只抽取未进入仓库的重要背景。

不要默认读取全部历史聊天。只有出现“为什么这样设计”且 Git/文档没有答案时，才在导出记录中定向搜索相关关键词。导出文件可能包含隐私、源码或密钥，不得提交到 Git；读取完成后只把必要且已核实的决策摘要写入项目文档。

## 4. 一次性恢复检查

在修改代码前核对：

```bash
pwd
git status -sb
git log -5 --oneline --decorate
git remote -v
git fetch origin
gh pr list --state open
```

然后确认：

- 当前远端 `main`、最近已合并 PR 和当前工作分支。
- 工作区修改属于谁，是否有未提交文件或构建产物。
- Flyway 最新版本、数据库兼容要求和真实 MySQL 验收状态。
- 后端、前端、Benchmark 的最近测试基线是否仍能复现。
- 哪些结果来自 Mock/Fake，哪些来自真实 Git、MySQL 或模型。
- 当前精确暂停点、唯一下一小任务、输入输出、状态变化、失败路径和测试方案。

事实优先级固定为：

```text
Git/已合并 PR/当前测试/数据库
  > CODEX_HANDOFF 与设计文档
  > PROJECT_LOG
  > 导出的聊天记录和口述
```

历史记录声称“完成”但没有代码、测试、数据库或 PR 证据时，按未验证处理。

## 5. 恢复完成的输出

新账号只需输出一份简短恢复报告：

```text
已完成一次性历史恢复。
当前 main/分支：...
未提交或未合并工作：...
数据库与测试基线：...
尚未真实验证：...
下一小任务：...
本任务验收：...
```

输出后立即进入开发，不再要求用户反复粘贴提示词，也不在每次任务重读 `conversations.json`。

## 6. 旧账号切换前要留下什么

- 一个可验证的小闭环提交和对应 PR，或明确记录尚未提交的原因。
- 最新 `CODEX_HANDOFF.md`：分支、commit、PR、测试、数据库版本、暂停点和下一任务。
- 业务或架构变化写入 `PROJECT_LOG.md` 和对应设计文档。
- 环境只记录变量名，不记录密码、Token、Cookie、完整 Prompt 或私有源码。
- 面试材料只写已经实现、测试并能够解释的能力。

无需为新账号编写一大段重复提示词。仓库本身就是可验证的交接载体；聊天导出只在第一次恢复时补充缺失背景。
