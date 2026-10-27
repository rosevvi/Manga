package com.manga.agent.changeset;

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

/** 提案持久化与严格 Schema 校验，拒绝模型夹带的业务字段。 */
@Component
@RequiredArgsConstructor
public class ChangeSetCodec {
    private final ObjectMapper mapper;
    private final Validator validator;

    /** JSON 按已知 Schema 严格解析并执行嵌套 Bean Validation。 */
    public <T> T validate(JsonNode value, Class<T> type) {
        if (Objects.isNull(value) || value.isNull()) throw new BusinessException(ChangeSetResponseCode.INVALID);
        try {
            T result = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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
