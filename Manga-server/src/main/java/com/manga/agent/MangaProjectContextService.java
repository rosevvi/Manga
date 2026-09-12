package com.manga.agent;

import com.manga.dto.ProjectScriptResponse;
import com.manga.dto.StoryboardShotResponse;
import com.manga.entity.MangaProject;
import com.manga.service.ProjectScriptService;
import com.manga.service.ProjectService;
import com.manga.service.StoryboardShotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 汇总一个已授权项目的有限创作上下文，避免 Agent 直接访问数据层。 */
@Service
@RequiredArgsConstructor
public class MangaProjectContextService {

    private static final int MAX_SHOTS = 100;

    private final ProjectService projectService;
    private final ProjectScriptService scriptService;
    private final StoryboardShotService storyboardShotService;

    public Map<String, Object> getProjectContext(long userId, long projectId) {
        MangaProject project = projectService.requireAccessibleProject(projectId, userId);
        ProjectScriptResponse script = scriptService.findForUser(projectId, userId);
        List<StoryboardShotResponse> shots = storyboardShotService.findAllForUser(projectId, userId, null).stream()
                .limit(MAX_SHOTS)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", Map.of(
                "id", project.getId(),
                "name", project.getName(),
                "description", project.getDescription() == null ? "" : project.getDescription(),
                "genre", project.getGenre() == null ? "" : project.getGenre(),
                "aspectRatio", project.getAspectRatio(),
                "artStyle", project.getArtStyle() == null ? "" : project.getArtStyle(),
                "artStyleDescription", project.getArtStyleDescription() == null ? "" : project.getArtStyleDescription()
        ));
        result.put("script", script);
        result.put("storyboardShots", shots);
        result.put("storyboardShotCount", shots.size());
        result.put("storyboardShotsTruncated", shots.size() == MAX_SHOTS);
        return result;
    }
}
