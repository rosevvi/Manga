package com.manga.agent.execution;

import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 将用户手动选择的视频参数交给现有审批流程。 */
@Component
@RequiredArgsConstructor
public class VideoProposalExecutor implements CreativeStepExecutor {
    private final ChangeSetCodec codec;
    private final ManualMediaProposalFlow flow;

    @Override
    public CreativeStepType stepType() { return CreativeStepType.VIDEO_PROPOSAL; }

    @Override
    public void execute(ClaimedCreativeStep claim) {
        flow.execute(claim, ChangeSetKind.VIDEO_GENERATION,
                () -> codec.tree(codec.read(claim.inputJson(), CreativeStepInput.class).instruction()));
    }
}
