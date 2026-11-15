package com.manga.agent.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.changeset.ChangeSetCodec;
import com.manga.agent.changeset.ChangeSetKind;
import com.manga.common.enums.AiModelType;
import com.manga.common.enums.AiProviderType;
import com.manga.common.enums.AiProxyType;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ResolvedAiProviderConfig;
import com.manga.entity.AiProviderConfig;
import com.manga.entity.GenerationJob;
import com.manga.integration.ai.AgnesEndpoint;
import com.manga.integration.http.ExternalHttpClientFactory;
import com.manga.repository.AiProviderConfigRepository;
import com.manga.service.AiProviderConfigService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 覆盖 Agnes 图片报价、冻结配置及其同步提交协议，不请求真实供应商。 */
class AgnesImageProviderTests {
    private static final long USER_ID = 8L;
    private static final long CONFIG_ID = 12L;
    private static final String MODEL = AgnesImageModel.FLASH_2_5.id();
    private static final BigDecimal ESTIMATE = new BigDecimal("0.010");

    @Test
    void quotesOwnedImageConfigAndValidatesModelAndNativeDimensions() {
        var configs = mock(AiProviderConfigService.class);
        var repository = mock(AiProviderConfigRepository.class);
        var quote = new ImageGenerationQuoteService(configs, properties(), repository);
        var saved = AiProviderConfig.builder().id(CONFIG_ID).name("Agnes 图片").providerType(AiProviderType.AGNES)
                .modelType(AiModelType.IMAGE).enabled(true).baseUrl(AgnesEndpoint.BASE_URL)
                .defaultModel(MODEL).apiKeyCiphertext("test-only").build();
        when(repository.findAllOwnedBy(USER_ID)).thenReturn(List.of(saved));
        var choice = quote.options(USER_ID).getFirst();
        assertThat(choice.provider()).isEqualTo(GenerationProviderKey.AGNES);
        assertThat(choice.sizes()).containsExactly("1024x1024", "1248x832", "832x1248");

        var resolved = config(AgnesEndpoint.BASE_URL, MODEL);
        when(configs.resolveOwned(USER_ID, CONFIG_ID, AiModelType.IMAGE)).thenReturn(Optional.of(resolved));
        var approved = quote.canonicalize(input(MODEL, 832, 1248), USER_ID);
        assertThat(approved.estimatedCost()).isEqualByComparingTo(ESTIMATE);
        assertThat(approved.configSnapshot()).isEqualTo(GenerationConfigSnapshot.from(resolved));
        assertThatThrownBy(() -> quote.canonicalize(input(MODEL, 1024, 1536), USER_ID)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> quote.canonicalize(input(AgnesImageModel.FLASH_2_1.id(), 832, 1248), USER_ID))
                .isInstanceOf(BusinessException.class);

        when(configs.resolveOwned(USER_ID, CONFIG_ID, AiModelType.IMAGE))
                .thenReturn(Optional.of(config("https://untrusted.example/v1", MODEL)));
        assertThatThrownBy(() -> quote.canonicalize(input(MODEL, 832, 1248), USER_ID)).isInstanceOf(BusinessException.class);
    }

    @Test
    void submitsDocumentedBase64RequestAndKeepsVideoRoutingSeparate() throws Exception {
        var quotes = mock(ImageGenerationQuoteService.class);
        var clients = mock(ExternalHttpClientFactory.class);
        var resolved = config(AgnesEndpoint.BASE_URL, MODEL);
        when(quotes.resolve(GenerationProviderKey.AGNES, CONFIG_ID, USER_ID)).thenReturn(resolved);
        var client = WebClient.builder().baseUrl(AgnesEndpoint.VERSIONED_URL).exchangeFunction(request -> {
            assertThat(request.url().toString()).isEqualTo(AgnesEndpoint.VERSIONED_URL + "/images/generations");
            assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-only");
            return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body("{\"data\":[{\"b64_json\":\"aW1hZ2U=\",\"url\":null}]}").build());
        }).build();
        when(clients.createBoundedWebClient(eq(AgnesEndpoint.VERSIONED_URL), any(), any(), anyInt())).thenReturn(client);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var mapper = new ObjectMapper();
            var codec = new ChangeSetCodec(mapper, factory.getValidator());
            var image = new AgnesImageProvider(quotes, clients, properties(), codec, mapper);
            var video = new AgnesVideoProvider(null, clients, properties(), codec);
            var registry = new GenerationProviderRegistry(List.of(image, video));
            var input = input(MODEL, 1248, 832);
            var job = GenerationJob.builder().jobId("image-job").provider(GenerationProviderKey.AGNES)
                    .mediaKind(ChangeSetKind.IMAGE_GENERATION).configId(CONFIG_ID).userId(USER_ID)
                    .requestJson(codec.write(input)).configSnapshotJson(codec.write(GenerationConfigSnapshot.from(resolved))).build();
            var request = mapper.valueToTree(image.requestBody(input));
            assertThat(request).isEqualTo(mapper.readTree("""
                    {"model":"%s","prompt":"A coastal town","size":"1K","ratio":"3:2","return_base64":true}
                    """.formatted(MODEL)));
            assertThat(registry.require(job)).isSameAs(image);
            assertThat(image.idempotentSubmission()).isFalse();
            var response = image.submit(job);
            assertThat(response.providerJobId()).isNull();
            assertThat(response.snapshot().status()).isEqualTo(GenerationJobStatus.MATERIALIZING);
            assertThat(response.snapshot().imageBase64()).isEqualTo("aW1hZ2U=");
            job.setMediaKind(ChangeSetKind.VIDEO_GENERATION);
            assertThat(registry.require(job)).isSameAs(video);
        }
    }

    private GenerationProperties properties() {
        return new GenerationProperties(4, 40, 20, 4, 2, Duration.ofMinutes(20), Duration.ofSeconds(10),
                Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofSeconds(15), 1024 * 1024,
                Duration.ofMinutes(5), Map.of(MODEL, ESTIMATE), "", Duration.ofMinutes(5));
    }

    private ResolvedAiProviderConfig config(String address, String model) {
        return new ResolvedAiProviderConfig(CONFIG_ID, AiProviderType.AGNES, address, model, "test-only",
                AiProxyType.NONE, null, null, null, null);
    }

    private ImageGenerationChange input(String model, int width, int height) {
        return new ImageGenerationChange(2L, GenerationProviderKey.AGNES, CONFIG_ID, model, "A coastal town", width, height,
                BigDecimal.ZERO, null, null);
    }
}
