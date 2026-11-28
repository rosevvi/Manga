package com.manga.repository;

import com.manga.entity.MangaTask;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.manga.entity.MangaTaskUnit;
import com.manga.mapper.MangaTaskMapper;
import com.manga.mapper.MangaTaskUnitMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 使用 MyBatis-Plus 持久化统一后台任务。 */
@Repository
@RequiredArgsConstructor
public class MangaTaskRepository {

    private final MangaTaskMapper taskMapper;
    private final MangaTaskUnitMapper unitMapper;

    public MangaTask create(MangaTask task) { taskMapper.insert(task); return task; }
    public MangaTaskUnit createUnit(MangaTaskUnit unit) { unitMapper.insert(unit); return unit; }
    public Optional<MangaTask> findById(long taskId) { return Optional.ofNullable(taskMapper.selectById(taskId)); }
    public List<MangaTask> findByOwner(long ownerUserId) { return taskMapper.findByOwner(ownerUserId); }
    public List<MangaTask> findActive(long ownerUserId) { return taskMapper.findActive(ownerUserId); }
    public List<MangaTask> findRecoverable() { return taskMapper.findRecoverable(); }
    /** 复查文件中的稳定序号，恢复时沿用先前提交的章节。 */
    public Optional<MangaTaskUnit> findUnit(long taskId, int index) {
        return Optional.ofNullable(unitMapper.selectOne(Wrappers.<MangaTaskUnit>lambdaQuery()
                .eq(MangaTaskUnit::getTaskId, taskId).eq(MangaTaskUnit::getUnitIndex, index)));
    }
    public List<MangaTaskUnit> findUnits(long taskId) { return unitMapper.findByTaskId(taskId); }
    public void update(MangaTask task) { taskMapper.updateProgress(task); }
    public void updateUnit(MangaTaskUnit unit) { unitMapper.updateById(unit); }
}
