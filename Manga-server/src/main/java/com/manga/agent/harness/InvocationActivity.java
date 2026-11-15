package com.manga.agent.harness;

import com.manga.agent.capability.CapabilityResult;
import com.manga.agent.domain.CreativeErrorCode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** 仅属于一次 Harness 的进展计数和有界读取缓存，不跨用户、代次或恢复调用共享。 */
public final class InvocationActivity {
    /** 未使用配置的独立单元调用沿用同一技术默认值。 */ public static final int DEFAULT_MAX_NO_PROGRESS_TURNS = 3;
    /** 每次执行最多保留的读取结果项数。 */ private static final int MAX_CACHE_ENTRIES = 256;
    /** 可公开的停滞原因。 */ public static final String NO_PROGRESS = "连续多轮重复读取或提交相同错误，任务没有有效进展，已停止生成；请查看执行详情后重新发起";
    private final int maxNoProgressTurns;
    private final Map<String, CapabilityResult> cache = new HashMap<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final Set<String> successfulReads = new HashSet<>();
    private long progress;
    private long observed = -1;
    private int stalled;
    private AgentRuntimeException fatal;

    public InvocationActivity(int maxNoProgressTurns) { this.maxNoProgressTurns = maxNoProgressTurns; }
    /** 每次模型调用之前检查上一轮工具是否取得新事实或完成写入检查点。 */
    public void beforeModel() { beforeModel(true); }
    /** 所有模型调用都先拒绝致命故障；只有行动轮次参与无进展计数。 */
    public synchronized void beforeModel(boolean actionRound) {
        if (Objects.nonNull(fatal)) { throw fatal; }
        if (!actionRound) { return; }
        stalled = observed == progress ? stalled + 1 : 0;
        observed = progress;
        if (stalled >= maxNoProgressTurns) {
            throw new AgentRuntimeException(CreativeErrorCode.AGENT_NO_PROGRESS, NO_PROGRESS);
        }
    }
    /** 读取输入首次成功才推进事实进展，重复读取不会重置停滞计数。 */
    public synchronized void succeeded(String fingerprint, boolean readOnly) {
        if (!readOnly || successfulReads.add(fingerprint)) { progress++; }
    }
    /** 返回此执行内相同输入的真实调用次数。 */
    public synchronized int called(String fingerprint) { return counts.merge(fingerprint, 1, Integer::sum); }
    /** 数据库或所有权故障应终止本次推理。 */
    public synchronized void abort(AgentRuntimeException failure) { fatal = failure; }
    /** 缓存键包含业务 revision，旧版本不会命中新版本读取。 */
    public synchronized CapabilityResult cached(String key) { return cache.get(key); }
    /** 缓存只接收已通过权限校验的成功读取。 */
    public synchronized void remember(String key, CapabilityResult result) {
        if (cache.size() < MAX_CACHE_ENTRIES) { cache.put(key, result); }
    }
}
