package com.manga.repository;

import com.manga.entity.CreativeMessage;
import com.manga.mapper.CreativeMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 封装消息投影的分页和幂等持久化。 */
@Repository
@RequiredArgsConstructor
public class CreativeMessageRepository {
    private final CreativeMessageMapper mapper;
    /** 查询逻辑消息的当前投影。 */
    public Optional<CreativeMessage> find(String key) {
        return Optional.ofNullable(mapper.findByProjectionKey(key));
    }
    /** 新增或更新已经在 Run 行锁内定位的消息。 */
    public void save(CreativeMessage message) {
        if (Objects.isNull(message.getId())) { mapper.insert(message); }
        else { mapper.updateById(message); }
    }
    /** 返回按主键倒序排列的一页消息。 */
    public List<CreativeMessage> history(String conversationId, Long beforeId, int limit) {
        return mapper.history(conversationId, beforeId, limit);
    }
    /** 返回尚未纳入摘要的已完成消息。 */
    public List<CreativeMessage> completedAfter(String conversationId, long afterId, int limit) {
        return mapper.completedAfter(conversationId, afterId, limit);
    }
    /** 为本轮所有消息统一提交终态。 */
    public void finishRun(String runId, String status, String toolStatus, long sequence, String actor) {
        mapper.finishRun(runId, status, toolStatus, sequence, actor);
    }
    /** 删除派生消息，供从事件恢复使用。 */
    public void deleteByRun(String runId) {
        mapper.deleteByRun(runId);
    }
}
