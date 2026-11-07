# 阶段 5A：图片生成作业

本阶段实现 Fake Provider 与用户指定的 OpenAI `gpt-image-2`。视频、批量生成与长视频治理属于后续 5B；当前实现不包含这些能力。代码已经连接前后端，真实供应商调用和进程重启等运行验收尚未执行。

## 请求链路

```text
分镜页选择镜头、图片配置、尺寸、提示词
  或在当前项目会话中明确提出图片生成要求
    → Creative Run / Director（冻结 Blueprint）
    → propose_image_generation
    → 图片 ChangeSet 草稿（目标归属、revision、配置快照、服务端预计费用）
    → 关闭模型流、释放 HarnessLease / KernelHandle
    → WAITING_APPROVAL
    → 用户预览提示词、供应商、配置端点、尺寸及费用
    → APPROVE
        同一事务：revision / 费用 / 端点复核
                 Approval + ChangeSet + GenerationJob + Submission Outbox
                 Step：WAITING_APPROVAL → WAITING_JOB
    → AFTER_COMMIT 立即唤醒媒体 worker
    → Run → Job → Capacity 行锁，领取 owner epoch / lease
    → 提交事务结束后才调用供应商
    → 供应商结果持久化（MATERIALIZING）
    → PNG 校验 → 使用 jobId 稳定键转存平台存储
    → Artifact + Job SUCCEEDED + generation_result_json + Step READY + Dispatch Outbox
    → 原 Blueprint、新 StateSession 恢复 Director
    → Director 总结真实结果，消息照常投影
```

用户的自然语言“同意”不会绕过审批 API。手动入口与聊天入口共用同一套提案、审批、作业与恢复逻辑。Run 等待外部作业期间仍保留事件和业务审计记录，不持有 Agent 推理执行资源。

## OpenAI 接口边界

- 真实模型固定为 `gpt-image-2`，没有替换为其他型号。首版输出一张 PNG、中等质量，支持 `1024x1024`、`1536x1024`、`1024x1536`。
- 使用 `POST /v1/images/generations`。当前适配器选择同步结果方式，独立媒体 worker 等待结果；HTTP 请求不在数据库事务内。
- 此接口返回图片内容，当前接入没有供应商作业查询或远程取消 API。`X-Client-Request-Id` 只作追踪，不当作幂等保证，也不伪造 `providerJobId`。
- 禁止 HTTP 客户端透明重试、自动重定向。接收有界 Base64，不下载供应商任意 URL；校验 PNG 格式、文件大小和像素边界后才转存。
- 前端有运行事件 SSE，图片卡片以持久化快照补偿。OpenAI 卡片显示实际阶段、耗时和错误；供应商没有百分比时 `progress` 为空。

