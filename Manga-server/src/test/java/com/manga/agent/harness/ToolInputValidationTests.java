package com.manga.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.collaboration.CreativeStepResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 保护模型可修正的字段约束，确保错误正文不会泄露被拒绝的内容。 */
class ToolInputValidationTests {
    @Test
    void invalidSummaryReportsFieldAndConstraintWithoutRejectedValue() throws Exception {
        var mapper = new ObjectMapper();
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var codec = new ChangeSetCodec(mapper, validation.getValidator());
            var input = (ObjectNode) mapper.readTree("{\"kind\":\"ANALYSIS\",\"content\":\"\",\"candidate\":null,\"verdict\":null,\"issues\":[],\"artifactIds\":[]}");
            assertThatThrownBy(() -> codec.validateForTool(input, CreativeStepResult.class))
                    .isInstanceOfSatisfying(ToolInputValidationException.class, error ->
                            assertThat(error.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("content")));
            input.put("content", "not-for-public-display"); input.put("unexpected", "private-input-marker");
            assertThatThrownBy(() -> codec.validateForTool(input, CreativeStepResult.class))
                    .isInstanceOfSatisfying(ToolInputValidationException.class, error -> {
                        assertThat(error.getMessage()).doesNotContain("private-input-marker", "not-for-public-display");
                        assertThat(error.issues()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("unexpected"));
                    });
        }
    }
}
