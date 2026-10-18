package com.manga.agent.capability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.manga.agent.domain.AgentPermissionPolicy;
import com.manga.agent.domain.CreativeRuntimeMessages;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 构建规范化 Capability Schema 与输入校验结果。
 */
public final class CapabilityContractSupport {

    public static final String CHAPTER_ID_FIELD = "chapterId";

    private CapabilityContractSupport() {
    }

    /** 从 JSON Schema 构建只读能力描述并计算稳定指纹。 */
    public static CapabilityDescriptor readOnlyDescriptor(
            ObjectMapper objectMapper,
            CapabilityName name,
            String description,
            String schemaJson,
            long implementationVersion) {
        try {
            JsonNode schema = objectMapper.readTree(schemaJson);
            ObjectMapper canonicalMapper = objectMapper.copy()
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            String canonicalJson = canonicalMapper.writeValueAsString(schema);
            return new CapabilityDescriptor(
                    name,
                    description,
                    schema,
                    sha256(canonicalJson),
                    true,
                    true,
                    AgentPermissionPolicy.READ_ONLY,
                    implementationVersion
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    CreativeRuntimeMessages.CAPABILITY_RESULT_SERIALIZATION_FAILED, exception);
        }
    }

    /** 读取必填的正整数型输入字段。 */
    public static long requirePositiveLong(CapabilityCommand command, String fieldName) {
        JsonNode input = Objects.isNull(command) ? null : command.input();
        JsonNode value = Objects.isNull(input) ? null : input.get(fieldName);
        if (Objects.isNull(value) || value.isNull()) {
            throw new IllegalArgumentException(
                    CreativeRuntimeMessages.CAPABILITY_INPUT_REQUIRED_TEMPLATE.formatted(fieldName));
        }
        if (!value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException(
                    CreativeRuntimeMessages.CAPABILITY_INPUT_INVALID_TEMPLATE.formatted(fieldName));
        }
        return value.longValue();
    }

    /** 读取可选的正整数型输入字段。 */
    public static Long optionalPositiveLong(CapabilityCommand command, String fieldName) {
        JsonNode input = Objects.isNull(command) ? null : command.input();
        JsonNode value = Objects.isNull(input) ? null : input.get(fieldName);
        if (Objects.isNull(value) || value.isNull()) {
            return null;
        }
        if (!value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException(
                    CreativeRuntimeMessages.CAPABILITY_INPUT_INVALID_TEMPLATE.formatted(fieldName));
        }
        return value.longValue();
    }

    /** 返回十六进制 SHA-256 摘要。 */
    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(CreativeRuntimeMessages.SHA_256_UNAVAILABLE, exception);
        }
    }
}
