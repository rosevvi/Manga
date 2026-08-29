package com.manga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.common.constant.AuditConstants;
import com.manga.common.constant.LoggingConstants;
import com.manga.common.constant.SecurityConstants;
import com.manga.common.constant.WechatConstants;
import com.manga.common.enums.ApplicationStatus;
import com.manga.common.enums.AuthResponseCode;
import com.manga.common.enums.CommonResponseCode;
import com.manga.common.enums.ExternalLoginStatus;
import com.manga.common.enums.RegistrationSource;
import com.manga.common.enums.StoryboardShotStatus;
import com.manga.common.enums.UserRole;
import com.manga.common.enums.AiProviderType;
import com.manga.config.properties.MangaSecurityProperties;
import com.manga.integration.wechat.WechatOfficialAccountClient;
import com.manga.integration.wechat.WechatQrCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证数据库账号、游客令牌和角色权限的完整鉴权流程。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTests {

    private static final String PUBLIC_HEALTH_ENDPOINT = "/api/v1/public/health";
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String GUEST_LOGIN_ENDPOINT = "/api/v1/auth/guest";
    private static final String WECHAT_QR_LOGIN_ENDPOINT = "/api/v1/auth/wechat/qr";
    private static final String WECHAT_QR_LOGIN_STATUS_ENDPOINT = "/api/v1/auth/wechat/qr/{loginToken}";
    private static final String WECHAT_CALLBACK_ENDPOINT = "/api/v1/auth/wechat/callback";
    private static final String CURRENT_USER_ENDPOINT = "/api/v1/users/me";
    private static final String CURRENT_USER_PASSWORD_ENDPOINT = "/api/v1/users/me/password";
    private static final String USERS_ENDPOINT = "/api/v1/users";
    private static final String USER_ROLES_ENDPOINT = "/api/v1/users/{userId}/roles";
    private static final String ROLES_ENDPOINT = "/api/v1/roles";
    private static final String PROJECTS_ENDPOINT = "/api/v1/projects";
    private static final String PROJECT_BY_ID_ENDPOINT = "/api/v1/projects/{projectId}";
    private static final String STORYBOARD_SHOTS_ENDPOINT = "/api/v1/projects/{projectId}/storyboard-shots";
    private static final String STORYBOARD_SHOT_BY_ID_ENDPOINT =
            "/api/v1/projects/{projectId}/storyboard-shots/{shotId}";
    private static final String STORYBOARD_SHOT_ORDER_ENDPOINT =
            "/api/v1/projects/{projectId}/storyboard-shots/order";
    private static final String AI_PROVIDER_CONFIGS_ENDPOINT = "/api/v1/ai-provider-configs";
    private static final String AI_PROVIDER_CONFIG_BY_ID_ENDPOINT = "/api/v1/ai-provider-configs/{configId}";
    private static final String AI_PROVIDER_CONFIG_DEFAULT_ENDPOINT =
            "/api/v1/ai-provider-configs/{configId}/default";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Manga-test-admin-password";
    private static final String INVALID_PASSWORD = "incorrect-password";
    private static final String UPDATED_DISPLAY_NAME = "Manga Test Administrator";
    private static final String UPDATED_PASSWORD = "Manga-updated-password";
    private static final String WECHAT_TEST_TOKEN = "test-callback-token";
    private static final String WECHAT_TEST_OPEN_ID = "wechat-open-id-for-integration-test";
    private static final String WECHAT_TEST_TICKET = "test-qr-ticket";
    private static final String WECHAT_TEST_QR_URL = "https://mp.weixin.qq.com/test-qr";
    private static final String WECHAT_TEST_TIMESTAMP = "1722513600";
    private static final String WECHAT_TEST_NONCE = "manga-nonce";
    private static final String TEST_TRACE_ID = "manga-integration-trace";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private MangaSecurityProperties securityProperties;

    @MockitoBean
    private WechatOfficialAccountClient wechatOfficialAccountClient;

    /**
     * 确认健康接口允许匿名访问。
     */
    @Test
    void publicHealthShouldBeAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get(PUBLIC_HEALTH_ENDPOINT)
                        .header(LoggingConstants.TRACE_ID_HEADER, TEST_TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(LoggingConstants.TRACE_ID_HEADER, TEST_TRACE_ID))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(ApplicationStatus.UP.name()));
    }

    /**
     * 确认匿名访问受保护接口时返回统一未认证响应。
     */
    @Test
    void protectedEndpointShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get(CURRENT_USER_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonResponseCode.UNAUTHORIZED.code()));
    }

    /**
     * 确认初始管理员可以登录并访问角色管理接口。
     */
    @Test
    void initialAdministratorShouldLoginAndAccessRoleModule() throws Exception {
        String token = loginAsAdministrator();

        mockMvc.perform(get(CURRENT_USER_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.data.guest").value(false))
                .andExpect(jsonPath("$.data.roles[?(@ == 'ADMIN')]").exists());
        mockMvc.perform(get(ROLES_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'ADMIN')]").exists())
                .andExpect(jsonPath("$.data[?(@.code == 'GUEST')].assignable").value(false));
    }

    /**
     * 确认错误密码不会暴露账号细节且不能获得令牌。
     */
    @Test
    void invalidPasswordShouldBeRejected() throws Exception {
        mockMvc.perform(post(LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(ADMIN_USERNAME, INVALID_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthResponseCode.INVALID_CREDENTIALS.code()));
    }

    /**
     * 确认游客令牌可以读取自身身份但不能访问管理员接口。
     */
    @Test
    void guestShouldReceiveLimitedToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post(GUEST_LOGIN_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.guest").value(true))
                .andExpect(jsonPath("$.data.user.roles[0]").value(UserRole.GUEST.name()))
                .andReturn();
        String token = accessToken(loginResult);

        mockMvc.perform(get(CURRENT_USER_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guest").value(true));
        mockMvc.perform(get(ROLES_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CommonResponseCode.FORBIDDEN.code()));
        mockMvc.perform(get(PROJECTS_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CommonResponseCode.FORBIDDEN.code()));
        mockMvc.perform(get(STORYBOARD_SHOTS_ENDPOINT, 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CommonResponseCode.FORBIDDEN.code()));
        mockMvc.perform(get(AI_PROVIDER_CONFIGS_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CommonResponseCode.FORBIDDEN.code()));
    }

    /**
     * 验证项目、分镜和 AI 配置的基础增删改查链路及用户隔离。
     */
    @Test
    @Transactional
    void administratorShouldCompleteProjectStoryboardAndAiConfigCrud() throws Exception {
        String token = loginAsAdministrator();

        MvcResult projectCreate = mockMvc.perform(post(PROJECTS_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "CRUD Integration Project",
                                "description", "Project created by integration test",
                                "coverUrl", "https://example.com/project-cover.png",
                                "genre", "Action",
                                "status", "DRAFT"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("CRUD Integration Project"))
                .andReturn();
        long projectId = objectMapper.readTree(projectCreate.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get(PROJECTS_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(projectId)).exists());
        mockMvc.perform(get(PROJECT_BY_ID_ENDPOINT, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(projectId));
        mockMvc.perform(put(PROJECT_BY_ID_ENDPOINT, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Updated CRUD Project",
                                "description", "Updated description",
                                "coverUrl", "https://example.com/updated-cover.png",
                                "genre", "Drama",
                                "status", "IN_PROGRESS"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated CRUD Project"));

        MvcResult shotCreate = mockMvc.perform(post(STORYBOARD_SHOTS_ENDPOINT, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "title", "Opening shot",
                                "sceneName", "Rooftop",
                                "shotType", "WIDE",
                                "cameraMovement", "PAN",
                                "durationSeconds", 8,
                                "content", "The city wakes under a crimson sky.",
                                "dialogue", "We begin.",
                                "soundEffect", "Wind",
                                "imageUrl", "https://example.com/shot.png",
                                "notes", "Establish the setting.",
                                "status", "DRAFT"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andReturn();
        long shotId = objectMapper.readTree(shotCreate.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get(STORYBOARD_SHOTS_ENDPOINT, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(shotId));
        mockMvc.perform(put(STORYBOARD_SHOT_BY_ID_ENDPOINT, projectId, shotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "title", "Updated opening shot",
                                "sceneName", "Updated rooftop",
                                "shotType", "MEDIUM",
                                "cameraMovement", "STATIC",
                                "durationSeconds", 10,
                                "content", "The updated opening.",
                                "dialogue", "We continue.",
                                "soundEffect", "City ambience",
                                "imageUrl", "https://example.com/updated-shot.png",
                                "notes", "Updated notes.",
                                "status", "READY"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated opening shot"))
                .andExpect(jsonPath("$.data.status").value(StoryboardShotStatus.READY.name()));
        mockMvc.perform(put(STORYBOARD_SHOT_ORDER_ENDPOINT, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("shotIds", java.util.List.of(shotId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sortOrder").value(1));
        mockMvc.perform(delete(STORYBOARD_SHOT_BY_ID_ENDPOINT, projectId, shotId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        MvcResult configCreate = mockMvc.perform(post(AI_PROVIDER_CONFIGS_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "CRUD AI Provider",
                                "providerType", AiProviderType.OPENAI.name(),
                                "baseUrl", "https://api.openai.com",
                                "defaultModel", "gpt-test",
                                "apiKey", "sk-integration-test-secret",
                                "enabled", true,
                                "defaultConfig", true,
                                "remark", "Integration test provider"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasApiKey").value(true))
                .andReturn();
        long configId = objectMapper.readTree(configCreate.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(put(AI_PROVIDER_CONFIG_BY_ID_ENDPOINT, configId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Updated CRUD AI Provider",
                                "providerType", AiProviderType.OPENAI.name(),
                                "baseUrl", "https://api.openai.com",
                                "defaultModel", "gpt-updated",
                                "apiKey", "",
                                "removeApiKey", false,
                                "enabled", true,
                                "defaultConfig", true,
                                "remark", "Updated provider"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated CRUD AI Provider"))
                .andExpect(jsonPath("$.data.hasApiKey").value(true));
        mockMvc.perform(get(AI_PROVIDER_CONFIGS_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(configId));
        mockMvc.perform(put(AI_PROVIDER_CONFIG_DEFAULT_ENDPOINT, configId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultConfig").value(true));
        mockMvc.perform(delete(AI_PROVIDER_CONFIG_BY_ID_ENDPOINT, configId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get(AI_PROVIDER_CONFIGS_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(configId)).doesNotExist());

        mockMvc.perform(delete(PROJECT_BY_ID_ENDPOINT, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROJECT_BY_ID_ENDPOINT, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    /**
     * 确认非 Bearer、签名错误和过期令牌均返回统一未认证响应。
     */
    @Test
    void invalidAuthorizationHeadersShouldBeRejected() throws Exception {
        String validToken = loginAsAdministrator();

        assertUnauthorized("Basic credentials");
        assertUnauthorized(bearer("malformed"));
        assertUnauthorized(bearer(tamperSignature(validToken)));
        assertUnauthorized(bearer(expiredToken()));
    }

    /**
     * 确认查询参数中的令牌不会被当作认证凭据。
     */
    @Test
    void tokenInQueryParameterShouldBeRejected() throws Exception {
        mockMvc.perform(get(CURRENT_USER_ENDPOINT)
                        .queryParam("access_token", loginAsAdministrator()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonResponseCode.UNAUTHORIZED.code()));
    }

    /**
     * 确认管理员可以查看数据库用户并维护其角色集合。
     */
    @Test
    void administratorShouldManageUserRoles() throws Exception {
        String token = loginAsAdministrator();
        MvcResult usersResult = mockMvc.perform(get(USERS_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value(ADMIN_USERNAME))
                .andReturn();
        JsonNode users = objectMapper.readTree(usersResult.getResponse().getContentAsString()).path("data");
        long adminId = users.path(0).path("id").asLong();

        mockMvc.perform(put(USER_ROLES_ENDPOINT, adminId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("roleCodes", java.util.List.of("ADMIN", "USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[?(@ == 'ADMIN')]").exists())
                .andExpect(jsonPath("$.data.roles[?(@ == 'USER')]").exists())
                .andExpect(jsonPath("$.data.createdBy").value(AuditConstants.SYSTEM_ACTOR))
                .andExpect(jsonPath("$.data.updatedBy").value(AuditConstants.SYSTEM_ACTOR));
    }

    /**
     * 确认登录用户可以维护显示名称并在校验原密码后修改密码。
     */
    @Test
    @Transactional
    void administratorShouldManageOwnProfileAndPassword() throws Exception {
        String token = loginAsAdministrator();

        mockMvc.perform(put(CURRENT_USER_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("displayName", UPDATED_DISPLAY_NAME)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value(UPDATED_DISPLAY_NAME))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(put(CURRENT_USER_PASSWORD_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "currentPassword", ADMIN_PASSWORD,
                                "newPassword", UPDATED_PASSWORD
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post(LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(ADMIN_USERNAME, UPDATED_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.displayName").value(UPDATED_DISPLAY_NAME));
    }

    /**
     * 确认公众号关注事件能够绑定微信身份并一次性兑换登录令牌。
     */
    @Test
    @Transactional
    void wechatFollowEventShouldCompleteQrLoginOnce() throws Exception {
        when(wechatOfficialAccountClient.createTemporaryQrCode(anyString(), any(Duration.class)))
                .thenReturn(new WechatQrCode(WECHAT_TEST_TICKET, WECHAT_TEST_QR_URL, 300));

        MvcResult createResult = mockMvc.perform(post(WECHAT_QR_LOGIN_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrCodeUrl").value(WECHAT_TEST_QR_URL))
                .andReturn();
        String loginToken = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("loginToken")
                .asText();
        var sceneCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(wechatOfficialAccountClient).createTemporaryQrCode(sceneCaptor.capture(), any(Duration.class));

        mockMvc.perform(get(WECHAT_QR_LOGIN_STATUS_ENDPOINT, loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ExternalLoginStatus.WAITING.name()));

        String callbackXml = """
                <xml>
                  <FromUserName><![CDATA[%s]]></FromUserName>
                  <MsgType><![CDATA[event]]></MsgType>
                  <Event><![CDATA[subscribe]]></Event>
                  <EventKey><![CDATA[qrscene_%s]]></EventKey>
                </xml>
                """.formatted(WECHAT_TEST_OPEN_ID, sceneCaptor.getValue());
        mockMvc.perform(post(WECHAT_CALLBACK_ENDPOINT)
                        .queryParam(WechatConstants.SIGNATURE_PARAMETER, wechatSignature())
                        .queryParam(WechatConstants.TIMESTAMP_PARAMETER, WECHAT_TEST_TIMESTAMP)
                        .queryParam(WechatConstants.NONCE_PARAMETER, WECHAT_TEST_NONCE)
                        .contentType(MediaType.TEXT_XML)
                        .content(callbackXml))
                .andExpect(status().isOk());

        mockMvc.perform(get(WECHAT_QR_LOGIN_STATUS_ENDPOINT, loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ExternalLoginStatus.CONFIRMED.name()))
                .andExpect(jsonPath("$.data.authentication.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.authentication.user.registrationSource")
                        .value(RegistrationSource.WECHAT_OFFICIAL_ACCOUNT.name()))
                .andExpect(jsonPath("$.data.authentication.user.roles[0]").value(UserRole.USER.name()));

        mockMvc.perform(get(WECHAT_QR_LOGIN_STATUS_ENDPOINT, loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ExternalLoginStatus.CONSUMED.name()))
                .andExpect(jsonPath("$.data.authentication").doesNotExist());
    }

    /**
     * 确认微信服务器可以完成首次回调地址签名校验。
     */
    @Test
    void wechatCallbackVerificationShouldEchoChallenge() throws Exception {
        String challenge = "wechat-echo-challenge";
        mockMvc.perform(get(WECHAT_CALLBACK_ENDPOINT)
                        .queryParam(WechatConstants.SIGNATURE_PARAMETER, wechatSignature())
                        .queryParam(WechatConstants.TIMESTAMP_PARAMETER, WECHAT_TEST_TIMESTAMP)
                        .queryParam(WechatConstants.NONCE_PARAMETER, WECHAT_TEST_NONCE)
                        .queryParam(WechatConstants.ECHO_STRING_PARAMETER, challenge))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString()
                ).isEqualTo(challenge));
    }

    private String loginAsAdministrator() throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value(SecurityConstants.TOKEN_TYPE))
                .andExpect(jsonPath("$.data.user.roles[?(@ == 'ADMIN')]").exists())
                .andReturn();
        return accessToken(result);
    }

    private String loginPayload(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("accessToken").asText();
    }

    private String bearer(String token) {
        return SecurityConstants.TOKEN_TYPE + " " + token;
    }

    private void assertUnauthorized(String authorization) throws Exception {
        mockMvc.perform(get(CURRENT_USER_ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonResponseCode.UNAUTHORIZED.code()));
    }

    private String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char firstSignatureCharacter = token.charAt(signatureStart);
        char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private String expiredToken() {
        Instant expiresAt = Instant.now().minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.jwt().issuer())
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .subject(ADMIN_USERNAME)
                .claim(SecurityConstants.USER_ID_CLAIM, 1L)
                .claim(SecurityConstants.DISPLAY_NAME_CLAIM, UPDATED_DISPLAY_NAME)
                .claim(SecurityConstants.GUEST_CLAIM, false)
                .claim(SecurityConstants.ROLES_CLAIM, java.util.List.of(UserRole.ADMIN.name()))
                .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    private String wechatSignature() throws Exception {
        String[] values = {WECHAT_TEST_TOKEN, WECHAT_TEST_TIMESTAMP, WECHAT_TEST_NONCE};
        Arrays.sort(values);
        return HexFormat.of().formatHex(
                MessageDigest.getInstance(WechatConstants.SHA_1_ALGORITHM)
                        .digest(String.join("", values).getBytes(StandardCharsets.UTF_8))
        );
    }
}
