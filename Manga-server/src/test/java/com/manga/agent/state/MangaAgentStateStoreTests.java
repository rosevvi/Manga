package com.manga.agent.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.entity.MangaAgentState;
import com.manga.repository.MangaAgentStateRepository;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.ConcurrentSessionModificationException;
import io.agentscope.core.state.VersionedState;
import io.agentscope.core.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 AgentScope 状态序列化、版本围栏和持久层故障封闭行为。
 */
class MangaAgentStateStoreTests {

    private static final String USER_KEY = "42";
    private static final String SESSION_ID = "conversation-17";
    private static final String STATE_KEY = "director";

    @Test
    void shouldCreateInitialVersionWithCompareAndSwap() {
        MangaAgentStateRepository repository = mock(MangaAgentStateRepository.class);
        when(repository.insertInitial(any(MangaAgentState.class))).thenReturn(true);
        MangaAgentStateStore store = new MangaAgentStateStore(repository, new ObjectMapper());

        long version = store.saveIfVersion(USER_KEY, SESSION_ID, STATE_KEY, state("draft"), 0L);

        assertThat(version).isEqualTo(1L);
        ArgumentCaptor<MangaAgentState> row = ArgumentCaptor.forClass(MangaAgentState.class);
        verify(repository).insertInitial(row.capture());
        assertThat(row.getValue().getStateKind()).isEqualTo(AgentStateKind.SINGLE);
        assertThat(row.getValue().getStateType()).isEqualTo(AgentState.class.getName());
        assertThat(row.getValue().getStateJson()).doesNotContain("apiKey");
    }

    @Test
    void shouldRejectMismatchedExpectedVersion() {
        MangaAgentStateRepository repository = mock(MangaAgentStateRepository.class);
        when(repository.updateIfVersion(any(MangaAgentState.class))).thenReturn(false);
        MangaAgentStateStore store = new MangaAgentStateStore(repository, new ObjectMapper());

        assertThatThrownBy(() -> store.saveIfVersion(
                USER_KEY, SESSION_ID, STATE_KEY, state("new"), 3L))
                .isInstanceOf(ConcurrentSessionModificationException.class);
    }

    @Test
    void shouldRestoreVersionedAgentState() {
        MangaAgentStateRepository repository = mock(MangaAgentStateRepository.class);
        AgentState expected = state("restored summary");
        MangaAgentState row = MangaAgentState.builder()
                .userKey(USER_KEY)
                .stateSessionId(SESSION_ID)
                .stateKey(STATE_KEY)
                .stateKind(AgentStateKind.SINGLE)
                .stateType(AgentState.class.getName())
                .stateJson(JsonUtils.getJsonCodec().toJson(expected))
                .stateVersion(5L)
                .build();
        when(repository.find(USER_KEY, SESSION_ID, STATE_KEY)).thenReturn(Optional.of(row));
        MangaAgentStateStore store = new MangaAgentStateStore(repository, new ObjectMapper());

        VersionedState<AgentState> restored = store.getVersioned(
                USER_KEY, SESSION_ID, STATE_KEY, AgentState.class);

        assertThat(restored.version()).isEqualTo(5L);
        assertThat(restored.value().getSummary()).isEqualTo("restored summary");
    }

    @Test
    void shouldFailClosedWhenDatabaseIsUnavailable() {
        MangaAgentStateRepository repository = mock(MangaAgentStateRepository.class);
        when(repository.find(USER_KEY, SESSION_ID, STATE_KEY))
                .thenThrow(new DataAccessResourceFailureException("test database unavailable"));
        MangaAgentStateStore store = new MangaAgentStateStore(repository, new ObjectMapper());

        assertThatThrownBy(() -> store.get(USER_KEY, SESSION_ID, STATE_KEY, AgentState.class))
                .isInstanceOfSatisfying(AgentRuntimeException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(CreativeErrorCode.STATE_STORE_UNAVAILABLE));
    }

    private AgentState state(String summary) {
        return AgentState.builder()
                .userId(USER_KEY)
                .sessionId(SESSION_ID)
                .summary(summary)
                .build();
    }
}
