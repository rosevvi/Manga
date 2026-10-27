package com.manga.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeChangeSet;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/** 查询不可变提案；并发修改统一受所属 Run 行锁保护。 */
public interface CreativeChangeSetMapper extends BaseMapper<CreativeChangeSet> {
    /** 仅更新状态和结果，冻结的提案正文、原文和基础版本不可被状态更新改写。 */
    int updateState(@Param("value") CreativeChangeSet value);
    /** 查询一个步骤的唯一提案。 */
    CreativeChangeSet findByStep(@Param("stepId") String stepId);
    /** 查询 Run 下全部提案用于历史预览。 */
    List<CreativeChangeSet> findByRun(@Param("runId") String runId);
    /** 按认证用户与运行或会话范围分页查询，不返回执行中的草稿。 */
    List<CreativeChangeSet> findVisible(@Param("userId") long userId, @Param("runId") String runId,
            @Param("conversationId") String conversationId, @Param("beforeId") Long beforeId, @Param("limit") int limit);
}
