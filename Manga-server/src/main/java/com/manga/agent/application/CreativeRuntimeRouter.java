package com.manga.agent.application;

import com.manga.agent.config.CreativeRuntimeRoutingProperties;
import com.manga.agent.domain.CreativeRuntimeType;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.AgentRun;
import com.manga.repository.AgentRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按持久化运行时类型和灰度规则选择创作运行实现。
 */
@Component
public class CreativeRuntimeRouter {

    private static final Logger log = LoggerFactory.getLogger(CreativeRuntimeRouter.class);
    private static final String DUPLICATE_RUNTIME_TEMPLATE = "Duplicate creative runtime implementation: %s";
    private static final String MISSING_RUNTIME_TEMPLATE = "Creative runtime implementation is unavailable: %s";

    private final Map<CreativeRuntimeType, CreativeRuntime> runtimes;
    private final CreativeRuntimeRoutingProperties properties;
    private final AgentRunRepository runRepository;

    public CreativeRuntimeRouter(List<CreativeRuntime> runtimes,
            CreativeRuntimeRoutingProperties properties,
            AgentRunRepository runRepository) {
        this.runtimes = indexRuntimes(runtimes);
        this.properties = properties;
        this.runRepository = runRepository;
    }

    /** 按项目、用户和默认规则启动一次创作运行。 */
    public CreativeRunStartResult start(StartCreativeRunCommand command) {
        CreativeRuntimeType runtimeType = resolveForStart(command.userId(), command.projectId());
        log.info("Creative runtime selected runtimeType={} userId={} projectId={}",
                runtimeType, command.userId(), command.projectId());
        return requireRuntime(runtimeType).start(command);
    }

    /** 按 Run 创建时持久化的运行时类型转发取消请求。 */
    public void cancel(CancelCreativeRunCommand command) {
        AgentRun run = runRepository.findOwned(command.runId(), command.userId())
                .orElseThrow(() -> new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
        CreativeRuntimeType runtimeType = Objects.isNull(run.getRuntimeType())
                ? CreativeRuntimeType.LEGACY
                : run.getRuntimeType();
        log.info("Creative run cancellation routed runtimeType={} runId={} userId={}",
                runtimeType, command.runId(), command.userId());
        requireRuntime(runtimeType).cancel(command);
    }

    /** 项目覆盖优先于用户覆盖，均未命中时返回默认运行时。 */
    CreativeRuntimeType resolveForStart(long userId, Long projectId) {
        if (Objects.nonNull(projectId)) {
            CreativeRuntimeType projectRuntime = properties.getProjectOverrides().get(projectId);
            if (Objects.nonNull(projectRuntime)) {
                return projectRuntime;
            }
        }
        CreativeRuntimeType userRuntime = properties.getUserOverrides().get(userId);
        return Objects.isNull(userRuntime) ? properties.getDefaultRuntime() : userRuntime;
    }

    /** 返回已注册的运行时实现，缺失时立即暴露配置错误。 */
    private CreativeRuntime requireRuntime(CreativeRuntimeType runtimeType) {
        CreativeRuntime runtime = runtimes.get(runtimeType);
        if (Objects.isNull(runtime)) {
            throw new IllegalStateException(MISSING_RUNTIME_TEMPLATE.formatted(runtimeType));
        }
        return runtime;
    }

    /** 将 Spring 收集的策略实现构造成不可变索引，并拒绝重复类型。 */
    private Map<CreativeRuntimeType, CreativeRuntime> indexRuntimes(List<CreativeRuntime> candidates) {
        EnumMap<CreativeRuntimeType, CreativeRuntime> indexed = new EnumMap<>(CreativeRuntimeType.class);
        for (CreativeRuntime candidate : candidates) {
            CreativeRuntime previous = indexed.putIfAbsent(candidate.runtimeType(), candidate);
            if (Objects.nonNull(previous)) {
                throw new IllegalStateException(DUPLICATE_RUNTIME_TEMPLATE.formatted(candidate.runtimeType()));
            }
        }
        return Map.copyOf(indexed);
    }
}
