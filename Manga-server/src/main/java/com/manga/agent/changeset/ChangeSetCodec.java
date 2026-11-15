package com.manga.agent.changeset;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.exception.BusinessException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;
import com.manga.agent.harness.ToolInputValidationException;

/** 提案持久化与严格 Schema 校验，拒绝模型夹带的业务字段。 */
@Component
@RequiredArgsConstructor
public class ChangeSetCodec {
    /** 一次错误最多反馈的字段数，避免损坏候选制造过大的工具上下文。 */
    private static final int MAX_VALIDATION_ISSUES = 20;
    private final ObjectMapper mapper;
    private final Validator validator;

    /** 工具输入校验提供可修正的字段约束，不返回非法值或 Jackson 原始错误正文。 */
    public <T> T validateForTool(JsonNode value, Class<T> type) {
        try {
            if (Objects.isNull(value) || value.isNull()) {
                throw new ToolInputValidationException(List.of(
                        new ToolInputValidationException.FieldIssue("$", "必须提供对象")));
            }
            T result = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(value);
            var violations = validator.validate(result);
            if (!violations.isEmpty()) {
                throw new ToolInputValidationException(violations.stream().limit(MAX_VALIDATION_ISSUES).map(item ->
                        new ToolInputValidationException.FieldIssue(item.getPropertyPath().toString(), item.getMessage())).toList());
            }
            return result;
        } catch (JsonMappingException failure) {
            String path = failure.getPath().stream().map(reference -> Objects.nonNull(reference.getFieldName())
                    ? reference.getFieldName() : "[" + reference.getIndex() + "]").collect(Collectors.joining("."));
            throw new ToolInputValidationException(List.of(new ToolInputValidationException.FieldIssue(
                    path.isBlank() ? "$" : path, "字段类型不匹配、枚举无效或包含未知字段")));
        } catch (IOException failure) {
            throw new ToolInputValidationException(List.of(new ToolInputValidationException.FieldIssue("$", "必须符合声明的 JSON 结构")));
        }
    }

    /** JSON 按已知 Schema 严格解析并执行嵌套 Bean Validation。 */
    public <T> T validate(JsonNode value, Class<T> type) {
        if (Objects.isNull(value) || value.isNull()) throw new BusinessException(ChangeSetResponseCode.INVALID);
        try {
            T result = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(value);
            if (Objects.isNull(result) || !validator.validate(result).isEmpty()) {
                throw new BusinessException(ChangeSetResponseCode.INVALID);
            }
            return result;
        } catch (IOException exception) {
            throw new BusinessException(ChangeSetResponseCode.INVALID);
        }
    }

    /** 将已知聚合或 DTO 转为不可变持久化 JSON。 */
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(ChangeSetResponseCode.CORRUPTED.message(), exception); }
    }

    /** 恢复可信数据库中的类型化结果，损坏时阻止审批写入。 */
    public <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(ChangeSetResponseCode.CORRUPTED.message(), exception); }
    }

    /** 返回前端可预览的结构化内容。 */
    public JsonNode tree(String value) { return read(value, JsonNode.class); }
}
