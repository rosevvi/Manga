package com.manga.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.agent.generation.GenerationProviderKey;
import com.manga.entity.GenerationJob;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
/** 图片作业自定义 SQL，仅在 XML 中定义。 */
public interface GenerationJobMapper extends BaseMapper<GenerationJob> {
    /** 读取作业元数据，不加载媒体与配置快照。 */
    GenerationJob find(@Param("jobId") String jobId);
    /** 必须先锁 Run，再锁 Job。 */
    GenerationJob lock(@Param("jobId") String jobId);
    /** 有界查找无有效 worker 的到期作业。 */
    List<String> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
    /** 不读取大媒体和配置快照的列表。 */
    List<GenerationJob> findVisible(@Param("userId") long userId, @Param("projectId") long projectId,
            @Param("runId") String runId, @Param("conversationId") String conversationId,
            @Param("beforeId") Long beforeId, @Param("limit") int limit,
            @Param("shotId") Long shotId, @Param("mediaKind") String mediaKind);
    /** 查询需要跟随 Run 撤销采用权的作业。 */
    List<String> findByRun(@Param("runId") String runId);
    /** 串行化供应商并发准入。 */
    String lockCapacity(@Param("provider") GenerationProviderKey provider);
    /** 统计真实在途及未知作业，空用户表示供应商总量。 */
    int countInFlight(@Param("provider") GenerationProviderKey provider, @Param("userId") Long userId);
    /** 带 epoch 的完整生命周期更新，明确清空可空字段。 */
    int updateState(@Param("job") GenerationJob job, @Param("expectedEpoch") long expectedEpoch);
    /** 在审批事务内创建唯一提交意图。 */
    int createSubmission(@Param("jobId") String jobId, @Param("actor") String actor);
    /** 与 Job 同锁推进投递状态，不独立重投未知请求。 */
    int updateSubmission(@Param("jobId") String jobId, @Param("status") String status, @Param("actor") String actor);
}

