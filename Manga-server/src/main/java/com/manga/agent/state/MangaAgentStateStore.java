package com.manga.agent.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.domain.CreativeActor;
import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.entity.MangaAgentState;
import com.manga.repository.MangaAgentStateRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.ConcurrentSessionModificationException;
import io.agentscope.core.state.State;
import io.agentscope.core.state.VersionedState;
import io.agentscope.core.util.JsonCodec;
import io.agentscope.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 将 AgentScope 组件状态持久化到 Manga 数据库，并提供乐观版本围栏。
 */
@Component
@RequiredArgsConstructor
public class MangaAgentStateStore implements AgentStateStore {

    private final MangaAgentStateRepository repository;
    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec = JsonUtils.getJsonCodec();

    /** 无条件保存单对象状态；同一组件已存在时递增版本。 */
    @Override
    public void save(String userId, String sessionId, String key, State state) {
        requireCoordinates(userId, sessionId, key);
        Objects.requireNonNull(state, CreativeRuntimeMessages.AGENT_STATE_VALUE_REQUIRED);
        try {
            repository.upsert(toStateRow(
                    userId,
                    sessionId,
                    key,
                    AgentStateKind.SINGLE,
                    state.getClass().getName(),
                    jsonCodec.toJson(state),
                    null));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw invalidPayload(exception);
        }
    }

    /** 声明当前数据库实现支持状态版本比较并交换。 */
    @Override
    public boolean supportsVersioning() {
        return true;
    }

    /** 查询单对象状态及其当前乐观版本。 */
    @Override
    public <T extends State> VersionedState<T> getVersioned(
            String userId,
            String sessionId,
            String key,
            Class<T> stateType) {
        requireCoordinates(userId, sessionId, key);
        Objects.requireNonNull(stateType, CreativeRuntimeMessages.AGENT_STATE_VALUE_REQUIRED);
        try {
            Optional<MangaAgentState> stored = repository.find(userId, sessionId, key);
            if (stored.isEmpty()) {
                return new VersionedState<>(null, 0L);
            }
            MangaAgentState row = stored.orElseThrow();
            requireKind(row, AgentStateKind.SINGLE);
            return new VersionedState<>(deserialize(row, stateType), row.getStateVersion());
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw invalidPayload(exception);
        }
    }

