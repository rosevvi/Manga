package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeMessageProjection;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

/** 独立投影检查点的创建、加锁、领取与提交 SQL。 */
public interface CreativeMessageProjectionMapper extends BaseMapper<CreativeMessageProjection> {
    /** 幂等补齐新 Run 检查点，初始水位来自兼容的 Run 镜像列。 */
    int ensure(@Param("runId") String runId, @Param("actor") String actor);
    /** 同一 Run 的消息批次只允许一个数据库事务持锁处理。 */
    CreativeMessageProjection lock(@Param("runId") String runId);
    /** 登记本批次所有权，便于拒绝外部尚未到期的领取者。 */
    int claim(@Param("runId") String runId, @Param("owner") String owner,
            @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);
    /** 消息变化和水位在同一事务内提交；完成后释放批次租约。 */
    int advance(@Param("runId") String runId, @Param("sequence") long sequence, @Param("owner") String owner);
}
