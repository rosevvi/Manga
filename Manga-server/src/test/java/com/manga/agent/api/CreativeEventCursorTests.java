package com.manga.agent.api;

import com.manga.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Creative Run SSE 游标的查询参数优先级和断线重连格式。
 */
class CreativeEventCursorTests {

    private static final String RUN_ID = "run-test";

    @Test
    void shouldPreferAfterSequenceQueryParameter() {
        assertThat(CreativeEventCursor.resolve(RUN_ID, 12L, RUN_ID + ":8")).isEqualTo(12L);
    }

    @Test
    void shouldResumeFromLastEventId() {
        assertThat(CreativeEventCursor.resolve(RUN_ID, null, RUN_ID + ":42")).isEqualTo(42L);
    }

    @Test
    void shouldStartFromZeroWithoutCursor() {
        assertThat(CreativeEventCursor.resolve(RUN_ID, null, null)).isZero();
    }

    @Test
    void shouldRejectCursorFromAnotherRun() {
        assertThatThrownBy(() -> CreativeEventCursor.resolve(RUN_ID, null, "other-run:42"))
                .isInstanceOf(BusinessException.class);
    }
}
