package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeRepairDecision;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/** 修订确认单的简单持久化；并发修改统一持有所属 Run 行锁。 */
public interface CreativeRepairDecisionMapper extends BaseMapper<CreativeRepairDecision> {
    /** 按状态与期限索引读取有限个到期确认单。 */
    List<CreativeRepairDecision> expired(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
