# 阶段 5A：图片生成作业

本阶段实现 Fake Provider 与用户指定的 OpenAI `gpt-image-2`，补齐产物采用和未发布对象回收。图片业务链路已完成隔离验收；真实供应商付费调用未执行。视频、批量生成与长视频治理属于后续 5B。

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
    → PNG 校验 → 登记物理对象 → 使用 jobId + owner epoch 的独立键转存平台存储
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
| 转存后、产物事务提交前崩溃 | 新代次从持久化结果继续转存，不再调用供应商；每个代次独立对象，旧对象登记记录负责回收，Artifact 的 job 唯一键只发布一次 |
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
| `manga_generation_media_object` | IO 前登记的策略、物理命名空间、对象名、发布状态、回收租约和墓碑 |
| `CreativeStep.generation_result_json` | 原 Blueprint 恢复所需的安全结构化结果 |

Job 和提案只冻结不含凭据的端点配置；执行时重新读取当前用户配置并复核端点，允许正常轮换 API Key。日志、事件和卡片不包含密钥、代理密码、完整 Base64 或原始供应商错误。

产物先作为独立结果保存。用户点击“用作镜头图片”时，先从服务端读取当前镜头图片与项目 revision，预览后再确认。确认请求只能提交 revision，镜头、产物地址和采用人由服务端确定。版本冲突返回 409，必须重新预览；相同预览请求重放只返回原采用结果，不再次写入或覆盖后续人工编辑。

采用经 `StoryboardShotService` 只修改图片与 `image_artifact_id`，不覆盖正文、对白或备注；项目/章节 revision、产物采用审计和 `ARTIFACT_ADOPTED` 事件在同一事务提交。手动改换图片地址会解除原产物关联，地址保持不变则保留来源。

## 未发布对象回收

1. 每次物理写入之前，先在 `manga_generation_media_object` 登记引用与代次，崩溃后仍知道需要回收哪个对象。
2. 发布 Artifact 与对象状态 `PUBLISHED` 同事务完成。回收程序同时复核发布状态、Artifact 引用和 Job 有效 lease。
3. 未发布对象经过宽限期后，以 `Run → Job → Object` 锁顺序领取独立回收令牌，将状态改为 `RETIRING`，随后在事务外删除文件。
4. 删除失败保留回收责任并退避；删除成功保留 `DELETED` 墓碑，定期复查极晚到的旧 worker 写入。
5. 每个 owner epoch 使用不同文件名，旧 worker 无法覆盖新代次的已发布产物。磁盘 `.part` 文件也有确定名称，可随登记对象一起回收。

本地和 OSS 均只处理登记引用，不按用户 URL 推导删除路径，不扫描并清空整个目录或 Bucket。策略、命名空间和对象名必须匹配；物理配置变化时会安全拒绝旧位置操作并保留重试责任。本地目录命名空间在目录创建前后保持一致。

V9 前没有登记记录的文件不做猜测性删除；已发布产物也不在自动回收范围内。专门的产物版本管理、已发布产物删除属于后续业务功能。

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
6. 从 `ArtifactApplicationService` 看版本校验、采用去重和领域写入；从 `GenerationMediaCleanupService` 看发布与回收互斥。

应用数据库迁移 V8 与 `V9__artifact_adoption_and_media_cleanup.sql`，要求 MySQL 8.0.19+ 的标准 DROP CONSTRAINT 语法；脚本也可在 H2 MySQL 模式检查。见 [MySQL ALTER TABLE](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html)。本次已通过 `Manga-server/.env` 的实际连接，在 MySQL 8.4.11 顺序执行 V3–V9，并核对 V8、V9 成功及新增对象表字段注释。

真实生成先在用户 AI 配置中保存 OpenAI 端点和凭据，并保留可用的 Director 文本模型配置。按用户确认，`manga.creative.generation.image-prices.gpt-image-2` 已配置为单张预计 **1 USD**。该金额用于知情确认，不是供应商定价声明，也不是扣费硬性上限。移除价格配置会禁止创建真实付费提案。

其他容量、文件大小、超时和回调字段都在 `application.yml` 写有单位、限制与语义注释。Fake 不要求供应商密钥、费用配置或外部网络，仍需正常的 Director 提案流程。

## 验证与后续

本次未运行 Maven，未新增仓库测试用例。使用本机 JDK 21 和已缓存依赖完成后端编译，前端生产构建及 Mapper XML / 方法匹配检查通过。编译时修复了嵌套类型上的校验注解写法与 `LocalDateTime.plusMillis` 调用问题。

独立 H2 隔离演练只替换模型编译和推理边界，业务服务、事务、Mapper、Fake Provider 与本地存储使用实际实现。已验证：

- 审批 → WAITING_JOB → Fake 生成 → 对象登记及转存 → Artifact → 原 Blueprint / 新 StateSession 恢复。
- 重复批准不重复创建 Job；重复采用不重复推进 revision；采用期间项目修改触发冲突。
- 两个独立 owner 的服务实例并发领取只成功一个；非幂等提交租约过期进入 UNKNOWN，旧 worker 结果不复活作业。
- 两个独立 JVM 共用临时文件数据库：第一个 JVM 在转存后、Artifact 提交前强制退出，第二个 JVM 恢复持久化结果，只发布一次并恢复 Step，旧代次对象可回收，没有再次提交供应商生成。
- Run 取消联动待提交 Job，未发布对象可回收，已发布对象受保护，删除墓碑能够清除晚到写入。
- 回调验签及去重，Redis 不可用时业务继续，SSE 按 sequence 重连回放。

这些演练没有调用外部模型或付费图片接口，不代替实际多节点部署、浏览器网络中断及真实供应商联调。跨进程演练中还修复了本地目录创建前后 URI 尾斜杠变化造成的回收命名空间不一致问题。临时编译、数据库、图片和验收脚本统一位于 `.tmp`，完成后清理，且 `.tmp/` 不进入版本控制。

后续 5B 继续视频供应商、原生回调、批量任务和长视频治理；当前图片实现不表示已经具备视频支持。
