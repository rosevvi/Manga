package com.manga.agent.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.manga.agent.capability.CapabilityName;
import com.manga.agent.domain.AgentPermissionPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Agent 配置管理、发布与沙箱测试的 HTTP 契约。 */
public final class AgentProfileApi {
    /** 创建新的专业 Agent，不立即开放给普通运行。 */
    public record Create(
            /** 稳定配置标识。 */ @NotBlank @Pattern(regexp = AgentProfileContracts.KEY_PATTERN) String profileId,
            /** 由严格 Codec 校验的定义。 */ @NotNull JsonNode definition
    ) { }
    /** 新建或修改草稿需要携带聚合版本。 */
    public record Draft(
            /** 页面读取的聚合版本。 */ @Min(0) long expectedRevision,
            /** 新的版本内容。 */ @NotNull JsonNode definition
    ) { }
    /** 发布和回滚只改变指针，不允许请求夹带配置内容。 */
    public record Release(
            /** 页面读取的聚合版本。 */ @Min(0) long expectedRevision,
            /** 操作原因，进入追加式审计。 */ @Size(max = 1000) String comment
    ) { }
    /** 配置启停与版本发布相互独立。 */
    public record Enable(
            /** 页面读取的聚合版本。 */ @Min(0) long expectedRevision,
            /** 是否允许新的运行选择。 */ @NotNull Boolean enabled,
            /** 启停原因。 */ @Size(max = 1000) String comment
    ) { }
    /** 测试使用调用管理员自己的模型与项目权限，不接受任意连接地址。 */
    public record Test(
            /** 幂等启动标识。 */ @NotBlank @Size(max = 64) String requestId,
            /** 可选可访问项目，为空时不读取业务内容。 */ @Positive Long projectId,
            /** 管理员自己的模型配置，为空使用默认配置。 */ @Positive Long modelConfigId,
            /** 测试问题。 */ @NotBlank @Size(max = 12000) String instruction,
            /** 页面预览的定义指纹，防止测试期间草稿被他人替换。 */ @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String definitionFingerprint
    ) { }
    /** 管理身份元数据，内容版本通过独立接口读取。 */
    public record Summary(
            /** 稳定配置身份。 */ String profileId,
            /** 是否为内置身份。 */ boolean builtIn,
            /** 是否允许新调用。 */ boolean enabled,
            /** 当前发布版本。 */ Long activeVersion,
            /** 最后分配的版本。 */ long lastVersion,
            /** 并发编辑版本。 */ long revision,
            /** 最近变更时间。 */ LocalDateTime updatedAt,
            /** 最近操作者。 */ String updatedBy
    ) { }
    /** 已读取的配置版本和校验指纹。 */
    public record Version(
            /** 配置身份。 */ String profileId,
            /** 版本号。 */ long version,
            /** 草稿或不可变发布状态。 */ AgentProfileVersionStatus status,
            /** 完整纯数据定义，仅管理员可见。 */ AgentProfileDefinition definition,
            /** 规范化内容指纹。 */ String fingerprint,
            /** 首次发布时间。 */ LocalDateTime publishedAt,
            /** 首次发布人。 */ String publishedBy,
            /** 最近更新人。 */ String updatedBy
    ) { }
    /** 聚合详情不一次返回所有历史 Prompt。 */
    public record Detail(
            /** 当前身份和 revision。 */ Summary profile,
            /** 所有合法版本的有界摘要。 */ List<VersionSummary> versions
    ) { }
    /** 版本选择器的有界投影。 */
    public record VersionSummary(
            /** 版本号。 */ long version,
            /** 发布状态。 */ AgentProfileVersionStatus status,
            /** 内容指纹。 */ String fingerprint,
            /** 首次发布时间。 */ LocalDateTime publishedAt,
            /** 最近操作者。 */ String updatedBy
    ) { }
    /** 普通用户与 Director 可见目录，不包含 Prompt 和模型凭据。 */
    public record CatalogItem(
            /** 数据库稳定身份。 */ String profileId,
            /** 执行角色族。 */ AgentProfileKey role,
            /** 当前发布版本。 */ long version,
            /** 可见名称。 */ String displayName,
            /** 职责说明。 */ String description,
            /** 业务权限上限。 */ AgentPermissionPolicy permissionPolicy,
            /** 是否支持会话根调用。 */ boolean rootSelectable
    ) { }
    /** 管理页面可选择的能力说明。 */
    public record CapabilityOption(
            /** 稳定工具名。 */ CapabilityName name,
            /** 业务说明。 */ String description,
            /** 最低权限。 */ AgentPermissionPolicy requiredPermission,
            /** 输入 schema 指纹。 */ String schemaFingerprint
    ) { }
    /** 审计页面游标条目。 */
    public record Audit(
            /** 页游标。 */ long id,
            /** 相关版本。 */ Long version,
            /** 操作种类。 */ AgentProfileAction action,
            /** 原发布指针。 */ Long previousActiveVersion,
            /** 新发布指针。 */ Long activeVersion,
            /** 操作后的聚合版本。 */ long revision,
            /** 操作原因。 */ String comment,
            /** 操作时间。 */ LocalDateTime at,
            /** 操作人。 */ String actor
    ) { }
    /** 编译预览不调用模型，也不返回模型密钥或完整连接配置。 */
    public record Preview(
            /** 配置身份。 */ String profileId,
            /** 测试的精确版本。 */ long version,
            /** 冻结 Blueprint / Kernel 指纹。 */ String fingerprint,
            /** 沙箱实际工具子集。 */ Set<CapabilityName> capabilities,
            /** 实际模型名称。 */ String model,
            /** 实际最大推理轮数。 */ int maxTurns,
            /** 固定只读测试预算。 */ AgentBudgetLimits budget
    ) { }
    private AgentProfileApi() { }
}
