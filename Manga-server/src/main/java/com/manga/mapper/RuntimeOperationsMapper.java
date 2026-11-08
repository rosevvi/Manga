package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.ModelCallLedger;
import com.manga.entity.ProviderCallGate;
import com.manga.entity.CreativeRunArchive;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 用量、跨实例门禁、监测和归档的有界查询契约；SQL 全部放在 XML。 */
public interface RuntimeOperationsMapper extends BaseMapper<ModelCallLedger> {
    void ensureGate(@Param("configId") long configId, @Param("actor") String actor);
    ProviderCallGate lockGate(@Param("configId") long configId);
    void saveGate(@Param("gate") ProviderCallGate gate, @Param("actor") String actor);
    int activeCalls(@Param("configId") long configId, @Param("now") LocalDateTime now);
    BigDecimal dailyReserved(@Param("configId") long configId, @Param("start") LocalDateTime start);
    int finish(@Param("call") ModelCallLedger call, @Param("actor") String actor);
    ModelCallLedger lockCall(@Param("callId") String callId);
    int settle(@Param("callId") String callId, @Param("amount") BigDecimal amount, @Param("userId") long userId,
            @Param("note") String note, @Param("now") LocalDateTime now);
    List<ModelCallLedger> stale(@Param("now") LocalDateTime now);
    List<ModelCallLedger> byRun(@Param("runId") String runId, @Param("beforeId") Long beforeId);
    List<Map<String, Object>> overview(@Param("since") LocalDateTime since);
    List<ProviderCallGate> gates();
    List<Map<String, Object>> backlog();
    List<String> archiveCandidates(@Param("before") LocalDateTime before);
    CreativeRunArchive archive(@Param("runId") String runId);
    void insertArchive(CreativeRunArchive archive);
    List<Map<String, Object>> archiveSteps(@Param("runId") String runId, @Param("limit") int limit);
    List<Map<String, Object>> archiveEvents(@Param("runId") String runId, @Param("limit") int limit);
    List<Map<String, Object>> archiveMessages(@Param("runId") String runId, @Param("limit") int limit);
    List<Map<String, Object>> archivePlans(@Param("runId") String runId, @Param("limit") int limit);
    List<Map<String, Object>> archiveChanges(@Param("runId") String runId, @Param("limit") int limit);
    List<Map<String, Object>> archiveApprovals(@Param("runId") String runId, @Param("limit") int limit);
    int activeJobs(@Param("runId") String runId);
    long archivePayloadSize(@Param("runId") String runId);
}
