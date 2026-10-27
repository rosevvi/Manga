package com.manga.agent.changeset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.manga.dto.ProjectScriptSaveRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Schema v1：新建章节或整体替换指定章节，审批前只保存提案。 */
public record ScriptChangeSet(
        /** 空值创建章节；非空值必须属于当前项目。 */ @Positive Long chapterId,
        /** 审批后保存的章节标题。 */ @NotBlank @Size(max = 120) String title,
        /** 章节简介。 */ @Size(max = 2000) String synopsis,
        /** 完整章节正文，不接受局部字符串替换指令。 */ @NotBlank @Size(max = 200000) String rawContent,
        /** 完整场景和对白列表；空列表表示没有结构化场景。 */
        @NotNull @Size(max = 200) List<@NotNull @Valid ProjectScriptSaveRequest.Scene> scenes
) {
    /** 提交时校验最终场景编号及对白元素，避免审批时才遇到结构约束错误。 */
    @AssertTrue
    @JsonIgnore
    public boolean isStructureValid() {
        if (Objects.isNull(scenes)) return false;
        Set<Integer> numbers = new HashSet<>();
        for (int index = 0; index < scenes.size(); index++) {
            ProjectScriptSaveRequest.Scene scene = scenes.get(index);
            if (Objects.isNull(scene)) return false;
            int number = Objects.requireNonNullElse(scene.sceneNumber(), index + 1);
            if (number <= 0 || !numbers.add(number)) return false;
            if (Objects.nonNull(scene.dialogues()) && scene.dialogues().stream().anyMatch(Objects::isNull)) return false;
        }
        return true;
    }
}
