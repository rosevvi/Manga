package com.manga.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.CreativeRunStatus;
import com.manga.agent.harness.SessionGateLease;
import com.manga.agent.harness.SessionSerialGate;
import com.manga.common.enums.AiModelType;
import com.manga.common.enums.AiProviderType;
import com.manga.entity.AiProviderConfig;
import com.manga.repository.AiProviderConfigRepository;
import com.manga.service.ProjectAssetService;
import com.manga.service.ProjectScriptService;
import com.manga.service.security.SecretCipher;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 用本机流式模型响应验证 AgentScope 真正执行两次工具调用，并将章节资产写入数据库。 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:asset_protocol;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class ChapterAssetToolProtocolTests {
    @Autowired JdbcTemplate db;
    @Autowired ProjectScriptService scripts;
    @Autowired ChapterAssetExtraction extraction;
    @Autowired ProjectAssetService assets;
    @Autowired AiProviderConfigRepository configs;
    @Autowired SecretCipher cipher;
    @Autowired ObjectMapper json;
    @MockitoBean SessionSerialGate gate;

    @Test
    void runsTheAdvertisedToolsThroughChatCompletions() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                boolean read = requests.size() == 1;
                String tool = (read ? CapabilityName.READ_CHAPTER_ASSET_CONTEXT : CapabilityName.REGISTER_CHAPTER_ASSETS).code();
                String args = read ? "{}" : """
                        {"revision":1,"synopsis":"阿宁拾起铜铃。","assets":[{"assetKind":"PROP",
                        "fields":{"displayName":"铜铃","materialDescription":"铜"}}]}
                        """;
                var call = Map.of("index", 0, "id", "tool-" + requests.size(), "type", "function",
                        "function", Map.of("name", tool, "arguments", args));
                String chunk = json.writeValueAsString(Map.of("id", "reply-" + requests.size(),
                        "object", "chat.completion.chunk", "created", 1, "model", "test-model", "choices",
                        List.of(Map.of("index", 0, "delta", Map.of("role", "assistant", "tool_calls", List.of(call)),
                                "finish_reason", "tool_calls"))));
                byte[] body = ("data: " + chunk + "\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            when(gate.acquire(anyString())).thenReturn(mock(SessionGateLease.class));
            configs.create(AiProviderConfig.builder().ownerUserId(1L).name("local fixture").defaultConfig(true).enabled(true)
                    .providerType(AiProviderType.OPENAI_COMPATIBLE).modelType(AiModelType.TEXT)
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1").defaultModel("test-model")
                    .apiKeyCiphertext(cipher.encrypt("fixture")).createdBy("test").updatedBy("test").build());
            db.update("INSERT INTO manga_project(owner_user_id,name,created_by,updated_by) VALUES(1,'协议测试','test','test')");
            long project = db.queryForObject("SELECT id FROM manga_project WHERE name='协议测试'", Long.class);
            long chapter = scripts.createTaskChapter(project, "初遇", null, "阿宁拾起一只铜铃。", "TXT", 1, 1, "test").getId();
            var attempt = await().atMost(Duration.ofSeconds(30)).until(() -> extraction.latest(project, chapter, 1),
                    run -> run != null && run.status().isTerminal());
            assertThat(attempt.status()).as(attempt.errorMessage()).isEqualTo(CreativeRunStatus.COMPLETED);
            assertThat(requests.size()).isGreaterThanOrEqualTo(2);
            assertThat(requests.get(0)).contains(CapabilityName.REGISTER_CHAPTER_ASSETS.code());
            assertThat(requests.get(1)).contains("阿宁拾起一只铜铃。");
            assertThat(assets.chapter(project, chapter, 1).getFirst().fields().materialDescription()).isEqualTo("铜");
        } finally {
            server.stop(0);
        }
    }
}
