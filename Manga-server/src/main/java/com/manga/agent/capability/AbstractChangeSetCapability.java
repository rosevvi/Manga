package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.changeset.ChangeSetProposalService;
import com.manga.agent.domain.AgentPermissionPolicy;

/** 提案工具共有适配；工具本身没有批准或业务应用能力。 */
abstract class AbstractChangeSetCapability implements MangaAgentCapability {
    private final ChangeSetProposalService service;
    private final ObjectMapper mapper;
    private final ChangeSetKind kind;
    private final CapabilityDescriptor descriptor;

    /** 固定提案业务类型及模型可见 Schema。 */
    protected AbstractChangeSetCapability(ChangeSetProposalService service, ObjectMapper mapper, ChangeSetKind kind,
            CapabilityName name, String description, String schema) {
        this.service = service;
        this.mapper = mapper;
        this.kind = kind;
        CapabilityDescriptor base = CapabilityContractSupport.readOnlyDescriptor(mapper, name, description, schema, 1);
        descriptor = new CapabilityDescriptor(name, description, base.inputSchema(), base.schemaSha256(),
                false, false, AgentPermissionPolicy.PROPOSE_CHANGES, 1);
    }

    /** 返回需提案权限且不可并发的工具契约。 */
    @Override
    public CapabilityDescriptor descriptor() { return descriptor; }

    /** 仅保存草稿引用，执行器负责关闭模型调用后开放审批。 */
    @Override
    public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        return new CapabilityResult(mapper.valueToTree(service.propose(kind, command.input(), context)));
    }
}