依据：[OpenAI 模型说明](https://developers.openai.com/api/docs/models/gpt-image-2)、[Images API](https://developers.openai.com/api/reference/resources/images/methods/generate)。平台作业恢复语义由下面的状态机约束，不依赖未得到保证的供应商行为。

## 状态与恢复

| 场景 | 恢复方式 |
| --- | --- |
| 审批提交后尚未唤醒 | 每 3 秒扫描到期 Job，唯一 Submission Outbox 保留提交意图 |
| worker 尚未登记网络提交 | `PENDING_SUBMISSION` 可安全领取 |
| OpenAI 提交后超时、5xx、异常响应，或 SUBMITTING 执行权过期 | `UNKNOWN`，禁止自动再次 POST |
| Fake 提交时崩溃 | 平台 jobId 是确定性供应商 ID，允许幂等恢复 |
| 已知成功，尚未转存 | 从持久化 Base64 恢复 `MATERIALIZING`，只重试转存 |
| 转存后、产物事务提交前崩溃 | 稳定对象键覆盖同一文件，Artifact 的 job 唯一键保证只发布一次 |
| 暂时无法查询或保存媒体 | 当前阶段指数退避，不返回提交阶段 |
| 媒体格式错误 | 明确失败，不无限重试无效媒体 |
| 监测期限耗尽 | 外部执行结果不明进入 UNKNOWN；已知成功但转存耗尽则平台失败 |
| 旧 worker 晚到 | owner、epoch、lease 校验拒绝写入 |

`UNKNOWN` 继续占用供应商和用户风险容量。用户必须核对供应商事实，勾选确认并填写说明后，才能调用 `acknowledge-unknown` 结束平台等待。该操作不生成新图片、不确认远程取消、不承诺退款，也不把未知媒体伪装成成功。

`GenerationJobStateMachine` 禁止状态倒退和终态复活。供应商返回成功媒体时只能进入 `MATERIALIZING`，只有平台产物事务可以写 `SUCCEEDED`。

## 并发、事务与取消

- 所有涉及运行的控制命令固定使用 `Run → Job → Capacity` 锁顺序。提交准入持有供应商容量行锁，在 READ_COMMITTED 下统计在途和 UNKNOWN 作业，容量跨实例共享。
- provider-concurrency 按供应商统计，user-concurrency 按用户和供应商统计。排队本身不占在途容量。
- 媒体线程池独立于 Agent 通用业务池，传播 MDC。队列满时拒绝唤醒、保留持久化作业，不在 HTTP 审批线程执行长调用。
- Run 停止事件在同一事务标记所有未终态 Job 的 `cancelRequested`。未提交作业直接取消；已提交作业继续收集结果，取消后不能发布 Artifact 或恢复已停止 Run。
- OpenAI 同步任务的取消是“撤销结果采用权”，供应商可能继续执行并计费。已发布产物不被取消动作删除。
- 作业终态通过条件更新将 WAITING_JOB 恢复一次，冻结 Blueprint 不变，创建新的 StateSession 并重置推理期限。旧状态写入许可不恢复。

## 数据与存储

| 表 / 字段 | 用途 |
| --- | --- |
| `manga_generation_job` | 供应商、目标、冻结请求、状态、执行权、重试、暂存结果和人工核对 |
| `manga_generation_submission_outbox` | 与批准同事务创建的单次提交意图；claim / lease 与 Job 共用 |
| `manga_generation_capacity` | 跨实例供应商准入锁，不用本机 Semaphore 作为全局限制 |
| `manga_generation_callback_inbox` | 已验签事实引用、正文摘要、去重和处理进度 |
| `manga_creative_artifact` | 平台地址、内容摘要、字节数与 Run / Step / ChangeSet / Job / 用户关联 |
| `CreativeStep.generation_result_json` | 原 Blueprint 恢复所需的安全结构化结果 |

Job 和提案只冻结不含凭据的端点配置；执行时重新读取当前用户配置并复核端点，允许正常轮换 API Key。日志、事件和卡片不包含密钥、代理密码、完整 Base64 或原始供应商错误。

产物作为独立结果保存，不自动覆盖镜头现有图片。用户可通过现有镜头编辑采用图片地址；自动采用、专门的产物版本管理不在本次实现范围。转存后被取消或失去执行权的文件不创建 Artifact；此类无引用对象的后台清理仍需在存储治理阶段补充。

## Callback Inbox

当前 OpenAI Images 适配器不接收原生回调。平台中继协议先用于 Fake 验证，并明确区别于 OpenAI Webhook：

```text
POST /api/v1/generation-callbacks/FAKE
X-Manga-Timestamp: Unix 秒
X-Manga-Signature: hex(HMAC-SHA256(secret, timestamp + "\n" + 原始正文))
body: {"eventId":"稳定事件ID","providerJobId":"平台Fake作业ID"}
```

默认关闭，配置至少 32 字符的环境密钥后才接收。请求有长度限制并校验时钟偏移；同供应商、同事件 ID 去重，同 ID 不同正文拒绝。回调可以先于作业标识绑定到达，Inbox 会等待绑定，过期再忽略。

重复、乱序和迟到回调只作为“查询事实”的提示，不直接写成功、不接收图片 URL。消费状态与 Job 唤醒在同一事务持久化；网络查询仍由 Job lease 互斥。新供应商接原生回调前，应单独实现其官方验签和事件解析，不能直接复用中继协议冒充原生支持。

## 入口与配置

1. 从 `GenerationJobService` 看事务边界、claim、取消与恢复。
2. 从 `GenerationJobWorker` 看事务外的供应商 IO 和转存。
3. 从 `GenerationProvider`、`OpenAiImageProvider`、`FakeImageProvider` 看供应商扩展。
4. 从 `ChangeSetApprovalService` 看审批如何从 WAITING_APPROVAL 进入 WAITING_JOB。
5. 从前端 `ImageGenerationPanel`、`GenerationJobsPanel` 看手动推进与刷新恢复。

应用数据库迁移 `V8__generation_jobs.sql`，要求 MySQL 8.0.19+ 的标准 DROP CONSTRAINT 语法；脚本也可在 H2 MySQL 模式检查。见 [MySQL ALTER TABLE](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html)。

真实生成先在用户 AI 配置中保存 OpenAI 端点和凭据，并保留可用的 Director 文本模型配置。`manga.creative.generation.image-prices` 默认空，必须按实际账户核价后配置 `gpt-image-2` 的单张预计费用（USD），真实付费提案才可创建。预计金额是用户知情确认，不是供应商硬性扣费上限。

其他容量、文件大小、超时和回调字段都在 `application.yml` 写有单位、限制与语义注释。Fake 不要求供应商密钥、费用配置或外部网络，仍需正常的 Director 提案流程。

## 验证与后续

本次不运行 Maven、不新增测试用例。已执行前端生产构建、Java 语法解析、Mapper XML / 方法匹配检查，以及独立 H2 内存库的完整迁移、Mapper SQL 预编译和表字段注释检查。SQL 预编译只检查语句结构，不执行业务写入；Java 语法解析不替代类型检查或编译。已有受理测试只调整新增构造依赖。

尚需运行验收：Fake 全链路、双实例竞争、进程中止恢复、取消与结果竞态、签名回调重放、Redis 不可用及 SSE 断线恢复；真实 OpenAI 请求需在配置和费用确认后进行。本次没有发起付费生成调用。

完成 5A 运行验收后，再推进 5B 的视频供应商、原生回调、批量任务、长视频治理和无引用对象清理，不能将当前图片实现视作完整视频支持。
