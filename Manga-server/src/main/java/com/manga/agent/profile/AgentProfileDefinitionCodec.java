package com.manga.agent.profile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.manga.agent.capability.CapabilityContractSupport;
import com.manga.agent.capability.CapabilityName;
import com.manga.common.exception.BusinessException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Collections;
import java.util.Objects;

/** 发布定义的严格解析、稳定序列化和指纹计算。 */
@Component
@RequiredArgsConstructor
public class AgentProfileDefinitionCodec {
    private final ObjectMapper mapper;
    private final Validator validator;

    /** 拒绝未知字段、非法枚举、浮点整数和嵌套约束错误。 */
    public AgentProfileDefinition validate(JsonNode node) {
        try {
            AgentProfileDefinition value = mapper.readerFor(AgentProfileDefinition.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(node);
            if (Objects.isNull(value) || !validator.validate(value).isEmpty()) throw new BusinessException(AgentProfileResponseCode.INVALID);
            return new AgentProfileDefinition(value.schemaVersion(), value.role(), value.displayName(), value.description(), value.systemPrompt(),
                    Collections.unmodifiableSet(value.capabilities().isEmpty() ? EnumSet.noneOf(CapabilityName.class)
                            : EnumSet.copyOf(value.capabilities())), value.permissionPolicy(), value.maxTurns(), value.model(), value.budget());
        } catch (IOException | IllegalArgumentException failure) { throw new BusinessException(AgentProfileResponseCode.INVALID); }
    }

    /** 恢复持久化定义并继续执行严格 schema 校验。 */
    public AgentProfileDefinition read(String json) {
        try { return validate(mapper.readTree(json)); }
        catch (JsonProcessingException failure) { throw new IllegalStateException(AgentProfileResponseCode.CORRUPTED.message(), failure); }
    }

    /** 将类型化定义再次校验，避免内部调用绕过边界。 */
    public AgentProfileDefinition normalize(AgentProfileDefinition value) { return validate(mapper.valueToTree(value)); }

    /** 写入规范化版本内容。 */
    public String write(AgentProfileDefinition value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException(AgentProfileResponseCode.CORRUPTED.message(), failure); }
    }

    /** 返回规范化定义的稳定摘要。 */
    public String fingerprint(AgentProfileDefinition value) { return CapabilityContractSupport.sha256(write(value)); }
}
