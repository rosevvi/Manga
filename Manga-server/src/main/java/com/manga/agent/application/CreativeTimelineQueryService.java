package com.manga.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.api.CreativeTimelinePage;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintCodec;
import com.manga.agent.domain.CreativeEventType;
import com.manga.agent.domain.CreativeResourceType;
import com.manga.agent.event.CreativeEventSources;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.exception.BusinessException;
import com.manga.entity.CreativeEvent;
import com.manga.entity.CreativeStep;
import com.manga.entity.CreativeRun;
import com.manga.repository.CreativeEventRepository;
import com.manga.repository.CreativeRunRepository;
import com.manga.repository.CreativeStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** 从持久化 Journal 重建执行详情，权限与快照水位统一，固定次数查询避免 N+1。 */
@Service
@RequiredArgsConstructor
public class CreativeTimelineQueryService {
    private final CreativeRunRepository runs;
    private final CreativeStepRepository steps;
    private final CreativeEventRepository events;
    private final AgentBlueprintCodec blueprints;
    private final ObjectMapper mapper;

    /** 游标按扫描事实推进，隐藏的内部事件也推进水位，旧载荷缺失摘要时保留状态。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CreativeTimelinePage find(String runId, long userId, long afterSequence, int limit) {
        var run = runs.findOwned(runId, userId).orElseThrow(() ->
                new BusinessException(CommonResponseCode.FORBIDDEN, HttpStatus.FORBIDDEN));
        int pageSize = Math.clamp(limit, 1, 200);
        var rows = events.findBatchAfter(runId, Math.max(0, afterSequence), pageSize + 1);
        var page = rows.stream().limit(pageSize).toList();
        Map<String, CreativeStep> byId = steps.findByRunId(runId).stream()
                .collect(Collectors.toMap(CreativeStep::getStepId, Function.identity()));
        Map<String, AgentBlueprint> specs = byId.values().stream().filter(step -> Objects.nonNull(step.getBlueprintSnapshotJson()))
                .collect(Collectors.toMap(CreativeStep::getStepId, step -> blueprints.deserialize(step.getBlueprintSnapshotJson())));
        var items = page.stream().filter(event -> !(event.getEventType() == CreativeEventType.CONTENT_DELTA
                        && CreativeEventSources.WORKER.equals(event.getSource())))
                .map(event -> item(event, byId.get(event.getStepId()), specs.get(event.getStepId()), run))
                .toList();
        return new CreativeTimelinePage(items, page.isEmpty() ? afterSequence : page.getLast().getSequenceNo(),
                run.getNextSequence() - 1, rows.size() > pageSize);
    }

    /** 显式白名单映射，新增内部载荷不会意外进入公开接口。 */
    private CreativeTimelinePage.Item item(CreativeEvent event, CreativeStep step, AgentBlueprint blueprint, CreativeRun run) {
        JsonNode payload = payload(event);
        String type = event.getEventType().name();
        boolean tool = event.getEventType() == CreativeEventType.TOOL_STARTED || event.getEventType() == CreativeEventType.TOOL_COMPLETED;
        String action = tool ? payload.path("toolName").asText("工具调用") : type;
        String status = payload.path("status").asText(type);
        String result = tool ? text(payload, "resultSummary") : event.getEventType() == CreativeEventType.CONTENT_DELTA ? text(payload, "delta") : null;
        String failure = event.getEventType() == CreativeEventType.STEP_FAILED ? payload.path("errorMessage")
                .asText(Objects.isNull(step) ? null : step.getErrorMessage())
                : event.getEventType() == CreativeEventType.RUN_FAILED ? run.getErrorMessage()
                : event.getEventType() == CreativeEventType.JOB_FAILED ? text(payload, "errorMessage") : null;
        String agent = Objects.isNull(blueprint) ? null : Objects.isNull(blueprint.executionPolicy())
                ? blueprint.profileId() : blueprint.executionPolicy().displayName();
        Long epoch = payload.hasNonNull("ownerEpoch") ? payload.get("ownerEpoch").asLong() : null;
        List<String> resources = payload.hasNonNull("artifactId") ? List.of(payload.get("artifactId").asText()) : List.of();
        List<CreativeTimelinePage.ResourceLink> links = resources.isEmpty() || Objects.isNull(run.getProjectId()) ? List.of()
                : List.of(new CreativeTimelinePage.ResourceLink("在分镜工作区查看产物", "/console/projects/" + run.getProjectId() + "/storyboards"
                        + (run.getTargetType() == CreativeResourceType.STORYBOARD_SHOT ? "?shot=" + run.getTargetId() : "")));
        LocalDateTime started = date(payload, "startedAt");
        LocalDateTime finished = date(payload, "finishedAt");
        if (event.getEventType() == CreativeEventType.STEP_STARTED) started = event.getCreatedAt();
        if (event.getEventType() == CreativeEventType.STEP_FAILED || event.getEventType() == CreativeEventType.STEP_COMPLETED) finished = event.getCreatedAt();
        return new CreativeTimelinePage.Item(event.getRunId() + ":" + event.getSequenceNo(), event.getSequenceNo(), 2, type,
                event.getStepId(), Objects.isNull(step) ? null : step.getParentStepId(), epoch, agent,
                Objects.isNull(blueprint) ? null : blueprint.profileVersion(), text(payload, "toolCallId"), action, status,
                event.getCreatedAt(), started, finished, text(payload, "inputSummary"),
                result, resources, links, failure);
    }

    /** 旧事件和空载荷可展示基础事实；不猜测任何原始字段含义。 */
    private JsonNode payload(CreativeEvent event) {
        if (Objects.isNull(event.getPayloadJson())) return mapper.createObjectNode();
        try { return mapper.readTree(event.getPayloadJson()); }
        catch (JsonProcessingException failure) { return mapper.createObjectNode(); }
    }
    /** 只读取已明确列入公开 DTO 的标量字段。 */
    private String text(JsonNode node, String field) { return node.hasNonNull(field) ? node.get(field).asText() : null; }
    /** 缺失或旧格式时间不阻断其余历史事实的展示。 */
    private LocalDateTime date(JsonNode node, String field) {
        String value = text(node, field);
        if (Objects.isNull(value)) return null;
        try { return LocalDateTime.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }
}
