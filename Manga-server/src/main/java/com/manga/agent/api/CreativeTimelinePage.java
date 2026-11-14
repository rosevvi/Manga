package com.manga.agent.api;

import java.time.LocalDateTime;
import java.util.List;

/** 按事件序号分页的安全执行事实，可重复读取和从断点恢复。 */
public record CreativeTimelinePage(
        /** 当前页执行事实。 */ List<Item> items,
        /** 当前页已扫描至的事件序号，下一页从此序号之后读取。 */ long throughSequence,
        /** 查询快照中的日志最高水位。 */ long recoveryWatermark,
        /** 是否还有下一页。 */ boolean hasMore
) {
    /** 版本化公开时间线，不返回 Blueprint 提示词、凭据或原始供应商响应。 */
    public record Item(
            /** 不随重放变化的事件标识。 */ String id,
            /** 事实序号。 */ long sequence,
            /** 公开条目版本。 */ int payloadVersion,
            /** 事件类型。 */ String type,
            /** 所属步骤，可为空。 */ String stepId,
            /** 父步骤，可为空。 */ String parentStepId,
            /** 原始执行代次，旧事件可为空。 */ Long ownerEpoch,
            /** 固化 Agent 身份。 */ String agentName,
            /** 固化 Agent 版本。 */ Long agentVersion,
            /** 工具调用关联标识。 */ String actionId,
            /** 动作名称。 */ String actionName,
            /** 本条事实状态。 */ String status,
            /** 事实记录时间。 */ LocalDateTime occurredAt,
            /** 动作开始时间。 */ LocalDateTime startedAt,
            /** 动作结束时间。 */ LocalDateTime finishedAt,
            /** 安全输入摘要。 */ String inputSummary,
            /** 安全结果摘要或助手公开正文增量。 */ String resultSummary,
            /** 可供界面定位的产物标识。 */ List<String> resourceIds,
            /** 定位项目工作台的安全链接，不包含长期供应商地址。 */ List<ResourceLink> resourceLinks,
            /** 脱敏失败原因。 */ String failureReason
    ) { }
    /** 导航链接仅由平台根据归属项目构造。 */
    public record ResourceLink(/** 链接标题。 */ String label, /** 应用内相对路径。 */ String href) { }
}
