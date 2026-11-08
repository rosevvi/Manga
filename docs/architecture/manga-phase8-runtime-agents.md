# 阶段 8：受限运行时临时 Agent

Director 可在持久化委派计划中声明临时只读 Worker。临时定义不进入管理员发布目录，不运行自定义代码，不使用 SDK 的动态 SubAgent 功能，也不开放 Worker 递归委派。

## 请求与执行链路

`delegate_creative_plan` 的每个节点使用 `profile` 或 `dynamicAgent`，二者严格互斥。例如：

```json
{
  "title": "人物动机专项检查",
  "nodes": [
    {
      "key": "motivation",
      "dynamicAgent": {
        "name": "人物动机专家",
        "reason": "当前章节的人物转变需要专项分析",
        "role": "script_parser",
        "systemPrompt": "依据项目事实检查人物行动与动机是否一致，区分证据和推测。",
        "capabilities": ["get_project_overview", "get_chapter_content"],
        "maxTurns": 4
      },
      "instruction": "分析目标章节的人物转变",
      "dependsOn": []
    },
    {
      "key": "review",
      "profile": "continuity_reviewer",
      "instruction": "复核前置专项分析",
      "dependsOn": ["motivation"]
    }
  ]
}
```

链路为：严格 DTO 校验 → owner/lease/deadline 校验 → 临时 Prompt 筛查 → 继承父模型、求能力交集 → 检查 Run 额度 → 冻结整个计划 → 关闭 Director 执行资源 → 物化独立 Step/StateSession → 按依赖调度 → 提交结构化结果 → ALL_SUCCESS 汇合恢复 Director。

新增的 `DynamicAgentRequest` 只接受名称、创建原因、固定 Worker 角色、Prompt、业务读取白名单和轮数。模型配置、连接地址、权限策略、类名和工具代码均不是合法输入字段。

## 权限与 Prompt 审计

- 角色只能是分析、编剧、分镜或连续性评审，禁止 Director。
- 敏感能力直接拒绝；业务读取能力取请求与父 Blueprint 白名单的交集。
- Worker 的结果读取和提交工具由平台添加，并保留同 Run、传递依赖、项目归属与 owner epoch 检查。
- 模型继承父快照，不能指定新供应商、URL 或密钥。
- Prompt 与创建原因筛查明确的绕权、命令执行、密钥和任意连接指令。筛查是保守启发式，不能证明自然语言绝对安全；真正的权限边界是工具白名单、固定角色与关闭的 Shell/Web/文件/动态 SubAgent 能力。
- 临时 Worker 没有委派工具，委派服务也拒绝任何子步骤继续委派，因此不能通过改写 Prompt 递归扩张。

## 冻结预算

沿用 `manga_creative_run.budget_json` 保存 `DynamicAgentRunBudget`，无需新建数据库表。新 Run 受理时冻结配置，修改部署默认值不会重置已有运行。

`manga.creative.dynamic-agent` 默认边界：

| 项目 | 默认值 |
| --- | --- |
| 临时节点总数，含修订 | 6 |
| Run 内不同临时 KernelKey | 4 |
| 临时推理轮数累计预留 | 64 |
| 单次最大推理轮数 | 8，同时受父规格限制 |
| 临时 Prompt 累计字符数 | 18000，单份最多 6000 |
| 规划费用上限 | 1 USD |
| 每轮规划估值 | 必须由运维配置，默认空 |

启用临时创建前设置 `MANGA_DYNAMIC_AGENT_ESTIMATED_USD_PER_TURN`，值表示每轮的规划费用估值，单位 USD。它不是供应商单价，也不是实际 token 结算；请按自己的模型成本策略配置。缺失估值时只拒绝临时声明，已发布专业 Agent 仍可使用。

