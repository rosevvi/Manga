package com.manga.service;

import com.manga.agent.domain.CreativeActor;
import com.manga.agent.generation.GenerationError;
import com.manga.agent.generation.ImageGenerationChange;
import com.manga.agent.generation.ImageGenerationConfigService;
import com.manga.common.enums.ProjectAssetError;
import com.manga.common.enums.ProjectAssetKind;
import com.manga.common.exception.BusinessException;
import com.manga.dto.ProjectAssetContracts.Asset;
import com.manga.dto.ProjectAssetContracts.AssetSummary;
import com.manga.dto.ProjectAssetContracts.CreateAsset;
import com.manga.dto.ProjectAssetContracts.Fields;
import com.manga.dto.ProjectAssetContracts.SaveVisual;
import com.manga.dto.ProjectAssetContracts.ShotAssetImage;
import com.manga.dto.ProjectAssetContracts.Visual;
import com.manga.dto.StoryboardImageSnapshot;
import com.manga.entity.AssetVisual;
import com.manga.entity.AssetVisualReference;
import com.manga.entity.CreativeArtifact;
import com.manga.entity.GenerationMediaObject;
import com.manga.entity.ProjectAsset;
import com.manga.entity.ScriptChapterAssetRef;
import com.manga.repository.GenerationMediaObjectRepository;
import com.manga.repository.ProjectAssetRepository;
import com.manga.repository.StoryboardShotRepository;
import com.manga.service.storage.GeneratedMediaFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理章内资产和图片项，图片文件由生成产物统一保存。 */
@Service
@RequiredArgsConstructor
public class ProjectAssetService {
    private final ProjectAssetRepository repository;
    private final ProjectService projects;
    private final ProjectRevisionService revisions;
    private final ImageGenerationConfigService imageConfigs;
    private final MediaStorageService storage;
    private final GenerationMediaObjectRepository mediaObjects;
    private final StoryboardShotRepository shots;
    private final StoryboardShotService storyboard;

    /** 查询项目级目录，供不同章节选择同一资产。 */
    @Transactional(readOnly = true)
    public List<AssetSummary> catalog(long projectId, long userId) {
        projects.requireAccessibleProject(projectId, userId);
        return repository.findProject(projectId).stream()
                .map(asset -> new AssetSummary(asset.getId(), asset.getAssetKind(), asset.getDisplayName())).toList();
    }

    /** 批量读取章内信息、图片和参考图，按当前产物生成预览地址。 */
    @Transactional(readOnly = true)
    public List<Asset> chapter(long projectId, long chapterId, long userId) {
        projects.requireAccessibleProject(projectId, userId);
        revisions.chapter(projectId, chapterId);
        List<ProjectAsset> assets = repository.findChapter(projectId, chapterId);
        List<AssetVisual> visuals = repository.findVisuals(assets.stream().map(ProjectAsset::getId).toList());
        List<Long> visualIds = visuals.stream().map(AssetVisual::getId).toList();
        var refs = repository.findReferences(visualIds).stream().collect(Collectors.groupingBy(
                AssetVisualReference::getVisualId, Collectors.mapping(AssetVisualReference::getReferenceVisualId, Collectors.toList())));
        var artifacts = artifactMap(visuals.stream().map(AssetVisual::getCurrentArtifactId).filter(Objects::nonNull).distinct().toList());
        var busy = Set.copyOf(repository.busyVisualIds(projectId, visualIds));
        var groups = visuals.stream().collect(Collectors.groupingBy(AssetVisual::getProjectAssetId,
                Collectors.mapping(visual -> visualView(visual, refs.getOrDefault(visual.getId(), List.of()),
                        artifacts.get(visual.getCurrentArtifactId()), busy.contains(visual.getId())), Collectors.toList())));
        return assets.stream().map(asset -> new Asset(asset.getId(), asset.getAssetKind(), fields(asset),
                asset.getPrimaryVisualId(), groups.getOrDefault(asset.getId(), List.of()))).toList();
    }

    /** 可在同一事务中新建资产并加入当前章节。 */
    @Transactional
    public long create(long projectId, long userId, CreateAsset request) {
        revisions.lock(projectId, userId);
        if (request.chapterId() != null) revisions.chapter(projectId, request.chapterId());
        ProjectAsset asset = new ProjectAsset();
        asset.setProjectId(projectId);
        asset.setAssetKind(request.assetKind());
        asset.setCreatedBy(CreativeActor.user(userId));
        applyFields(asset, request.fields(), userId);
        repository.insertAsset(asset);
        if (request.chapterId() != null) link(request.chapterId(), asset.getId(), userId);
        return asset.getId();
    }

