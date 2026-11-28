package com.manga.agent.execution;

import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCompileRequest;
import com.manga.agent.blueprint.AgentBlueprintCompiler;
import com.manga.agent.collaboration.CollaborationGuard;
import com.manga.agent.collaboration.CreativeRunBudgetService;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.SystemActor;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.state.AgentStateSessionService;
import com.manga.common.exception.BusinessException;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/** 在首次执行时冻结导入任务的模型配置，配置缺失只会使识别任务失败。 */
@Service
@RequiredArgsConstructor
public class ChapterAssetBlueprintPreparation {
    private final AgentBlueprintCompiler compiler;
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeRunBudgetService budgets;
    private final AgentStateSessionService sessions;

    /** 在所属 Run 锁内冻结一次，后续恢复沿用标准 Blueprint 校验。 */
    @Transactional
    public AgentBlueprint freeze(ClaimedCreativeStep claim) {
        var run = runs.lockByRunId(claim.runId());
        var step = steps.findByStepId(claim.stepId()).orElseThrow();
        CollaborationGuard.requireOwner(step, claim.ownerInstanceId(), claim.ownerEpoch(), null);
        try {
            var compiled = compiler.compile(new AgentBlueprintCompileRequest(claim.userId(),
                    AgentProfileKey.CHAPTER_ASSET_EXTRACTOR, claim.permissionPolicy(), null, Map.of(), null));
            steps.freezeBlueprint(claim.stepId(), compiled.snapshotJson(), compiled.blueprint().fingerprint(),
                    claim.stateSessionId(), SystemActor.STEP_EXECUTION.code());
            sessions.register(run, claim.stateSessionId());
            budgets.constrain(claim.runId(), compiled.blueprint());
            return compiled.blueprint();
        } catch (BusinessException failure) {
            throw new AgentRuntimeException(CreativeErrorCode.RUN_CONFIG_UNAVAILABLE, failure.getResponseCode().message(), failure);
        }
    }
}
