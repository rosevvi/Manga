package com.manga.agent.harness;

import com.manga.common.exception.BusinessException;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.capability.AgentRuntimeContext;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.service.ProjectRevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** 工具输入规范化、版本化缓存键和安全错误转换，不缓存写入工具。 */
@Component
@RequiredArgsConstructor
public class ToolExecutionSupport {
    private final ObjectMapper mapper;
    private final ProjectRevisionService revisions;
    /** 规范化 JSON 对象字段顺序，避免仅参数顺序变化产生不同指纹。 */
    public String fingerprint(CapabilityName name, JsonNode input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    mapper.writeValueAsBytes(new InputMaterial(name, canonical(input)))));
        } catch (NoSuchAlgorithmException | IOException failure) {
            throw new IllegalStateException(CreativeRuntimeMessages.EVENT_PAYLOAD_CORRUPTED, failure);
        }
    }
    /** 仅授权不变的结果读取和带项目版本的领域读取进入缓存。 */
    public String cacheKey(CapabilityName name, String fingerprint, AgentRuntimeContext context) {
        if (name == CapabilityName.READ_DELEGATED_RESULT) { return fingerprint; }
        if (!CapabilityName.phaseTwoReadOnlyCapabilities().contains(name) || Objects.isNull(context.projectId())) { return null; }
        return fingerprint + ":" + revisions.lock(context.projectId(), context.userId()).getRevision();
    }
    /** 时间线与模型只获得领域错误说明，不返回 SQL 或框架异常。 */
    public String safeFailure(RuntimeException failure) {
        if (failure instanceof ToolInputValidationException invalid) {
            return ToolInputValidationException.MESSAGE + "：" + invalid.issues().stream()
                    .map(issue -> issue.path() + " " + issue.constraint()).limit(8).collect(Collectors.joining("；"));
        }
        if (failure instanceof AgentRuntimeException || failure instanceof BusinessException) {
            return failure.getMessage();
        }
        return CreativeRuntimeMessages.AGENT_STEP_EXECUTION_FAILED;
    }
    /** 递归排序对象，数组顺序和标量类型保持不变。 */
    private Object canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), canonical(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            return StreamSupport.stream(node.spliterator(), false).map(this::canonical).toList();
        }
        return node;
    }
    /** 指纹材料的类型化字段，避免散落字符串键。 */
    private record InputMaterial(/** 稳定能力标识。 */ CapabilityName capability, /** 规范化输入。 */ Object input) { }
}
