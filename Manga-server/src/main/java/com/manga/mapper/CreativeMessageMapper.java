package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeMessage;
import org.apache.ibatis.annotations.Param;
import java.util.List;
/** 访问消息投影，所有业务 SQL 均在 Mapper XML 中。 */
public interface CreativeMessageMapper extends BaseMapper<CreativeMessage> {
    /** 按逻辑投影键定位可重复更新的消息。 */
    CreativeMessage findByProjectionKey(@Param("key") String key);
    /** 按主键向前分页读取会话消息。 */
    List<CreativeMessage> history(@Param("conversationId") String conversationId, @Param("beforeId") Long beforeId, @Param("limit") int limit);
    /** 读取摘要水位后的已成功完成对话，排除工具和失败轮次。 */
    List<CreativeMessage> completedAfter(@Param("conversationId") String conversationId, @Param("afterId") long afterId, @Param("limit") int limit);
    /** 将本轮所有消息和未结束工具收敛为运行终态。 */
    int finishRun(@Param("runId") String runId, @Param("status") String status, @Param("toolStatus") String toolStatus, @Param("sequence") long sequence, @Param("actor") String actor);
    /** 删除一个 Run 的派生消息，供事务内重建使用。 */
    int deleteByRun(@Param("runId") String runId);
}
