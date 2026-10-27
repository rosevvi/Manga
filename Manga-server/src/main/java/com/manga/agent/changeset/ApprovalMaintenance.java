package com.manga.agent.changeset;

import com.manga.repository.CreativeApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 补偿到期审批；每项独立事务，单个失败不阻塞整批。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalMaintenance {
    private final CreativeApprovalRepository approvals;
    private final ChangeSetApprovalService service;
    private final ApprovalProperties properties;

    /** 定期结束过期等待并投递结构化恢复结果。 */
    @Scheduled(fixedDelayString = ChangeSetConstants.MAINTENANCE_INTERVAL)
    public void expire() {
        for (String id : approvals.findExpired(LocalDateTime.now(), properties.batchSize())) {
            try { service.expire(id); }
            catch (RuntimeException failure) { log.warn("Approval expiry failed changeSetId={}", id, failure); }
        }
    }
}
