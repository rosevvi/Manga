package com.manga.common.enums;

import java.time.Duration;

/** 文本模型单次调用总时限，独立于创作 Run 的执行期限。 */
public enum ModelCallTimeout {
    MINUTES_5(5), MINUTES_15(15), MINUTES_30(30), MINUTES_60(60), HOURS_2(120), UNLIMITED(0);

    public static final ModelCallTimeout DEFAULT = MINUTES_30;
    private final int minutes;

    ModelCallTimeout(int minutes) {
        this.minutes = minutes;
    }

    /** 空值兼容历史配置和旧 Blueprint。 */
    public static ModelCallTimeout resolve(ModelCallTimeout value) {
        return value == null ? DEFAULT : value;
    }

    /** 零时长表示由 Run 期限和静默保护接管。 */
    public Duration duration() {
        return Duration.ofMinutes(minutes);
    }
}
