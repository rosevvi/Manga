package com.manga.agent.profile;

import com.manga.repository.AgentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 用户与 Director 的发布目录，不依赖管理服务或能力注册表。 */
@Service
@RequiredArgsConstructor
public class AgentProfileCatalogService {
    private final AgentProfileRepository profiles;
    private final AgentProfileDefinitionCodec codec;

    /** 一次批量查询返回启用的发布版本，不泄露 Prompt 或用户模型配置。 */
    @Transactional(readOnly = true)
    public List<AgentProfileApi.CatalogItem> list() {
        return profiles.activeVersions(AgentProfileContracts.CATALOG_LIMIT).stream().map(version -> {
            var definition = codec.read(version.getDefinitionJson());
            return new AgentProfileApi.CatalogItem(version.getProfileKey(), definition.role(), version.getVersionNo(),
                    definition.displayName(), definition.description(), definition.permissionPolicy(),
                    AgentProfilePolicySupport.rootSelectable(definition.profile(version.getProfileKey(), version.getVersionNo())));
        }).toList();
    }
}
