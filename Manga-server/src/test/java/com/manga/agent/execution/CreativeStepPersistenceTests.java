package com.manga.agent.execution;

import com.manga.agent.application.CreativeEventService;
import com.manga.agent.domain.CreativeEventType;
import com.manga.entity.CreativeStep;
import com.manga.repository.CreativeStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Step 原子领取、owner epoch 围栏和重启后的租约恢复。
 */
@SpringBootTest
@ActiveProfiles("test")
class CreativeStepPersistenceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CreativeStepRepository stepRepository;

    @Autowired
    private CreativeStepRecoveryService recoveryService;

    @Autowired
    private CreativeEventService eventService;

    /** 多线程竞争同一 scheduleVersion 时只允许一个实例领取成功。 */
    @Test
    void shouldAllowOnlyOneConcurrentClaim() throws Exception {
        String runId = insertRun();
        String stepId = insertStep(runId, "READY", null, 0L, 0, 1L);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                String owner = "test-owner-" + index;
                futures.add(executor.submit(() -> stepRepository.claimReady(stepId, 1L, owner,
                        LocalDateTime.now().plusMinutes(1), "test:concurrency")));
            }
            int successfulClaims = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) successfulClaims++;
            }

            CreativeStep claimed = stepRepository.findByStepId(stepId).orElseThrow();
            assertThat(successfulClaims).isEqualTo(1);
            assertThat(claimed.getOwnerEpoch()).isEqualTo(1L);
            assertThat(claimed.getAttemptNo()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 模拟进程重启后恢复过期 RUNNING Step，并建立新调度版本 Outbox。 */
    @Test
    void shouldRecoverExpiredLeaseAfterRestart() {
        String runId = insertRun();
        String stepId = insertStep(runId, "RUNNING", "stopped-instance", 5L, 1, 1L);
        CreativeStep expired = stepRepository.findByStepId(stepId).orElseThrow();

        CreativeDispatchSignal signal = recoveryService.recover(expired, LocalDateTime.now()).orElseThrow();

        CreativeStep recovered = stepRepository.findByStepId(stepId).orElseThrow();
        assertThat(signal.scheduleVersion()).isEqualTo(2L);
        assertThat(recovered.getStatus().name()).isEqualTo("READY");
        assertThat(recovered.getScheduleVersion()).isEqualTo(2L);
        assertThat(recovered.getOwnerInstanceId()).isNull();
        assertThat(stepRepository.completeOwned(stepId, "stopped-instance", 5L, "{}",
                LocalDateTime.now(), "test:stale-owner")).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manga_creative_dispatch_outbox WHERE step_id = ? AND schedule_version = 2",
                Integer.class, stepId)).isEqualTo(1);
    }

    /** 并发事件提交由 Run 行锁串行分配 sequence，不产生重复或空洞。 */
    @Test
    void shouldAllocateContiguousEventSequenceConcurrently() throws Exception {
        String runId = insertRun();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                int value = index;
                futures.add(executor.submit(() -> eventService.append(
                        runId, CreativeEventType.CONTENT_DELTA, java.util.Map.of("index", value))));
            }
            for (Future<?> future : futures) future.get();

            List<Long> sequences = jdbcTemplate.queryForList(
                    "SELECT sequence_no FROM manga_creative_event WHERE run_id = ? ORDER BY sequence_no",
                    Long.class, runId);
            assertThat(sequences).containsExactlyElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, 16).boxed().toList());
        } finally {
            executor.shutdownNow();
        }
    }

    /** 插入不绑定会话的最小测试 Run。 */
    private String insertRun() {
        String runId = UUID.randomUUID().toString();
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM manga_user ORDER BY id ASC LIMIT 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO manga_creative_run (
                    run_id, request_id, request_fingerprint, user_id, trigger_source, operation_type,
                    engine_version, workflow_type, workflow_version, permission_policy, status,
                    deadline_at, next_sequence, created_by, updated_by
                ) VALUES (?, ?, ?, ?, 'CONVERSATION', 'ASSISTANT_CHAT', 'test', 'ASSISTANT_CHAT',
                    'test', 'READ_ONLY', 'QUEUED', ?, 1, 'test:fixture', 'test:fixture')
                """, runId, UUID.randomUUID().toString(), "a".repeat(64), userId,
                LocalDateTime.now().plusMinutes(5));
        return runId;
    }

    /** 插入指定状态和围栏信息的根 Step。 */
    private String insertStep(String runId, String status, String owner, long epoch,
            int attempts, long scheduleVersion) {
        String stepId = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = "RUNNING".equals(status) ? LocalDateTime.now().minusMinutes(1) : null;
        jdbcTemplate.update("""
                INSERT INTO manga_creative_step (
                    step_id, run_id, step_type, status, schedule_version, owner_instance_id,
                    owner_epoch, lease_until, attempt_no, max_attempts, input_json, created_by, updated_by
                ) VALUES (?, ?, 'ROOT_WORKFLOW', ?, ?, ?, ?, ?, ?, 3, '{}', 'test:fixture', 'test:fixture')
                """, stepId, runId, status, scheduleVersion, owner, epoch, leaseUntil, attempts);
        return stepId;
    }
}