    /** 更新一份项目资产，所有引用它的章节看到相同资料。 */
    @Transactional
    public void edit(long projectId, long assetId, long userId, Fields fields) {
        revisions.lock(projectId, userId);
        ProjectAsset asset = requireAsset(projectId, assetId);
        applyFields(asset, fields, userId);
        repository.updateAsset(asset);
    }

    /** 挂接与移出均为幂等操作，范围由项目和章节共同约束。 */
    @Transactional
    public void setChapterLink(long projectId, long chapterId, long assetId, long userId, boolean present) {
        revisions.lock(projectId, userId);
        revisions.chapter(projectId, chapterId);
        requireAsset(projectId, assetId);
        if (!present) repository.unlink(chapterId, assetId);
        else if (!repository.linked(chapterId, assetId)) link(chapterId, assetId, userId);
    }

    /** 同时保存图片设置和有序参考关系，现有图片产物由任务发布。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long saveVisual(long projectId, long assetId, Long visualId, long userId, SaveVisual request) {
        projects.requireOwnedProject(projectId, userId);
        ProjectAsset asset = requireAsset(projectId, assetId);
        if (request.visualKind().getAssetKind() != asset.getAssetKind()) throw new BusinessException(ProjectAssetError.INVALID_VISUAL);
        AssetVisual visual = visualId == null ? new AssetVisual() : requireLockedVisual(assetId, visualId);
        List<Long> refs = Objects.requireNonNullElse(request.referenceVisualIds(), List.of());
        validateReferences(visual, assetId, refs);
        String actor = CreativeActor.user(userId);
        visual.setProjectAssetId(assetId);
        visual.setVisualKind(request.visualKind());
        visual.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName().trim() : request.visualKind().getDisplayName());
        visual.setPrompt(value(request.prompt()));
        visual.setNegativePrompt(value(request.negativePrompt()));
        visual.setImageWidth(request.imageWidth());
        visual.setImageHeight(request.imageHeight());
        visual.setProvider(request.provider());
        visual.setModelConfigId(request.modelConfigId());
        visual.setModelName(value(request.modelName()));
        visual.setUpdatedBy(actor);
        if (visualId == null) {
            visual.setCreatedBy(actor);
            repository.insertVisual(visual);
        } else {
            requireIdle(projectId, visualId);
            repository.updateVisual(visual);
        }
        repository.replaceReferences(visual.getId(), referenceRows(visual.getId(), refs, actor));
        return visual.getId();
    }

    /** 基准图指向稳定图片项，发布替换后自然使用新产物。 */
    @Transactional
    public void setPrimary(long projectId, long assetId, long visualId, long userId) {
        revisions.lock(projectId, userId);
        ProjectAsset asset = requireAsset(projectId, assetId);
        AssetVisual visual = requireLockedVisual(assetId, visualId);
        if (visual.getCurrentArtifactId() == null) throw new BusinessException(ProjectAssetError.NO_ARTIFACT);
        asset.setPrimaryVisualId(visualId);
        asset.setUpdatedBy(CreativeActor.user(userId));
        repository.updateAsset(asset);
    }

    /** 读取采用前的分镜缩略图和并发检查所需的项目修订号。 */
    public StoryboardImageSnapshot shotImageContext(long projectId, long shotId, long userId) {
        return storyboard.imageSnapshot(projectId, shotId, userId);
    }

