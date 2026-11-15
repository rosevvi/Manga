package com.manga.agent.generation;

import java.util.Arrays;
import java.util.Optional;

/** 共用 Agnes Images 协议的文生图模型。 */
public enum AgnesImageModel {
    /** 推荐的文生图模型。 */
    FLASH_2_5("agnes-image-2.5-flash"),
    /** 兼容上一代已发布模型。 */
    FLASH_2_1("agnes-image-2.1-flash");

    private final String id;

    AgnesImageModel(String id) { this.id = id; }

    /** 返回用户配置和供应商请求使用的模型标识。 */
    public String id() { return id; }

    /** 匹配已接入的图片模型。 */
    public static Optional<AgnesImageModel> find(String id) {
        return Arrays.stream(values()).filter(model -> model.id.equals(id)).findFirst();
    }
}
