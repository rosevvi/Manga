package com.manga.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manga.agent.profile.AgentProfileCatalogService;
import com.manga.agent.profile.AgentProfileKey;
import com.manga.agent.profile.AgentProfileContracts;
import com.manga.agent.profile.AgentProfileResponseCode;
import com.manga.common.exception.BusinessException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Director 在委派前发现管理员已发布的专业 Worker，不猜测配置标识。 */
@Component
public class ListAgentProfilesCapability implements MangaAgentCapability {
    private static final String SCHEMA = """
            {"type":"object","properties":{"role":{"type":"string","enum":["script_parser","script_writer","storyboard_director","continuity_reviewer"]},"afterProfileId":{"type":"string","maxLength":64}},"additionalProperties":false}
            """;
    private final AgentProfileCatalogService catalog;
    private final ObjectMapper mapper;
    private final CapabilityDescriptor descriptor;

    /** 注册有界、只读的专业 Agent 目录工具。 */
    public ListAgentProfilesCapability(AgentProfileCatalogService catalog, ObjectMapper mapper) {
        this.catalog = catalog; this.mapper = mapper;
        descriptor = CapabilityContractSupport.readOnlyDescriptor(mapper, CapabilityName.LIST_AGENT_PROFILES,
                "分页列出当前启用且已发布的专业 Worker，包含 profileId、角色、版本和职责摘要。可按 role 筛选；nextAfterProfileId 非空时继续读取。委派使用返回的 profileId。", SCHEMA, 1);
    }

    /** 返回稳定目录协议。 */
    @Override
    public CapabilityDescriptor descriptor() { return descriptor; }

    /** 不包含 Director 根配置，Worker 仍然不能递归委派。 */
    @Override
    public CapabilityResult execute(CapabilityCommand command, AgentRuntimeContext context) {
        Query query;
        try { query = mapper.readerFor(Query.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(command.input()); }
        catch (IOException | IllegalArgumentException invalid) { throw new BusinessException(AgentProfileResponseCode.INVALID); }
        if (Objects.isNull(query) || (Objects.nonNull(query.afterProfileId())
                && !query.afterProfileId().matches(AgentProfileContracts.KEY_PATTERN))) throw new BusinessException(AgentProfileResponseCode.INVALID);
        var selected = catalog.list().stream().filter(profile -> profile.role() != AgentProfileKey.MANGA_DIRECTOR)
                .filter(profile -> Objects.isNull(query.role()) || profile.role() == query.role())
                .filter(profile -> Objects.isNull(query.afterProfileId()) || profile.profileId().compareTo(query.afterProfileId()) > 0)
                .sorted(Comparator.comparing(profile -> profile.profileId()))
                .limit(AgentProfileContracts.TOOL_CATALOG_PAGE_SIZE + 1).toList();
        var items = selected.stream().limit(AgentProfileContracts.TOOL_CATALOG_PAGE_SIZE).map(profile ->
                new Worker(profile.profileId(), profile.role(), profile.version(), profile.displayName(), abbreviated(profile.description()))).toList();
        String next = selected.size() > AgentProfileContracts.TOOL_CATALOG_PAGE_SIZE ? items.getLast().profileId() : null;
        return new CapabilityResult(mapper.valueToTree(new Page(items, next)));
    }

    /** 目录只提供短职责，避免上下文随管理员配置数无限增长。 */
    private String abbreviated(String text) {
        int limit = AgentProfileContracts.TOOL_DESCRIPTION_CHARACTERS;
        if (text.length() <= limit) return text;
        return text.substring(0, Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit);
    }
    /** 工具分页输入。 */
    public record Query(
            /** 可选角色筛选。 */ AgentProfileKey role,
            /** 上一页返回的最后身份。 */ String afterProfileId
    ) { }
    /** Director 可见的有界专业定义摘要。 */
    public record Worker(
            /** 委派使用的稳定身份。 */ String profileId,
            /** 固定执行角色。 */ AgentProfileKey role,
            /** 发布版本。 */ long version,
            /** 可见名称。 */ String displayName,
            /** 短职责摘要。 */ String description
    ) { }
    /** 当前页与继续读取游标。 */
    public record Page(
            /** 当前页专业定义。 */ List<Worker> items,
            /** 为空表示已经到达末页。 */ String nextAfterProfileId
    ) { }
}
