package com.manga.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.constant.SecurityConstants;
import com.manga.common.security.SecurityErrorResponseWriter;
import com.manga.common.security.TokenAuthenticationFilter;
import com.manga.config.properties.MangaSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证已鉴权的 SSE 在异步返回时继续使用同一请求的身份。 */
@SpringJUnitWebConfig(AsyncRequestSecurityTests.WebConfig.class)
class AsyncRequestSecurityTests {
    private static final String STREAM_PATH = "/api/v1/test/events";
    private static final String TEST_TOKEN = "fixture-token";
    private static final String TERMINAL_EVENT = "RUN_COMPLETED";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void shouldCompleteAuthenticatedSseDispatchWithoutAnHttpSession() throws Exception {
        var started = mvc.perform(get(STREAM_PATH)
                        .header(HttpHeaders.AUTHORIZATION, SecurityConstants.BEARER_PREFIX + TEST_TOKEN))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data:" + TERMINAL_EVENT)));
        assertThat(started.getRequest().getSession(false)).isNull();
        mvc.perform(get(STREAM_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, TokenAuthenticationFilter.class, SecurityErrorResponseWriter.class,
            TestSseController.class})
    static class WebConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        MangaSecurityProperties mangaSecurityProperties() {
            return new MangaSecurityProperties(List.of(), Duration.ZERO, null, null);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token).header("alg", "HS256").subject("test-user")
                    .claim(SecurityConstants.USER_ID_CLAIM, 1L).build();
        }
    }

    @RestController
    static class TestSseController {
        @GetMapping(value = STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        Flux<ServerSentEvent<String>> stream() {
            return Flux.just(ServerSentEvent.builder(TERMINAL_EVENT).build());
        }
    }
}