    /** 将章内资产的当前图采用为镜头参考图，镜头记录保存具体产物 ID。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void adoptForShot(long projectId, long shotId, long userId, ShotAssetImage request) {
        revisions.lock(projectId, userId);
        var shot = shots.findByProjectAndId(projectId, shotId).orElseThrow(ProjectAssetService::notFound);
        AssetVisual visual = repository.lockVisual(request.visualId()).orElseThrow(ProjectAssetService::notFound);
        requireAsset(projectId, visual.getProjectAssetId());
        if (shot.getChapterId() == null || !repository.linked(shot.getChapterId(), visual.getProjectAssetId())) {
            throw new BusinessException(ProjectAssetError.NOT_IN_CHAPTER);
        }
        if (!Objects.equals(visual.getCurrentArtifactId(), request.artifactId())) {
            throw new BusinessException(ProjectAssetError.VISUAL_REPLACED, HttpStatus.CONFLICT);
        }
        CreativeArtifact artifact = repository.findArtifacts(List.of(request.artifactId())).stream()
                .filter(item -> item.getProjectId() == projectId && GeneratedMediaFormat.PNG.mimeType().equals(item.getMediaType()))
                .findFirst().orElseThrow(ProjectAssetService::notFound);
        storyboard.adoptGeneratedImage(projectId, shotId, userId, request.expectedProjectRevision(),
                artifact.getId(), artifact.getUrl(), CreativeActor.user(userId));
    }

    /** 隔离同一个图片项的并发受理和草稿编辑，权限与项目修订检查沿用创作入口。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void admitGeneration(long projectId, long visualId, long userId) {
        revisions.lock(projectId, userId);
        AssetVisual visual = repository.lockVisual(visualId).orElseThrow(ProjectAssetService::notFound);
        requireAsset(projectId, visual.getProjectAssetId());
        requireIdle(projectId, visualId);
        generationSettings(projectId, visualId, userId);
    }

    /** 使用既有图片参数结构生成提案；资产归属记录在 Run 和 Job 的目标列。 */
    @Transactional(readOnly = true)
    public ImageGenerationChange generationSettings(long projectId, long visualId, long userId) {
        AssetVisual visual = requireVisual(projectId, visualId);
        if (visual.getProvider() == null || !StringUtils.hasText(visual.getModelName())
                || !StringUtils.hasText(visual.getPrompt()) || visual.getImageWidth() == null || visual.getImageHeight() == null) {
            throw new BusinessException(ProjectAssetError.INVALID_VISUAL);
        }
        var settings = imageConfigs.canonicalize(new ImageGenerationChange(null, visual.getProvider(), visual.getModelConfigId(),
                visual.getModelName(), positiveAndNegative(visual), visual.getImageWidth(), visual.getImageHeight(), null), userId);
        if (!repository.findReferences(List.of(visualId)).isEmpty()
                && !imageConfigs.supportsReferences(settings.provider(), settings.configSnapshot())) {
            throw new BusinessException(GenerationError.REFERENCES_UNSUPPORTED);
        }
        return settings;
    }

