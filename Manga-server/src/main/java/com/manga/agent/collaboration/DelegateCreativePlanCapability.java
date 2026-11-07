package com.manga.agent.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.capability.CapabilityCommand;
import com.manga.agent.capability.CapabilityDescriptor;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.capability.CapabilityResult;
import com.manga.agent.capability.MangaAgentCapability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 将平台协作服务适配为受 Blueprint 白名单约束的工具。 */
@Component
@RequiredArgsConstructor
public class DelegateCreativePlanCapability implements MangaAgentCapability {
    private final CreativeDelegationService delegation;
    private final ObjectMapper mapper;

    /** 返回与 Worker 冻结快照一致的协议。 */
    @Override
    public CapabilityDescriptor descriptor() {
        return CollaborationToolContract.descriptor(mapper, CapabilityName.DELEGATE_CREATIVE_PLAN);
    }

    /** 在可信上下文内执行，参数中没有用户、Run 或 owner 覆盖入口。 */
    @Override
    public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        return new CapabilityResult(mapper.valueToTree(delegation.delegate(command, context)));
    }
}

