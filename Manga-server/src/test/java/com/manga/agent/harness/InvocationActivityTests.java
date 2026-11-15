package com.manga.agent.harness;

import com.manga.agent.domain.CreativeErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证重复读取不会无限重置进展，新的资源版本或检查点仍能继续执行。 */
class InvocationActivityTests {
    /** 三轮相同读取没有新结果时，下一次计费调用之前明确停止。 */
    @Test
    void repeatedReadsStopBeforeAnotherModelCall() {
        var activity = new InvocationActivity(3);
        activity.beforeModel(); activity.succeeded("chapter:1:revision:1", true);
        activity.beforeModel();
        for (int turn = 0; turn < 2; turn++) {
            activity.succeeded("chapter:1:revision:1", true); activity.beforeModel();
        }
        activity.succeeded("chapter:1:revision:1", true);
        assertThatThrownBy(activity::beforeModel).isInstanceOfSatisfying(AgentRuntimeException.class,
                error -> assertThat(error.errorCode()).isEqualTo(CreativeErrorCode.AGENT_NO_PROGRESS));
    }
    /** 资源新版本和成功提交均为真实进展，恢复的 Harness 不继承旧停滞计数。 */
    @Test
    void revisedResourceAndCheckpointAllowMoreTurns() {
        var activity = new InvocationActivity(1);
        activity.beforeModel(); activity.succeeded("chapter:1:revision:1", true); activity.beforeModel();
        activity.succeeded("chapter:1:revision:2", true); activity.beforeModel();
        activity.succeeded("submitted-candidate", false); activity.beforeModel();
        assertThat(new InvocationActivity(1).called("chapter:1")).isEqualTo(1);
        assertThat(activity.called("chapter:1")).isEqualTo(1);
        assertThat(activity.called("chapter:1")).isEqualTo(2);
    }
}
