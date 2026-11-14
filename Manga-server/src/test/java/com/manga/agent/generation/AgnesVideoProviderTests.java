package com.manga.agent.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.GenerationJob;
import com.manga.integration.http.ExternalHttpClientFactory;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 覆盖异步提交、查询协议及下载边界，使用本地 HTTP 替身。 */
class AgnesVideoProviderTests {
    private static final long USER_ID = 8L;
    private static final long CONFIG_ID = 12L;
    private static final String VIDEO_URL = "https://platform-outputs.agnes-ai.space/video.mp4";

    @Test
    void submitsAndPollsByVideoIdAndFrozenModel() {
        var quotes = mock(VideoGenerationQuoteService.class);
        var clients = mock(ExternalHttpClientFactory.class);
        var config = new ResolvedAiProviderConfig(CONFIG_ID, AiProviderType.AGNES, AgnesVideoModel.BASE_URL,
                AgnesVideoModel.FLASH.id(), "test-only", AiProxyType.NONE, null, null, null, null);
        when(quotes.resolve(CONFIG_ID, USER_ID)).thenReturn(config);
        var requests = new ArrayList<String>();
        var client = WebClient.builder().baseUrl(AgnesVideoModel.BASE_URL).exchangeFunction(request -> {
            assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-only");
            requests.add(request.method() + " " + request.url());
            String body = requests.size() == 1
                    ? """
                    {"video_id":"video_test","id":"task_not_for_polling","status":"queued","progress":0}
                    """
                    : """
                    {"status":"completed","progress":100,"internal_status":"pending","url":"%s"}
                    """.formatted(VIDEO_URL);
            return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body).build());
        }).build();
        when(clients.createBoundedWebClient(eq(AgnesVideoModel.BASE_URL), any(), any(), anyInt())).thenReturn(client);
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var codec = new ChangeSetCodec(new ObjectMapper(), validatorFactory.getValidator());
            var snapshot = GenerationConfigSnapshot.from(config);
            var request = new VideoGenerationChange(2L, CONFIG_ID, "Camera moves slowly", 5, "9:16",
                    config.defaultModel(), null, "USD", snapshot);
            var job = GenerationJob.builder().userId(USER_ID).configId(CONFIG_ID).status(GenerationJobStatus.SUBMITTING)
                    .configSnapshotJson(codec.write(snapshot)).requestJson(codec.write(request)).build();
            var provider = new AgnesVideoProvider(quotes, clients, null, codec);
            assertThat(provider.idempotentSubmission()).isFalse();
            var submitted = provider.submit(job);
            assertThat(submitted.providerJobId()).isEqualTo("video_test");
            assertThat(submitted.snapshot().status()).isEqualTo(GenerationJobStatus.SUBMITTED);
            job.setStatus(GenerationJobStatus.SUBMITTED);
            job.setProviderJobId(submitted.providerJobId());
            assertThat(provider.query(job).resultUrl()).isEqualTo(VIDEO_URL);
            assertThat(requests).containsExactlyElementsOf(List.of(
                    "POST https://apihub.agnes-ai.com/v1/videos",
                    "GET https://apihub.agnes-ai.com/agnesapi?video_id=video_test&model_name=agnes-video-2.5-flash"));
        }
    }

    @Test
    void rejectsUntrustedDownloadUrls() {
        assertThatThrownBy(() -> AgnesVideoProvider.resultUri("https://127.0.0.1/private.mp4"))
                .isInstanceOf(GenerationException.class);
        assertThatThrownBy(() -> AgnesVideoProvider.resultUri("https://platform-outputs.agnes-ai.space:8443/video.mp4"))
                .isInstanceOf(GenerationException.class);
    }
}
