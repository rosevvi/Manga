package com.manga.agent.execution;

import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.agent.domain.CreativeStepInput;
import com.manga.agent.domain.CreativeStepType;
import com.manga.service.ProjectAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 从已保存的图片项读取参数，生成可审批的图片提案。 */
@Component
@RequiredArgsConstructor
public class AssetImageProposalExecutor implements CreativeStepExecutor {
    private final ProjectAssetService assets;
    private final ChangeSetCodec codec;
    private final ManualMediaProposalFlow flow;

    @Override
    public CreativeStepType stepType() { return CreativeStepType.ASSET_IMAGE_PROPOSAL; }

    @Override
    public void execute(ClaimedCreativeStep claim) {
        flow.execute(claim, ChangeSetKind.IMAGE_GENERATION, () -> {
            var target = codec.read(claim.inputJson(), CreativeStepInput.class).target();
            return codec.tree(codec.write(assets.generationSettings(claim.projectId(), target.resourceId(), claim.userId())));
        });
    }
}
