package com.manga.agent.profile;

import com.manga.repository.AgentProfileRepository;
import com.manga.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 新调用读取启用的发布指针；恢复调用只读取已有 Blueprint，不经过本源。 */
@Component
@Primary
@RequiredArgsConstructor
public class DatabaseAgentProfileSource implements AgentProfileSource {
    private final AgentProfileRepository profiles;
    private final AgentProfileDefinitionCodec codec;

    /** 内置操作也使用数据库发布版本，资源定义只用于初次初始化。 */
    @Override
    @Transactional
    public AgentProfile require(AgentProfileKey key) { return require(key.code()); }

    /** 与发布、禁用共用身份行锁，保证新调用使用一个确定且启用的版本。 */
    @Override
    @Transactional
    public AgentProfile require(String profileId) {
        var profile = profiles.lock(profileId);
        if (Objects.isNull(profile) || !profile.getEnabled() || Objects.isNull(profile.getActiveVersion())) unavailable();
        var version = profiles.lockVersion(profileId, profile.getActiveVersion());
        if (Objects.isNull(version) || version.getStatus() != AgentProfileVersionStatus.PUBLISHED) unavailable();
        var definition = codec.read(version.getDefinitionJson());
        if (!codec.fingerprint(definition).equals(version.getDefinitionFingerprint())) {
            throw new IllegalStateException(AgentProfileResponseCode.CORRUPTED.message());
        }
        return definition.profile(profileId, version.getVersionNo());
    }

    /** 不回退到资源默认定义，避免管理员禁用被静默绕过。 */
    private void unavailable() { throw new BusinessException(AgentProfileResponseCode.UNAVAILABLE, HttpStatus.CONFLICT); }
}
