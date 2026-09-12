package com.manga.agent;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventTypeTests {

    @Test
    void exposesOnlySafeFirstPhaseEventTypes() {
        assertThat(EnumSet.allOf(AgentEventType.class)).containsExactlyInAnyOrder(
                AgentEventType.RUN_STARTED,
                AgentEventType.CONTENT,
                AgentEventType.TOOL_CALL_STARTED,
                AgentEventType.TOOL_CALL_FINISHED,
                AgentEventType.RUN_COMPLETED,
                AgentEventType.RUN_FAILED,
                AgentEventType.RUN_CANCELLED);
    }
}
