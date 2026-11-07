package com.manga.agent.collaboration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Worker 间传递的不可变结果；候选内容并不代表用户已经批准。 */
public record CreativeStepResult(
        /** 与 Worker 角色匹配的输出类型。 */ @NotNull StepResultKind kind,
        /** 可安全展示并注入上下文的简短摘要。 */ @NotBlank @Size(max = 2000) String content,
        /** SCRIPT_DRAFT 或 STORYBOARD_DRAFT 使用对应 ChangeSet schema；其余类型为空。 */ JsonNode candidate,
        /** REVIEW 必填，其余类型为空。 */ ReviewVerdict verdict,
        /** 评审指出的问题或分析发现。 */ @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> issues,
        /** 已存在且属于当前项目的产物主键。 */ @NotNull @Size(max = 16) List<@NotNull @Positive Long> artifactIds
) { }
