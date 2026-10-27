package com.manga.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeApproval;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
/** 审批数据访问，状态变化在 Run 锁内提交。 */
public interface CreativeApprovalMapper extends BaseMapper<CreativeApproval> {
    /** 仅保存决定与审批人，开放审批后不改写有效期和创建事实。 */
    int updateDecision(@Param("value") CreativeApproval value);
    /** 按提案读取审批。 */
    CreativeApproval findByChangeSet(@Param("changeSetId") String changeSetId);
    /** 有界扫描过期审批，逐项在新事务中处理。 */
    List<String> findExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);
    /** 批量装配提案对应审批，避免逐行查询。 */
    List<CreativeApproval> findAll(@Param("ids") List<String> ids);
}
