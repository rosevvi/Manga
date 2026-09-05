package com.manga.repository;

import com.manga.entity.ProjectScript;
import com.manga.entity.ProjectScriptDialogue;
import com.manga.entity.ProjectScriptEpisode;
import com.manga.entity.ProjectScriptScene;
import com.manga.mapper.ProjectScriptDialogueMapper;
import com.manga.mapper.ProjectScriptEpisodeMapper;
import com.manga.mapper.ProjectScriptMapper;
import com.manga.mapper.ProjectScriptSceneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 使用 MyBatis-Plus 持久化项目剧本及其结构化内容。 */
@Repository
@RequiredArgsConstructor
public class ProjectScriptRepository {

    private final ProjectScriptMapper scriptMapper;
    private final ProjectScriptEpisodeMapper episodeMapper;
    private final ProjectScriptSceneMapper sceneMapper;
    private final ProjectScriptDialogueMapper dialogueMapper;

    /** 按项目查询剧本。 */
    public Optional<ProjectScript> findByProjectId(long projectId) {
        return Optional.ofNullable(scriptMapper.findByProjectId(projectId));
    }

    /** 保存新剧本。 */
    public ProjectScript create(ProjectScript script) {
        scriptMapper.insert(script);
        return script;
    }

    /** 更新剧本主记录。 */
    public void update(ProjectScript script) {
        scriptMapper.updateByProjectId(script);
    }

    /** 查询剧本分集。 */
    public List<ProjectScriptEpisode> findEpisodes(long scriptId) {
        return episodeMapper.findByScriptId(scriptId);
    }

    /** 查询剧本场景。 */
    public List<ProjectScriptScene> findScenes(long scriptId) {
        return sceneMapper.findByScriptId(scriptId);
    }

    /** 查询剧本对白。 */
    public List<ProjectScriptDialogue> findDialogues(long scriptId) {
        return dialogueMapper.findByScriptId(scriptId);
    }

    /** 创建剧本分集。 */
    public ProjectScriptEpisode createEpisode(ProjectScriptEpisode episode) {
        episodeMapper.insert(episode);
        return episode;
    }

    /** 创建剧本场景。 */
    public ProjectScriptScene createScene(ProjectScriptScene scene) {
        sceneMapper.insert(scene);
        return scene;
    }

    /** 创建剧本对白。 */
    public ProjectScriptDialogue createDialogue(ProjectScriptDialogue dialogue) {
        dialogueMapper.insert(dialogue);
        return dialogue;
    }

    /** 删除剧本全部对白。 */
    public void deleteDialogues(long scriptId) {
        dialogueMapper.deleteByScriptId(scriptId);
    }

    /** 删除剧本全部场景。 */
    public void deleteScenes(long scriptId) {
        sceneMapper.deleteByScriptId(scriptId);
    }

    /** 删除剧本全部分集。 */
    public void deleteEpisodes(long scriptId) {
        episodeMapper.deleteByScriptId(scriptId);
    }

    /** 替换剧本全部结构化内容。 */
    public void replaceStructure(long scriptId, List<ProjectScriptEpisode> episodes,
            List<ProjectScriptScene> scenes, List<ProjectScriptDialogue> dialogues) {
        dialogueMapper.deleteByScriptId(scriptId);
        sceneMapper.deleteByScriptId(scriptId);
        episodeMapper.deleteByScriptId(scriptId);
        episodes.forEach(episodeMapper::insert);
        scenes.forEach(sceneMapper::insert);
        dialogues.forEach(dialogueMapper::insert);
    }
}
