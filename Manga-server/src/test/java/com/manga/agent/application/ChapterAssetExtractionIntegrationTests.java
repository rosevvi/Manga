package com.manga.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.manga.agent.blueprint.AgentBlueprint;
import com.manga.agent.blueprint.AgentBlueprintPreflight;
import com.manga.agent.capability.*;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.harness.MangaAgentInvoker;
import com.manga.common.enums.AiProviderType;
import com.manga.common.security.AuthenticatedUser;
import com.manga.entity.AiProviderConfig;
import com.manga.repository.AiProviderConfigRepository;
import com.manga.service.ProjectAssetService;
import com.manga.service.ProjectScriptService;
import com.manga.service.ScriptTaskService;
import com.manga.common.enums.TaskStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.mockito.Answers;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 走实际导入、调度、工具、事务和状态查询，仅替换模型响应。 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chapter_assets;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class ChapterAssetExtractionIntegrationTests {
    private static final long USER = 1;
    private static final String ACTOR = "test:chapter-assets";
    @Autowired ProjectScriptService scripts;
    @Autowired ScriptTaskService imports;
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS) StringRedisTemplate progressCache;
    @Autowired ProjectAssetService assets;
    @Autowired ChapterAssetExtraction extraction;
    @Autowired CapabilityRegistry tools;
    @Autowired AgentBlueprintPreflight preflight;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockitoBean MangaAgentInvoker model;
    @MockitoBean AiProviderConfigRepository configs;

    @Test
    void importsReuseAssetsAcrossChaptersAndPreserveExistingDetails() {
        long project = project();
        configureModel();
        when(model.invokeUntil(any(), any(), any(), any())).thenAnswer(call -> Flux.defer(() -> {
            preflight.validate(call.getArgument(0, AgentBlueprint.class));
            AgentRuntimeContext context = call.getArgument(1);
            var source = tools.require(CapabilityName.READ_CHAPTER_ASSET_CONTEXT)
                    .execute(new CapabilityCommand(json.createObjectNode()), context).data();
            ObjectNode input = json.createObjectNode().put("revision", source.get("revision").asLong())
                    .put("synopsis", "阿宁在渡口找到铜铃。");
            var found = input.putArray("assets");
            found.addObject().put("assetKind", "CHARACTER").putObject("fields").put("displayName", "阿宁")
                    .put("roleLabel", "主角").put("overview", source.get("title").asText());
            found.addObject().put("assetKind", "SCENE").putObject("fields").put("displayName", "渡口");
            found.addObject().put("assetKind", "PROP").putObject("fields").put("displayName", "铜铃").put("materialDescription", "铜");
            var register = tools.require(CapabilityName.REGISTER_CHAPTER_ASSETS);
            register.execute(new CapabilityCommand(input), context);
            register.execute(new CapabilityCommand(input), context);
            return Flux.empty();
        }));
        long first = single(project);
        waitFor(project, first, CreativeRunStatus.COMPLETED);
        long second = scripts.createTaskChapter(project, "再访", "手写摘要", "阿宁重回渡口，铜铃还在。", "TXT", 2, USER, ACTOR).getId();
        waitFor(project, second, CreativeRunStatus.COMPLETED);
        var firstAssets = assets.chapter(project, first, USER);
        var secondAssets = assets.chapter(project, second, USER);
        assertThat(assets.catalog(project, USER)).hasSize(3);
        assertThat(secondAssets).isEqualTo(firstAssets);
        assertThat(secondAssets.getFirst().fields().overview()).isEqualTo("初遇");
        assertThat(secondAssets.getLast().fields().materialDescription()).isEqualTo("铜");
        assertThat(scripts.findChapterForUser(project, first, USER).synopsis()).isEqualTo("阿宁在渡口找到铜铃。");
        assertThat(scripts.findChapterForUser(project, second, USER).synopsis()).isEqualTo("手写摘要");
    }

    @Test
    void missingModelPreservesImportedChapterAndExposesFailure() {
        long project = project();
        long chapter = single(project);
        String error = waitFor(project, chapter, CreativeRunStatus.FAILED).errorMessage();
        assertThat(error).contains("模型");
        assertThat(scripts.findChapterForUser(project, chapter, USER).rawContent()).contains("铜铃");
    }

    @Test
    void uploadedFileDispatchesOneExtractionPerChapterIncludingEmptyResults() {
        configureModel();
        when(model.invokeUntil(any(), any(), any(), any())).thenAnswer(call -> Flux.defer(() -> {
            AgentRuntimeContext context = call.getArgument(1);
            var source = extraction.read(context, 0);
            extraction.register(context, new ChapterAssetExtraction.Submission(source.revision(), "单章摘要", List.of()));
            return Flux.empty();
        }));
        long project = project();
        var file = new MockMultipartFile("file", "章节.md", "text/markdown",
                "# 第1章 开篇\n天亮了。\n## 第2章 夜幕\n风停了。".getBytes(StandardCharsets.UTF_8));
        var task = imports.submitImport(project, file, "MARKDOWN", null, USER, ACTOR);
        var imported = await().atMost(Duration.ofSeconds(15)).until(() -> imports.toResponseForUser(task.id(), USER),
                item -> item.finishedAt() != null);
        assertThat(imported.status()).as(imported.lastError()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(imported.totalUnits()).isEqualTo(2);
        var chapters = scripts.findForUser(project, USER).chapters();
        assertThat(chapters).extracting(chapter -> chapter.title()).containsExactly("第1章 开篇", "第2章 夜幕");
        for (var chapter : chapters) waitFor(project, chapter.id(), CreativeRunStatus.COMPLETED);
        assertThat(assets.catalog(project, USER)).isEmpty();
    }

    @Test
    void textOnlyAnswerCannotCompleteExtraction() {
        configureModel();
        when(model.invokeUntil(any(), any(), any(), any())).thenReturn(Flux.empty());
        long project = project();
        long chapter = single(project);
        assertThat(waitFor(project, chapter, CreativeRunStatus.FAILED).errorMessage()).contains("未提交");
        assertThat(assets.catalog(project, USER)).isEmpty();
    }

    @Test
    void invalidExistingIdRollsBackTheEntireBatch() {
        configureModel();
        when(model.invokeUntil(any(), any(), any(), any())).thenAnswer(call -> Flux.defer(() -> {
            AgentRuntimeContext context = call.getArgument(1);
            ObjectNode input = json.createObjectNode().put("revision", 1).put("synopsis", "待验证摘要");
            var found = input.putArray("assets");
            found.addObject().put("assetKind", "CHARACTER").putObject("fields").put("displayName", "阿宁");
            found.addObject().put("assetKind", "PROP").put("existingAssetId", Long.MAX_VALUE)
                    .putObject("fields").put("displayName", "铜铃");
            tools.require(CapabilityName.REGISTER_CHAPTER_ASSETS).execute(new CapabilityCommand(input), context);
            return Flux.empty();
        }));
        long project = project();
        long chapter = single(project);
        waitFor(project, chapter, CreativeRunStatus.FAILED);
        assertThat(assets.catalog(project, USER)).isEmpty();
        assertThat(scripts.findChapterForUser(project, chapter, USER).synopsis()).isNull();
    }

    private void configureModel() {
        when(configs.findDefaultOwnedBy(USER)).thenReturn(Optional.of(AiProviderConfig.builder()
                .id(1L).enabled(true).providerType(AiProviderType.OPENAI_COMPATIBLE)
                .baseUrl("https://example.invalid/v1").defaultModel("test-model").build()));
    }

    private long project() {
        String name = "assets-" + UUID.randomUUID();
        db.update("INSERT INTO manga_project(owner_user_id,name,created_by,updated_by) VALUES(?,?,?,?)", USER, name, ACTOR, ACTOR);
        return db.queryForObject("SELECT id FROM manga_project WHERE name=?", Long.class, name);
    }

    private long single(long project) {
        var identity = new AuthenticatedUser(USER, ACTOR, ACTOR, false, List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
        try {
            return scripts.createChapter(project, "初遇", null, "阿宁在渡口拾起铜铃。", "MANUAL").id();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ChapterAssetExtraction.Attempt waitFor(long project, long chapter, CreativeRunStatus expected) {
        var result = await().atMost(Duration.ofSeconds(15)).until(() -> extraction.latest(project, chapter, USER),
                attempt -> attempt != null && attempt.status().isTerminal());
        assertThat(result.status()).as(result.errorMessage()).isEqualTo(expected);
        return result;
    }
}
