package com.manga.agent.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.blueprint.AgentBlueprintFingerprintMaterial;
import com.manga.agent.blueprint.AgentKernelKey;
import com.manga.agent.blueprint.CapabilityManifest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.profile.AgentExecutionPolicy;
import com.manga.agent.profile.AgentModelPolicy;
import com.manga.agent.profile.AgentModelSelection;
import com.manga.agent.profile.AgentBudgetLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.regex.Pattern;

/** 临时规格只能继承模型、缩小能力和预算；不进入管理员发布目录。 */
@Component
@RequiredArgsConstructor
public class DynamicAgentBlueprintFactory {
    /** 与合法数据库身份互斥的临时身份前缀。 */
    public static final String ID_PREFIX = "runtime-";
    /** 拒绝明确要求绕过平台或连接任意执行环境的提示词；权限隔离不依赖文本检测。 */
    private static final Pattern UNSAFE = Pattern.compile(
            "(?is)(https?://|runtime\\.exec|processbuilder|powershell|cmd\\.exe|curl\\s|execute\\s+shell|忽略.{0,12}(权限|系统|规则)|绕过.{0,12}(审批|权限)|ignore.{0,30}(permission|system|previous)|api[_ ]?key|执行.{0,10}(命令|脚本))");
    private final AgentBlueprintCodec codec;
    private final ObjectMapper mapper;

    /** 对业务原因与 Prompt 做审计筛查，冻结父模型和有效白名单。 */
    public AgentBlueprint freeze(DynamicAgentRequest request, AgentBlueprint parent, DynamicAgentRunBudget budget) {
        if (request.role() == AgentProfileKey.MANGA_DIRECTOR || !CapabilityName.phaseTwoReadOnlyCapabilities().containsAll(request.capabilities())
                || UNSAFE.matcher(request.systemPrompt() + "\n" + request.reason()).find()) {
            throw new AgentRuntimeException(CreativeErrorCode.PERMISSION_DENIED, DynamicAgentMessages.REJECTED);
        }
        if (Objects.isNull(budget) || Objects.isNull(budget.estimatedUsdPerTurn())) {
            throw new AgentRuntimeException(CreativeErrorCode.RUN_CONFIG_UNAVAILABLE, DynamicAgentMessages.PRICE_REQUIRED);
        }
        int turns = Math.min(request.maxTurns(), Math.min(parent.maxTurns(), budget.maxTurns()));
        String id = ID_PREFIX + CapabilityContractSupport.sha256(write(request)).substring(0, 24);
        var capabilities = new ArrayList<>(parent.capabilities().stream()
                .filter(cap -> request.capabilities().contains(cap.name()) && cap.requiredPermission() == AgentPermissionPolicy.READ_ONLY).toList());
        for (var name : List.of(CapabilityName.READ_DELEGATED_RESULT, CapabilityName.SUBMIT_CREATIVE_STEP_RESULT)) {
            var tool = CollaborationToolContract.descriptor(mapper, name);
            capabilities.add(new CapabilityManifest(name, tool.schemaSha256(), tool.readOnly(), tool.concurrencySafe(), tool.requiredPermission(), tool.implementationVersion()));
        }
        capabilities.sort(Comparator.comparing(cap -> cap.name().code()));
        var policy = new AgentExecutionPolicy(id, request.name(),
                new AgentModelPolicy(AgentModelSelection.INHERIT_PARENT, Set.of(parent.model().providerType()), Set.of(parent.model().modelName())),
                new AgentBudgetLimits(1, 1, 1, 0, 1, turns + 1), false);
        String prompt = request.systemPrompt() + "\n" + CollaborationMessages.WORKER_TEMPLATE.formatted(request.name(), switch (request.role()) {
            case SCRIPT_PARSER -> StepResultKind.ANALYSIS;
            case SCRIPT_WRITER -> StepResultKind.SCRIPT_DRAFT;
            case STORYBOARD_DIRECTOR -> StepResultKind.STORYBOARD_DRAFT;
            case CONTINUITY_REVIEWER -> StepResultKind.REVIEW;
            default -> throw new AgentRuntimeException(CreativeErrorCode.PERMISSION_DENIED, DynamicAgentMessages.REJECTED);
        });
        var material = new AgentBlueprintFingerprintMaterial(request.role(), 1, parent.model(), prompt, parent.stableVariables(), capabilities,
                AgentPermissionPolicy.READ_ONLY, turns, policy);
        String fingerprint = codec.fingerprint(material);
        return new AgentBlueprint(new AgentKernelKey(fingerprint), request.role(), 1, parent.model(), prompt, parent.stableVariables(), capabilities,
                AgentPermissionPolicy.READ_ONLY, turns, fingerprint, policy);
    }

    /** 稳定集合顺序用于临时身份，避免同一规格因 Set 顺序制造额外 Kernel。 */
    private String write(DynamicAgentRequest request) {
        var normalized = new DynamicAgentRequest(request.name(), request.reason(), request.role(), request.systemPrompt(),
                request.capabilities().isEmpty() ? EnumSet.noneOf(CapabilityName.class) : EnumSet.copyOf(request.capabilities()), request.maxTurns());
        try { return mapper.writeValueAsString(normalized); }
        catch (JsonProcessingException failure) { throw new IllegalStateException(DynamicAgentMessages.REJECTED, failure); }
    }
}