    /**
     * 按期望版本原子保存状态。期望版本为零时只允许首次创建，
     * 期望版本为 UNVERSIONED 时采用普通覆盖语义。
     */
    @Override
    @Transactional
    public long saveIfVersion(
            String userId,
            String sessionId,
            String key,
            State state,
            long expectedVersion) {
        requireCoordinates(userId, sessionId, key);
        Objects.requireNonNull(state, CreativeRuntimeMessages.AGENT_STATE_VALUE_REQUIRED);
        if (expectedVersion < UNVERSIONED) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.AGENT_STATE_VERSION_INVALID);
        }
        MangaAgentState row = toStateRow(
                userId,
                sessionId,
                key,
                AgentStateKind.SINGLE,
                state.getClass().getName(),
                serialize(state),
                expectedVersion);
        try {
            if (expectedVersion == UNVERSIONED) {
                repository.upsert(row);
                return repository.find(userId, sessionId, key)
                        .map(MangaAgentState::getStateVersion)
                        .orElseThrow(() -> unavailable(null));
            }
            boolean updated = expectedVersion == 0L
                    ? repository.insertInitial(row)
                    : repository.updateIfVersion(row);
            if (!updated) {
                throw conflict(userId, sessionId, key, expectedVersion);
            }
            return expectedVersion + 1L;
        } catch (DuplicateKeyException exception) {
            throw conflict(userId, sessionId, key, expectedVersion);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    /** 无条件保存完整列表状态；空列表仍保留列表类型。 */
    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> states) {
        requireCoordinates(userId, sessionId, key);
        Objects.requireNonNull(states, CreativeRuntimeMessages.AGENT_STATE_VALUE_REQUIRED);
        if (states.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(CreativeRuntimeMessages.AGENT_STATE_VALUE_REQUIRED);
        }
        String stateType = states.isEmpty()
                ? State.class.getName()
                : states.getFirst().getClass().getName();
        try {
            repository.upsert(toStateRow(
                    userId,
                    sessionId,
                    key,
                    AgentStateKind.LIST,
                    stateType,
                    jsonCodec.toJson(states),
                    null));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw invalidPayload(exception);
        }
    }

    /** 查询单对象状态，不存在时返回空。 */
    @Override
    public <T extends State> Optional<T> get(
            String userId,
            String sessionId,
            String key,
            Class<T> stateType) {
        VersionedState<T> versioned = getVersioned(userId, sessionId, key, stateType);
        return Optional.ofNullable(versioned.value());
    }

    /** 查询并逐项恢复列表状态。 */
    @Override
    public <T extends State> List<T> getList(
            String userId,
            String sessionId,
            String key,
            Class<T> stateType) {
        requireCoordinates(userId, sessionId, key);
        Objects.requireNonNull(stateType, CreativeRuntimeMessages.AGENT_STATE_VALUE_REQUIRED);
        try {
            Optional<MangaAgentState> stored = repository.find(userId, sessionId, key);
            if (stored.isEmpty()) {
                return List.of();
            }
            MangaAgentState row = stored.orElseThrow();
            requireKind(row, AgentStateKind.LIST);
            JsonNode root = objectMapper.readTree(row.getStateJson());
            if (!root.isArray()) {
                throw new IllegalStateException(CreativeRuntimeMessages.AGENT_STATE_PAYLOAD_INVALID);
            }
            List<T> result = new ArrayList<>(root.size());
            for (JsonNode element : root) {
                result.add(jsonCodec.fromJson(element.toString(), stateType));
            }
            return List.copyOf(result);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw invalidPayload(exception);
        } catch (Exception exception) {
            throw invalidPayload(exception);
        }
    }

    /** 判断指定用户会话是否存在任意持久状态。 */
    @Override
    public boolean exists(String userId, String sessionId) {
        requireCoordinate(userId, CreativeRuntimeMessages.AGENT_STATE_USER_KEY_REQUIRED);
        requireCoordinate(sessionId, CreativeRuntimeMessages.AGENT_STATE_SESSION_ID_REQUIRED);
        try {
            return repository.exists(userId, sessionId);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    /** 删除指定用户会话的全部状态。 */
    @Override
    public void delete(String userId, String sessionId) {
        requireCoordinate(userId, CreativeRuntimeMessages.AGENT_STATE_USER_KEY_REQUIRED);
        requireCoordinate(sessionId, CreativeRuntimeMessages.AGENT_STATE_SESSION_ID_REQUIRED);
        try {
            repository.deleteSession(userId, sessionId);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    /** 删除指定用户会话的单个组件状态。 */
    @Override
    public void delete(String userId, String sessionId, String key) {
        requireCoordinates(userId, sessionId, key);
        try {
            repository.deleteState(userId, sessionId, key);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    /** 列出指定用户拥有状态的会话标识。 */
    @Override
    public Set<String> listSessionIds(String userId) {
        requireCoordinate(userId, CreativeRuntimeMessages.AGENT_STATE_USER_KEY_REQUIRED);
        try {
            return Set.copyOf(new LinkedHashSet<>(repository.listSessionIds(userId)));
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    /** 创建统一审计信息的持久化状态行。 */
    private MangaAgentState toStateRow(
            String userId,
            String sessionId,
            String key,
            AgentStateKind kind,
            String stateType,
            String stateJson,
            Long version) {
        return MangaAgentState.builder()
                .userKey(userId)
                .stateSessionId(sessionId)
                .stateKey(key)
                .stateKind(kind)
                .stateType(stateType)
                .stateJson(stateJson)
                .stateVersion(version)
                .createdBy(CreativeActor.AGENT_STATE_STORE)
                .updatedBy(CreativeActor.AGENT_STATE_STORE)
                .build();
    }

    /** 使用存储类型和调用方期望类型恢复单对象状态。 */
    private <T extends State> T deserialize(MangaAgentState row, Class<T> stateType) {
        if (!Objects.equals(row.getStateType(), stateType.getName())) {
            throw new ClassCastException("Stored Agent state type does not match requested type");
        }
        return jsonCodec.fromJson(row.getStateJson(), stateType);
    }

    /** 序列化状态，并将编解码失败归类为状态载荷错误。 */
    private String serialize(State state) {
        try {
            return jsonCodec.toJson(state);
        } catch (RuntimeException exception) {
            throw invalidPayload(exception);
        }
    }

    /** 校验数据库载荷种类与读取 API 一致。 */
    private void requireKind(MangaAgentState row, AgentStateKind expected) {
        if (row.getStateKind() != expected) {
            throw new ClassCastException("Stored Agent state kind does not match requested kind");
        }
    }

    /** 校验 AgentScope 状态三元坐标。 */
    private void requireCoordinates(String userId, String sessionId, String key) {
        requireCoordinate(userId, CreativeRuntimeMessages.AGENT_STATE_USER_KEY_REQUIRED);
        requireCoordinate(sessionId, CreativeRuntimeMessages.AGENT_STATE_SESSION_ID_REQUIRED);
        requireCoordinate(key, CreativeRuntimeMessages.AGENT_STATE_KEY_REQUIRED);
    }

    /** 校验单个状态坐标文本。 */
    private void requireCoordinate(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 创建 AgentScope 期望的乐观并发冲突异常。 */
    private ConcurrentSessionModificationException conflict(
            String userId,
            String sessionId,
            String key,
            long expectedVersion) {
        return new ConcurrentSessionModificationException(userId, sessionId, key, expectedVersion);
    }

    /** 将持久层故障转换为封闭失败，禁止 Harness 使用空状态继续执行。 */
    private AgentRuntimeException unavailable(Throwable cause) {
        return new AgentRuntimeException(
                CreativeErrorCode.STATE_STORE_UNAVAILABLE,
                CreativeRuntimeMessages.AGENT_STATE_SESSION_GATE_UNAVAILABLE,
                cause);
    }

    /** 将不可恢复状态载荷转换为封闭失败。 */
    private AgentRuntimeException invalidPayload(Throwable cause) {
        return new AgentRuntimeException(
                CreativeErrorCode.STATE_STORE_UNAVAILABLE,
                CreativeRuntimeMessages.AGENT_STATE_PAYLOAD_INVALID,
                cause);
    }
}