    /** 提交模型前解析已选图片项的最新文件，保留选择顺序。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<GenerationMediaObject> currentReferenceMedia(long projectId, long visualId) {
        AssetVisual visual = requireVisual(projectId, visualId);
        List<Long> ids = repository.findReferences(List.of(visualId)).stream().map(AssetVisualReference::getReferenceVisualId).toList();
        var refs = validateReferences(visual, visual.getProjectAssetId(), ids);
        Map<Long, CreativeArtifact> artifacts = artifactMap(refs.values().stream().map(AssetVisual::getCurrentArtifactId).distinct().toList());
        var mediaIds = artifacts.values().stream().map(CreativeArtifact::getMediaObjectId).filter(Objects::nonNull).distinct().toList();
        var objects = mediaObjects.findAll(mediaIds).stream().collect(Collectors.toMap(GenerationMediaObject::getId, Function.identity()));
        return ids.stream().map(id -> {
            CreativeArtifact artifact = artifacts.get(refs.get(id).getCurrentArtifactId());
            if (artifact == null || artifact.getProjectId() != projectId || !GeneratedMediaFormat.PNG.mimeType().equals(artifact.getMediaType())
                    || artifact.getMediaObjectId() == null || !objects.containsKey(artifact.getMediaObjectId())) {
                throw new BusinessException(ProjectAssetError.INVALID_REFERENCE);
            }
            return objects.get(artifact.getMediaObjectId());
        }).toList();
    }

    /** 生成成功事务里替换当前指针。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCurrent(long projectId, long visualId, long artifactId, String actor) {
        AssetVisual visual = repository.lockVisual(visualId).orElseThrow(ProjectAssetService::notFound);
        requireAsset(projectId, visual.getProjectAssetId());
        repository.publishVisual(visualId, artifactId, actor);
    }

    private AssetVisual requireVisual(long projectId, long visualId) {
        AssetVisual visual = repository.findVisual(visualId).orElseThrow(ProjectAssetService::notFound);
        requireAsset(projectId, visual.getProjectAssetId());
        return visual;
    }

    private AssetVisual requireLockedVisual(long assetId, long visualId) {
        return repository.lockVisual(visualId).filter(visual -> visual.getProjectAssetId() == assetId)
                .orElseThrow(ProjectAssetService::notFound);
    }

    private ProjectAsset requireAsset(long projectId, long assetId) {
        return repository.findAsset(projectId, assetId).orElseThrow(ProjectAssetService::notFound);
    }

    private Map<Long, AssetVisual> validateReferences(AssetVisual owner, long assetId, List<Long> ids) {
        var refs = repository.findVisualIds(ids).stream().filter(ref -> ref.getProjectAssetId() == assetId
                && !ref.getId().equals(owner.getId()) && ref.getCurrentArtifactId() != null)
                .collect(Collectors.toMap(AssetVisual::getId, Function.identity()));
        if (refs.size() != ids.size()) throw new BusinessException(ProjectAssetError.INVALID_REFERENCE);
        return refs;
    }

    private void requireIdle(long projectId, long visualId) {
        if (!repository.busyVisualIds(projectId, List.of(visualId)).isEmpty()) {
            throw new BusinessException(ProjectAssetError.GENERATING, HttpStatus.CONFLICT);
        }
    }

    private List<AssetVisualReference> referenceRows(long visualId, List<Long> ids, String actor) {
        List<AssetVisualReference> rows = new ArrayList<>(ids.size());
        for (int index = 0; index < ids.size(); index++) {
            var row = new AssetVisualReference();
            row.setVisualId(visualId);
            row.setReferenceVisualId(ids.get(index));
            row.setDisplayOrder(index);
            row.setCreatedBy(actor);
            row.setUpdatedBy(actor);
            rows.add(row);
        }
        return rows;
    }

    private void link(long chapterId, long assetId, long userId) {
        var relation = new ScriptChapterAssetRef();
        relation.setChapterId(chapterId);
        relation.setProjectAssetId(assetId);
        relation.setCreatedBy(CreativeActor.user(userId));
        relation.setUpdatedBy(relation.getCreatedBy());
        repository.link(relation);
    }

    private Map<Long, CreativeArtifact> artifactMap(List<Long> ids) {
        return repository.findArtifacts(ids).stream().collect(Collectors.toMap(CreativeArtifact::getId, Function.identity()));
    }

    private Visual visualView(AssetVisual visual, List<Long> refs, CreativeArtifact artifact, boolean busy) {
        return new Visual(visual.getId(), visual.getVisualKind(), visual.getDisplayName(), visual.getPrompt(), visual.getNegativePrompt(),
                visual.getImageWidth(), visual.getImageHeight(), visual.getProvider(), visual.getModelConfigId(), visual.getModelName(), refs,
                visual.getCurrentArtifactId(), artifact == null ? null : storage.previewGeneratedMedia(artifact.getUrl()),
                artifact == null ? null : artifact.getCreatedAt(), busy);
    }

    private Fields fields(ProjectAsset asset) {
        return new Fields(asset.getDisplayName(), asset.getOverview(), asset.getRoleLabel(), asset.getIdentityDescription(),
                asset.getAgeGroup(), asset.getAppearanceDescription(), asset.getCostumeDescription(), asset.getLocationDescription(),
                asset.getEnvironmentDescription(), asset.getLightingDescription(), asset.getMaterialDescription());
    }

    private void applyFields(ProjectAsset asset, Fields fields, long userId) {
        boolean character = asset.getAssetKind() == ProjectAssetKind.CHARACTER;
        boolean scene = asset.getAssetKind() == ProjectAssetKind.SCENE;
        asset.setDisplayName(fields.displayName().trim());
        asset.setOverview(value(fields.overview()));
        asset.setRoleLabel(character ? value(fields.roleLabel()) : "");
        asset.setIdentityDescription(character ? value(fields.identityDescription()) : "");
        asset.setAgeGroup(character ? value(fields.ageGroup()) : "");
        asset.setAppearanceDescription(character ? value(fields.appearanceDescription()) : "");
        asset.setCostumeDescription(character ? value(fields.costumeDescription()) : "");
        asset.setLocationDescription(scene ? value(fields.locationDescription()) : "");
        asset.setEnvironmentDescription(scene ? value(fields.environmentDescription()) : "");
        asset.setLightingDescription(scene ? value(fields.lightingDescription()) : "");
        asset.setMaterialDescription(asset.getAssetKind() == ProjectAssetKind.PROP ? value(fields.materialDescription()) : "");
        asset.setUpdatedBy(CreativeActor.user(userId));
    }

    private String positiveAndNegative(AssetVisual visual) {
        return StringUtils.hasText(visual.getNegativePrompt())
                ? visual.getPrompt() + "\n\n避免出现：" + visual.getNegativePrompt() : visual.getPrompt();
    }

    private static String value(String text) { return text == null ? "" : text.trim(); }

    private static BusinessException notFound() { return new BusinessException(ProjectAssetError.NOT_FOUND, HttpStatus.NOT_FOUND); }
}
