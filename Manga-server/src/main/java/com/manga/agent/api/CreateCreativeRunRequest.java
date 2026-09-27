package com.manga.agent.api;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.agent.domain.CreativeRuntimeMessages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 定义会话入口和页面手动入口共用的 Creative Run 创建契约。
 */
public record CreateCreativeRunRequest(
        /** 客户端生成的启动幂等标识；同一用户内唯一。 */
        @NotBlank @Size(max = 64) String requestId,
        /** 请求来自助手会话还是页面上的手动操作。 */
        @NotNull CreativeRunTriggerSource triggerSource,
        /** 本次 Run 需要完成的稳定业务操作。 */
        @NotNull CreativeOperationType operationType,
        /** 可选的既有会话标识；手动操作必须为空。 */
        @Size(max = 36) String conversationId,
        /** 可选项目主键；手动操作必须提供。 */
        @Positive Long projectId,
        /** 手动操作的目标资源；会话操作必须为空。 */
        @Valid CreativeTargetRequest target,
        /** 本次 Run 允许 Agent 使用的最高业务权限。 */
        @NotNull AgentPermissionPolicy permissionPolicy,
        /** 用户自然语言指令或手动操作的补充说明。 */
        @NotBlank @Size(max = 12000) String instruction
) {

    /** 校验会话入口与手动入口不能混用的字段组合。 */
    @AssertTrue(message = CreativeRuntimeMessages.REQUEST_SCOPE_INVALID)
    public boolean isScopeValid() {
        if (Objects.isNull(triggerSource) || Objects.isNull(operationType)) {
            return true;
        }
        if (triggerSource == CreativeRunTriggerSource.CONVERSATION) {
            return operationType == CreativeOperationType.ASSISTANT_CHAT && Objects.isNull(target);
        }
        return operationType != CreativeOperationType.ASSISTANT_CHAT
                && !StringUtils.hasText(conversationId)
                && Objects.nonNull(projectId)
                && Objects.nonNull(target);
    }
}
