package com.manga.repository;
import com.manga.agent.generation.GenerationError;
import com.manga.entity.GenerationMediaObject;
import com.manga.mapper.GenerationMediaObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/** 只负责登记对象的数据访问，不执行存储 IO。 */
@Repository
@RequiredArgsConstructor
public class GenerationMediaObjectRepository {
    private final GenerationMediaObjectMapper mapper;
    /** 查询对象元数据。 */
    public Optional<GenerationMediaObject> find(long id) { return Optional.ofNullable(mapper.selectById(id)); }
    /** 必须遵守 Run→Job→Object 锁顺序。 */
    public GenerationMediaObject lock(long id) { return mapper.lock(id); }
    /** 在写文件前登记回收责任。 */
    public GenerationMediaObject create(GenerationMediaObject object) { mapper.insert(object); return object; }
    /** 有界扫描，按 nextAttemptAt 公平分配检查机会。 */
    public List<Long> due(LocalDateTime now, int limit) { return mapper.due(now, limit); }
    /** 已存在 Artifact 的对象绝不回收。 */
    public boolean referenced(long id) { return mapper.references(id) > 0; }
    /** 对象一旦发布就永久退出自动回收范围。 */
    public void publish(long id, String actor) {
        if (mapper.publish(id, actor) != 1) throw new IllegalStateException(GenerationError.STATE.message());
    }
    /** 调用者持有对象锁并完成资格检查。 */
    public void claim(long id, String token, LocalDateTime until, String actor) { mapper.claim(id, token, until, actor); }
    /** 活动对象释放本轮扫描机会。 */
    public void defer(long id, LocalDateTime next, String actor) { mapper.defer(id, next, actor); }
    /** 回收网络调用结束后仅由本轮令牌推进，返回是否仍持有回收权。 */
    public boolean finish(long id, String token, boolean deleted, LocalDateTime next, String actor) {
        return mapper.finish(id, token, deleted, next, actor) == 1;
    }
}

