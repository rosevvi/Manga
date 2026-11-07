package com.manga.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.agent.generation.GenerationProviderKey;
import com.manga.agent.generation.CallbackInboxStatus;
import com.manga.entity.GenerationCallbackInbox;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.time.LocalDateTime;
/** 回调去重与待处理事件 SQL，仅由 XML 实现。 */
public interface GenerationCallbackMapper extends BaseMapper<GenerationCallbackInbox> {
    /** 插入幂等事件，重复事件不修改原始摘要。 */
    int receive(@Param("provider") GenerationProviderKey provider, @Param("event") GenerationCallbackEvent event, @Param("actor") String actor);
    /** 确认同一事件 ID 没有更换正文。 */
    String payloadHash(@Param("provider") GenerationProviderKey provider, @Param("eventId") String eventId);
    /** 有界扫描支持回调先于提交结果持久化到达。 */
    List<GenerationCallbackInbox> pending(@Param("now") LocalDateTime now, @Param("limit") int limit);
    /** 未绑定事件退避，释放扫描批次给后续回调。 */
    int defer(@Param("id") long id, @Param("now") LocalDateTime now,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("actor") String actor);
    /** 在作业事务中标记已处理，条件更新防止重复消费。 */
    int complete(@Param("id") long id, @Param("status") CallbackInboxStatus status, @Param("actor") String actor);
    /** 不含原始正文的签名验证结果。 */
    record GenerationCallbackEvent(
            /** 回调事件 ID。 */ String eventId,
            /** 供应商作业 ID。 */ String providerJobId,
            /** 已验签原始正文摘要。 */ String payloadSha256
    ) { }
}
