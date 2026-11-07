package com.manga.agent.profile;

import com.manga.agent.application.CreativeRunStartResult;
import com.manga.agent.application.RunAdmissionService;
import com.manga.agent.application.StartCreativeRunCommand;
import com.manga.agent.blueprint.AgentBlueprintCompileRequest;
import com.manga.agent.blueprint.AgentBlueprintCompiler;
import com.manga.agent.blueprint.CompiledAgentBlueprint;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeRunTriggerSource;
import com.manga.common.exception.BusinessException;
import com.manga.repository.AgentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 管理员可以先无模型调用预览，再显式启动受预算约束的只读测试 Run。 */
@Service
@RequiredArgsConstructor
public class AgentProfileTestService {
    private final AgentProfileRepository profiles;
    private final AgentProfileDefinitionCodec codec;
    private final AgentProfileDefinitionValidator validator;
    private final AgentBlueprintCompiler compiler;
    private final AgentProfileManagementProperties properties;
    private final RunAdmissionService admission;

    /** 读取精确版本并编译，不获取 Harness 或连接供应商。 */
    @Transactional
    public AgentProfileApi.Preview preview(String key, long version, AgentProfileApi.Test request, long userId) {
        var blueprint = prepare(key, version, request, userId).blueprint();
        return new AgentProfileApi.Preview(key, version, blueprint.fingerprint(), blueprint.capabilities().stream()
                .map(value -> value.name()).collect(Collectors.toUnmodifiableSet()), blueprint.model().modelName(),
                blueprint.maxTurns(), blueprint.executionPolicy().budget());
    }

    /** 用户明确点击测试才启动模型调用；测试只读，使用当前管理员可访问的项目和模型。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreativeRunStartResult start(String key, long version, AgentProfileApi.Test request, long userId) {
        var selection = new AgentProfileSelection(key, version, request.modelConfigId(), true, request.definitionFingerprint());
        var command = new StartCreativeRunCommand(userId, request.requestId(), CreativeRunTriggerSource.CONVERSATION,
                CreativeOperationType.ASSISTANT_CHAT, null, request.projectId(), null, AgentPermissionPolicy.READ_ONLY,
                request.instruction(), selection);
        var existing = admission.replayIfAdmitted(command);
        if (existing.isPresent()) return existing.get();
        var blueprint = prepare(key, version, request, userId);
        return admission.admitSandbox(command, blueprint);
    }

    /** 和编辑、禁用共用身份锁，确保测试的内容、状态和用户看到的指纹一致。 */
    private CompiledAgentBlueprint prepare(String key, long number, AgentProfileApi.Test request, long userId) {
        var profile = profiles.lock(key); var version = profiles.version(key, number);
        if (Objects.isNull(profile) || !profile.getEnabled() || Objects.isNull(version)) {
            throw new BusinessException(AgentProfileResponseCode.UNAVAILABLE, HttpStatus.CONFLICT);
        }
        if (!version.getDefinitionFingerprint().equals(request.definitionFingerprint())) {
            throw new BusinessException(AgentProfileResponseCode.CONFLICT, HttpStatus.CONFLICT);
        }
        var definition = validator.validate(key, profile.getBuiltIn(), codec.read(version.getDefinitionJson()));
        if (!codec.fingerprint(definition).equals(version.getDefinitionFingerprint())) {
            throw new IllegalStateException(AgentProfileResponseCode.CORRUPTED.message());
        }
        return compiler.compileSandbox(definition.profile(key, number), new AgentBlueprintCompileRequest(userId,
                definition.role(), AgentPermissionPolicy.READ_ONLY, request.modelConfigId(), Map.of(), key), properties.testMaxTurns());
    }
}
