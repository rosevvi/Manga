package com.manga.agent.api;

import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 解析 Creative Run SSE 查询参数和 Last-Event-ID 断点游标。
 */
public final class CreativeEventCursor {

    /** 禁止实例化无状态游标解析器。 */
    private CreativeEventCursor() {
    }

    /** 查询参数优先；缺省时解析符合 runId:sequence 格式的 Last-Event-ID。 */
    public static long resolve(String runId, Long afterSequence, String lastEventId) {
        if (Objects.nonNull(afterSequence)) {
            return Math.max(0L, afterSequence);
        }
        if (!StringUtils.hasText(lastEventId)) {
            return 0L;
        }
        String expectedPrefix = runId + CreativeApiConstants.EVENT_ID_SEPARATOR;
        if (!lastEventId.startsWith(expectedPrefix)) {
            throw invalidCursor();
        }
        try {
            return Math.max(0L, Long.parseLong(lastEventId.substring(expectedPrefix.length())));
        } catch (NumberFormatException exception) {
            throw invalidCursor();
        }
    }

    /** 返回不会暴露内部解析细节的统一游标校验异常。 */
    private static BusinessException invalidCursor() {
        return new BusinessException(CommonResponseCode.VALIDATION_ERROR,
                CreativeRuntimeMessages.INVALID_EVENT_CURSOR, HttpStatus.BAD_REQUEST);
    }
}
