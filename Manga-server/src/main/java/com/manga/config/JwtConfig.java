package com.manga.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.constant.SecurityConstants;
import com.manga.config.properties.MangaSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** 配置 JWT 的签发与验证组件。 */
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    /** JWT HMAC SHA-256 签名算法名称。 */
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final MangaSecurityProperties securityProperties;

    /** 创建 JWT 编码器。 */
    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey()));
    }

    /** 创建 JWT 解码器。 */
    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(securityProperties.jwt().issuer())
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** 解析并校验 JWT 签名密钥。 */
    private SecretKey jwtSecretKey() {
        byte[] secretBytes = securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < SecurityConstants.JWT_MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    ExceptionMessageConstants.JWT_SECRET_TOO_SHORT_TEMPLATE
                            .formatted(SecurityConstants.JWT_MIN_SECRET_BYTES)
            );
        }
        return new SecretKeySpec(secretBytes, HMAC_SHA_256);
    }
}