本地 `.env` 和 `.env.example` 暂设为 **0.05 USD/轮**，部署配置本身仍保留缺值拒绝创建的规则。估算参考 [OpenAI 官方 GPT-5.4 定价](https://developers.openai.com/api/docs/models/gpt-5.4)：标准输入 2.50 USD/百万 token、输出 15 USD/百万 token。假设每轮 8000 输入和 1000 输出（包括计费的推理 token），不计缓存优惠，费用为 `8000 × 2.50 / 1000000 + 1000 × 15 / 1000000 = 0.035 USD`；向上取 0.05 作为初始规划值。此参考不表示当前 Run 一定选择 GPT-5.4；临时模型仍继承父快照。更贵模型、更长上下文或更多推理 token 可能超出估值，后续应以实际用量校准。

保持每 Run 规划上限 1 USD 不变，按 0.05 可预留最多 20 轮。配置 `maxTurns=8` 的临时 Agent 按 9 轮预留，即 0.45 USD；两个此类节点预留 0.90 USD，三个会被拒绝。数量上限 6 与费用上限同时生效，不保证每个 Run 能以最大轮数创建 6 个节点。该估值不包含图片/视频生成费，也不包含 Director 和已发布 Worker 的调用费用。

修改 `.env` 后重启后端，且只对新建 Run 生效；已有 Run 保留受理时冻结的估值，原先缺少估值的 Run 不会被自动补价。

计划接受前计算所有已保存计划的临时数量、不同 Kernel、Prompt 总量和最大推理轮数；DRAFT、已完成、失败及修订记录均计入，不返还创建额度。最大轮数乘冻结估值不得超规划费用上限。

执行前再次在 Run 锁内扣减额度，按 `maxTurns + 1` 保守预留。同一 owner epoch 重复扣减幂等，失败不退款。临时 Worker 只允许一次模型执行，已经提交的结果可恢复完成而无需再次调用模型。

平台 Run 总步骤数、深度、并发数、修订及模型执行预算继续生效。全局 Kernel 缓存仍使用有界 LRU 和活跃 Handle 保护，单 Run 的 Kernel 限额不能突破进程缓存容量。

规划费用是估算限额，不能保证供应商实际账单不超过该数；实际 token 费用核算属于阶段 9 的用量账本。

## 恢复与事件

完整原始声明和临时 Blueprint 固化在 `FrozenDelegation`，物化后 Blueprint 也存入 Step。恢复不重新生成 Prompt，不查管理员配置，不使用新的费率或默认模型。

`DYNAMIC_AGENT_CREATED` 记录创建原因、原始纯数据配置、输入、有效 manifest、实际轮数和 Blueprint 指纹，关联 Run、Step 与计划。完整模型快照可由指纹关联持久化规格。

`DYNAMIC_AGENT_RESULT` 在围栏完成事务中记录经 schema 校验的结果。重复完成不会重复发布结果事件。普通工具错误仍通过原工具状态事件呈现，拒绝的声明不会成为可执行计划。

协作图以冻结名称展示临时节点并标记“只读、禁止继续委派”。这些审计事件不拼接进用户聊天正文。

精确截止时间新增独立的 Manga 调度器信号，达到截止时间即取消模型流并 eager cleanup，不等待下一次租约心跳。同步资源获取仍受既有数据库和 Kernel 获取超时约束。

## 验证与范围

使用本机 JDK 编译主代码及已有测试，没有运行 Maven，没有新增仓库测试。隔离库验证严格输入、越权角色、敏感能力、Prompt 绕权、无估值、数量/不同 Kernel/Prompt/费用/深度边界、幂等、结果审计和取消；另外对 100 个不同 KernelKey 验证 LRU 资源关闭，并验证截止信号取消与资源释放。模型调用采用替身，不产生付费请求。

隔离验证共通过 127 次断言，其中包含 100 次不同 KernelKey 的资源获取检查，并非 127 个独立用例。已有 Harness、Kernel 池、执行器、Blueprint 与枚举契约相关的 12 个用例通过；前端构建通过。真实供应商工具选择和临时专业 Prompt 的内容质量没有在本次验证中验收。

阅读入口：`DynamicAgentRequest` → `DynamicAgentBlueprintFactory` → `CreativeDelegationService` → `CreativeRunBudgetService` → `CreativeGraphService` → `MangaAgentInvoker`。
