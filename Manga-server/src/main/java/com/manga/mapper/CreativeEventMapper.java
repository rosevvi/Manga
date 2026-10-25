package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeEvent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提供 Creative Event 的追加持久化与游标查询。
 */
public interface CreativeEventMapper extends BaseMapper<CreativeEvent> {
    /** 有界读取游标后的事件，避免投影加载整个长会话。 */
    List<CreativeEvent> findBatchAfter(@Param("runId") String runId,
            @Param("afterSequence") long afterSequence, @Param("limit") int limit);

    /** 查询指定 Run 游标之后的已提交事件。 */
    List<CreativeEvent> findAfter(@Param("runId") String runId,
            @Param("afterSequence") long afterSequence);
}
