package com.manga.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.manga.entity.AssetVisual;
import com.manga.entity.AssetVisualReference;
import com.manga.entity.CreativeArtifact;
import com.manga.entity.ProjectAsset;
import com.manga.entity.ScriptChapterAssetRef;
import com.manga.mapper.AssetVisualMapper;
import com.manga.mapper.AssetVisualReferenceMapper;
import com.manga.mapper.CreativeArtifactMapper;
import com.manga.mapper.ProjectAssetMapper;
import com.manga.mapper.ScriptChapterAssetRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 批量读取资产、图片项和引用，所有领域写入由服务统一控制。 */
@Repository
@RequiredArgsConstructor
public class ProjectAssetRepository {
    private final ProjectAssetMapper assets;
    private final ScriptChapterAssetRefMapper chapters;
    private final AssetVisualMapper visuals;
    private final AssetVisualReferenceMapper references;
    private final CreativeArtifactMapper artifacts;

    public Optional<ProjectAsset> findAsset(long projectId, long assetId) {
        return Optional.ofNullable(assets.selectOne(Wrappers.<ProjectAsset>lambdaQuery()
                .eq(ProjectAsset::getProjectId, projectId).eq(ProjectAsset::getId, assetId)));
    }

    public List<ProjectAsset> findProject(long projectId) {
        return assets.selectList(Wrappers.<ProjectAsset>lambdaQuery()
                .eq(ProjectAsset::getProjectId, projectId).orderByAsc(ProjectAsset::getId));
    }

    public List<ProjectAsset> findChapter(long projectId, long chapterId) {
        var ids = chapters.selectList(Wrappers.<ScriptChapterAssetRef>lambdaQuery()
                .eq(ScriptChapterAssetRef::getChapterId, chapterId)).stream().map(ScriptChapterAssetRef::getProjectAssetId).toList();
        return ids.isEmpty() ? List.of() : assets.selectList(Wrappers.<ProjectAsset>lambdaQuery()
                .eq(ProjectAsset::getProjectId, projectId).in(ProjectAsset::getId, ids).orderByAsc(ProjectAsset::getId));
    }

    public void insertAsset(ProjectAsset asset) { assets.insert(asset); }
    public void updateAsset(ProjectAsset asset) { assets.updateById(asset); }

    public boolean linked(long chapterId, long assetId) { return chapters.exists(chapterLink(chapterId, assetId)); }
    public void link(ScriptChapterAssetRef relation) { chapters.insert(relation); }
    public void unlink(long chapterId, long assetId) { chapters.delete(chapterLink(chapterId, assetId)); }

    private LambdaQueryWrapper<ScriptChapterAssetRef> chapterLink(long chapterId, long assetId) {
        return Wrappers.<ScriptChapterAssetRef>lambdaQuery().eq(ScriptChapterAssetRef::getChapterId, chapterId)
                .eq(ScriptChapterAssetRef::getProjectAssetId, assetId);
    }

    public List<AssetVisual> findVisuals(Collection<Long> assetIds) {
        return assetIds.isEmpty() ? List.of() : visuals.selectList(Wrappers.<AssetVisual>lambdaQuery()
                .in(AssetVisual::getProjectAssetId, assetIds).orderByAsc(AssetVisual::getId));
    }

    public List<AssetVisual> findVisualIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : visuals.selectByIds(ids);
    }

    public Optional<AssetVisual> findVisual(long id) { return Optional.ofNullable(visuals.selectById(id)); }
    public Optional<AssetVisual> lockVisual(long id) { return Optional.ofNullable(visuals.lock(id)); }
    public List<Long> busyVisualIds(long projectId, Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : visuals.busyVisualIds(projectId, ids);
    }
    public void insertVisual(AssetVisual visual) { visuals.insert(visual); }
    public void updateVisual(AssetVisual visual) { visuals.updateById(visual); }
    public void publishVisual(long visualId, long artifactId, String actor) {
        visuals.update(null, Wrappers.<AssetVisual>lambdaUpdate().eq(AssetVisual::getId, visualId)
                .set(AssetVisual::getCurrentArtifactId, artifactId).set(AssetVisual::getUpdatedBy, actor));
    }

    public List<AssetVisualReference> findReferences(Collection<Long> visualIds) {
        return visualIds.isEmpty() ? List.of() : references.selectList(Wrappers.<AssetVisualReference>lambdaQuery()
                .in(AssetVisualReference::getVisualId, visualIds).orderByAsc(AssetVisualReference::getDisplayOrder));
    }

    /** 调用者持有图片项行锁，成组替换选择及顺序。 */
    public void replaceReferences(long visualId, List<AssetVisualReference> choices) {
        references.delete(Wrappers.<AssetVisualReference>lambdaQuery().eq(AssetVisualReference::getVisualId, visualId));
        if (!choices.isEmpty()) references.insert(choices);
    }

    public List<CreativeArtifact> findArtifacts(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : artifacts.selectByIds(ids);
    }
}
