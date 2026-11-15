# Manga 执行过程与用户确认修订

## 请求与恢复链路

```text
模型 / 工具
  → 正文与供应商分析分别按回复、文本块合并
  → 每 Step 4096 项有界缓冲
  → 校验 owner、epoch、lease 与截止时间
  → MySQL Journal 事务分配序号并提交
      ├─ Redis 唤醒 → SSE 公共条目 → 前端连续序号归并
      └─ 投影唤醒 → 独立消息检查点 → 批量更新 Message 和水位
```

MySQL 仍是事实来源，没有 Redis 异步回写。SSE 的 `activity` 与时间线分页使用同一公开映射；子 Agent 正文和供应商分析可以恢复，但不注入主助手的 Message 或会话记忆。供应商没有分析字段时不伪造内容。

正文与分析分别按 200ms 或 1 KiB 合并，工具参数增量仍被过滤。公开条目版本为 3，新增计划、轮次、执行代次、回复块、输入指纹、复用标识、目标引用及安全错误。旧事件缺失这些字段时仍展示原有名称、状态和时间。

前端收到 SSE 后立即归并连续序号，重复事件忽略，缺口通过时间线分页补齐；每页成功即可展示。Run、Graph 和修订确认快照独立加载，不再阻塞正文渲染。聊天正文在 Message 上叠加尚未覆盖的主 Agent 事件，使用消息 `lastSequence` 和历史 `projections` 消除重复。

## 评审与修订状态机

```text
生成候选 → 评审 → 汇合
                  ├─ PASS → 恢复导演 → ChangeSet 提案 → 用户批准后领域写入
                  └─ NEEDS_REPAIR → WAITING_REPAIR_CONFIRMATION
                                     ├─ 确认 → 修订候选 → 再次评审 → 汇合
                                     ├─ 结束 / 过期 → CANCELLED，保留原候选
                                     └─ 版本变化 → CONFLICTED + Run FAILED
```

每份评审最多创建一个确认单。确认单固化原计划、轮次、候选及评审 Step、问题、项目与章节 revision、暂停时剩余执行时间、决定请求和操作者。确认只授权生成下一份候选，不批准正式分镜写入。

用户确认后直接复用原候选 Worker 与评审 Worker 的冻结 Blueprint，物化“修订、评审、汇合”三个节点。原候选和原评审通过明确的依赖边连接新修订节点，不授予同 Run 所有结果的读取权。修订提交前必须已经成功读取这两个引用；读取审计和结果提交均使用仅更新目标字段的 owner 围栏 SQL，避免覆盖并发心跳的新 lease。

修订等待默认 24 小时。等待期间撤销旧 StateSession 写入许可，不持有 Harness、Kernel 或执行租约；确认后用剩余执行时间重新计算 Run 截止时间，并登记新的独立 StateSession。原 Run 冻结的步骤、模型和最多两轮修订预算不会重置。停止、过期、冲突与重复确认均在 Run 锁内收敛。

接口：

| 接口 | 行为 |
| --- | --- |
| `GET /api/v1/creative-runs/{runId}/repair-decisions` | 所有者查询确认历史、问题及未批准候选 |
| `POST /api/v1/creative-runs/{runId}/repair-decisions/{decisionId}/decide` | `requestId`、`action=CONFIRM_REPAIR/STOP`、可选 `comment`；同一决定重复执行不创建新计划 |

## 消息投影、重复操作与故障边界

- 每 Run 的投影检查点保存水位、领取者和 lease，投影批次锁定该行，不在整个批次持有 Run 行锁。消息变化、水位及 Run 兼容水位镜像在同一事务提交，镜像更新置于批次尾部。
- 提交后唤醒按 Run 去重；专用队列饱和只丢弃唤醒提示，由水位扫描补偿，不取消模型。损坏事件使整个批次回滚，修复后从断点继续。
- 历史读取不再同步推进全部投影。新会话轮次在取得会话锁之前补齐已结束轮次的消息，避免旧回答缺失；重建按“投影检查点 → 会话”的顺序加锁，避免反向等待。
- 事件热路径复用已经加锁的最新 Run 快照；文本写入不再逐条续租，独立心跳仍持续续租。读取工具结束不再触发三次检查点查询，只检查可能提交提案、委派或 Worker 结果的工具。
- 同一 Harness 缓存已授权的不可变结果读取，正式资源缓存包含项目 revision。恢复或新 Harness 不共享缓存。相同调用仍保留独立 toolCallId，展示目标、调用次数和是否复用，不以名称吞并不同操作。
- 连续三轮没有新事实或检查点、仅有相同读取或错误时，以 `AGENT_NO_PROGRESS` 在下一次计费之前结束。Compaction 不计入行动停滞轮次；数据库或所有权等致命错误也禁止后续模型及 Compaction 调用。
- 工具 schema 错误返回字段路径与约束，最多 20 项，不返回非法值。公共时间线记录安全错误，原始 SQL、凭据、系统提示词和完整供应商响应不进入条目。

