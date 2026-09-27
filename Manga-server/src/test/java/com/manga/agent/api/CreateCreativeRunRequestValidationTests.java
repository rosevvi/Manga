package com.manga.agent.api;

import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeOperationType;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.domain.CreativeRunTriggerSource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证会话和手动创建 Run 的 Bean Validation 与跨字段约束。
 */
class CreateCreativeRunRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptConversationRequest() {
        CreateCreativeRunRequest request = new CreateCreativeRunRequest(
                "request-chat", CreativeRunTriggerSource.CONVERSATION, CreativeOperationType.ASSISTANT_CHAT,
                null, 101L, null, AgentPermissionPolicy.READ_ONLY, "分析项目节奏");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldAcceptManualChapterStoryboardRequest() {
        CreateCreativeRunRequest request = new CreateCreativeRunRequest(
                "request-storyboard", CreativeRunTriggerSource.MANUAL,
                CreativeOperationType.GENERATE_CHAPTER_STORYBOARD, null, 101L,
                new CreativeTargetRequest(CreativeResourceType.CHAPTER, 202L),
                AgentPermissionPolicy.CONFIRM_SIDE_EFFECTS, "为本章生成分镜");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldRejectManualRequestWithoutTarget() {
        CreateCreativeRunRequest request = new CreateCreativeRunRequest(
                "request-storyboard", CreativeRunTriggerSource.MANUAL,
                CreativeOperationType.GENERATE_CHAPTER_STORYBOARD, null, 101L, null,
                AgentPermissionPolicy.CONFIRM_SIDE_EFFECTS, "为本章生成分镜");

        Set<ConstraintViolation<CreateCreativeRunRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("scopeValid");
    }

    @Test
    void shouldRejectConversationRequestWithManualTarget() {
        CreateCreativeRunRequest request = new CreateCreativeRunRequest(
                "request-chat", CreativeRunTriggerSource.CONVERSATION, CreativeOperationType.ASSISTANT_CHAT,
                null, 101L, new CreativeTargetRequest(CreativeResourceType.CHAPTER, 202L),
                AgentPermissionPolicy.READ_ONLY, "分析项目节奏");

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
