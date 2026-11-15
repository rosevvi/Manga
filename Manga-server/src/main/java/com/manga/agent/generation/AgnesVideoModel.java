package com.manga.agent.generation;

import java.util.Arrays;
import java.util.Optional;

/** Agnes Video 2.5 系列支持的模型标识及输出分辨率。 */
public enum AgnesVideoModel {
    /** 通用视频模型。 */
    STANDARD("agnes-video-2.5"),
    /** 低延迟视频模型。 */
    FLASH("agnes-video-2.5-flash");

    /** 首版提供两个模型均支持的分辨率。 */
    public static final String SIZE = "720P";

    private final String id;

    AgnesVideoModel(String id) { this.id = id; }

    /** 返回供应商实际接收的模型标识。 */
    public String id() { return id; }

    /** 匹配当前接入的稳定模型标识。 */
    public static Optional<AgnesVideoModel> find(String id) {
        return Arrays.stream(values()).filter(model -> model.id.equals(id)).findFirst();
    }
}
