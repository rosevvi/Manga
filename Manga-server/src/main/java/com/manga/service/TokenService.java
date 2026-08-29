package com.manga.service;

import com.manga.common.constant.SecurityConstants;
import com.manga.common.enums.UserRole;
import com.manga.common.enums.RegistrationSource;
import com.manga.config.properties.MangaSecurityProperties;
import com.manga.dto.AuthResponse;
import com.manga.dto.CurrentUserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 负责为正式用户和游客签发访问令牌。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final MangaSecurityProperties securityProperties;
    private final Clock clock = Clock.systemUTC();

    /**
     * 根据主体信息和令牌类型签发 Bearer Token。
     */
    public AuthResponse issue(
            Long userId,
            String username,
            String displayName,
            Set<UserRole> roles,
            RegistrationSource registrationSource,
            boolean guest) {
        Duration timeToLive = guest
                ? securityProperties.jwt().guestTokenTtl()
                : securityProperties.jwt().accessTokenTtl();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(timeToLive);
        var roleCodes = roles.stream().map(UserRole::name).sorted().toList();

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(securityProperties.jwt().issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(username)
                .claim(SecurityConstants.DISPLAY_NAME_CLAIM, displayName)
                .claim(SecurityConstants.GUEST_CLAIM, guest)
                .claim(SecurityConstants.ROLES_CLAIM, roleCodes);
        if (userId != null) {
            claimsBuilder.claim(SecurityConstants.USER_ID_CLAIM, userId);
        }

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(headers, claimsBuilder.build()))
                .getTokenValue();
        CurrentUserResponse user = new CurrentUserResponse(
                userId,
                username,
                displayName,
                guest,
                roleCodes,
                null,
                registrationSource == null ? null : registrationSource.name(),
                null,
                null
        );
        log.debug("JWT issued userId={} guest={} roles={} ttlSeconds={}",
                userId, guest, roleCodes, timeToLive.toSeconds());
        return new AuthResponse(SecurityConstants.TOKEN_TYPE, accessToken, timeToLive.toSeconds(), user);
    }
}