## 前端交互

任务中心按计划轮次和 Agent 展示正文、供应商分析、工具与最终报告。当前步骤默认展开，完成后的步骤保留摘要；用户手动折叠会优先于自动状态，并按 Run 和稳定条目标识在当前浏览器会话恢复。工具、供应商分析、长报告均可折叠，支持“全部收起”；不增加任务中心最小化。

用户位于底部时跟随新内容，查看历史时保留位置并提示“有新内容”。事件水位显示“已显示 / 已知”，帮助区分没有输出、日志尚未提交与客户端补读。修订面板支持候选预览、问题清单、补充意见、确认修订和结束任务。

## 配置与可观察性

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `manga.creative.journal.event-queue-capacity` | 4096 | 每个 Step 的合并事件预取数，不是字节上限 |
| `manga.creative.journal.max-threads` | 16 | Journal 专用底层线程上限 |
| `manga.creative.journal.scheduler-queue-capacity` | 2048 | 每个底层线程的调度队列上限 |
| `manga.creative.projection.max-threads` | 2 | 独立投影线程上限 |
| `manga.creative.projection.queue-capacity` | 256 | 每个底层投影线程的调度队列上限 |
| `manga.creative.projection.lease` | 30s | 投影批次领取租约 |
| `manga.creative.repair.confirmation-ttl` | 24h | 用户确认等待期限 |
| `manga.creative.activity.max-no-progress-turns` | 3 | 连续无进展行动轮次 |

对应环境变量在 `application.yml` 有字段注释。批量投影大小沿用 `manga.creative.memory.projection-batch-size`。

Micrometer 指标包括 `manga.creative.event.queue`、`manga.creative.event.persist`、`manga.creative.projection.lag` 和 `manga.creative.projection.backlog.events`。不添加 Run ID 等高基数指标标签；backlog 表示最近一次处理批次的剩余事件数，不是全局总和。

## 数据库、验收与启动

`V15__creative_repair_and_projection.sql` 扩展 Step 状态约束，增加跨轮次已读结果字段，创建修订确认及独立消息投影检查点，并从 Run 回填原投影水位。表和字段均有注释、创建/更新时间和操作者字段。结果工具协议实现版本升为 2，历史 Blueprint 保留原指纹供只读展示。

升级前等待活动 Run 和外部作业收敛，再启动新后端应用迁移并刷新前端；不要在线改写已经物化的旧计划。迁移不删除旧任务、候选、消息或事件。不把模型生成、修订确认和 ChangeSet 批准合并成一个操作。

验证命令：

```powershell
# Java 21，后端目录
mvn.cmd -o -q test

# 前端目录
node --test test/creativeTimeline.test.js
npm.cmd run build
npm.cmd run test:browser
```

浏览器验收启动独立 Vite 与本机假 API，使用实际组件、真实分段 SSE 和隔离 Edge 上下文；不读取用户登录状态或调用模型。默认使用已安装的 Edge，可用 `MANGA_BROWSER_CHANNEL` 或 `MANGA_BROWSER_EXECUTABLE` 指定其他已安装浏览器。截图保存在忽略目录 `Manga-server/target/creative-process-browser/`。

覆盖确认前零模型调用、并发幂等、来源读取、冻结规格、剩余时间恢复、过期/停止/冲突、旧 StateStore 写入拒绝、慢投影不阻塞事件与历史、投影损坏回滚、缓存版本和安全错误、流式先于快照、重连去重、折叠恢复、候选预览及窄屏。真实供应商验收仍需用户批准生成费用。

建议 commit message：

```text
feat: 完善创作过程流式展示与用户确认修订流程

- 按轮次和 Agent 展示正文、供应商分析、工具与可折叠报告
- 增加修订确认、候选引用和执行计时暂停，复用冻结规格恢复
- 独立消息投影检查点和有界调度，优化事件写入与断线恢复
- 增加读取复用、明确目标摘要、字段错误及无进展保护
- 补充 V15 迁移、并发恢复测试及隔离浏览器验收
```
