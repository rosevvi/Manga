package com.manga.agent.generation;
/** 图片作业路由与调度协议。 */
public final class GenerationConstants {
    /** 生成作业 API。 */ public static final String API = "/api/v1/generation-jobs";
    /** 供平台异步供应商中继使用的签名回调根路径，不是 OpenAI 原生 Webhook。 */ public static final String CALLBACK_API = "/api/v1/generation-callbacks";
    /** 回调根路径下的全部供应商，仍强制执行 HMAC 鉴权。 */ public static final String CALLBACK_PATTERN = CALLBACK_API + "/**";
    /** HMAC-SHA256 十六进制签名头。 */ public static final String CALLBACK_SIGNATURE_HEADER = "X-Manga-Signature";
    /** 发送时间戳头，单位 Unix 秒。 */ public static final String CALLBACK_TIMESTAMP_HEADER = "X-Manga-Timestamp";
    /** 回调只接收事实引用，禁止媒体数据或任意 URL。 */ public static final int CALLBACK_MAX_BYTES = 2048;
    /** 补偿调度表达式。 */ public static final String INTERVAL = "${manga.creative.generation.scan-interval:3s}";
    /** Fake 模型标识。 */ public static final String FAKE_MODEL = "fake-image";
    /** 运维配置预计费用的计价币种。 */ public static final String CURRENCY = "USD";
    /** 指定的首个真实图片模型。 */ public static final String OPENAI_MODEL = "gpt-image-2";
    /** 固定质量，保证审批预览和实际请求一致。 */ public static final String QUALITY = "medium";
    /** 平台只接收 PNG，避免下载外部任意 URL。 */ public static final String FORMAT = "png";
    /** OpenAI 图片生成相对路径。 */ public static final String IMAGE_PATH = "/images/generations";
    /** 图片 API 的请求追踪头；不是幂等承诺。 */ public static final String CLIENT_REQUEST_ID = "X-Client-Request-Id";
    /** 作业列表单页上限。 */ public static final int PAGE_SIZE = 25;
    /** 平台生成媒体子目录。 */ public static final String MEDIA_DIRECTORY = "generated";
    /** 原冻结规格恢复只注入业务引用。 */ public static final String RESUME = "\n<generation_result>%s</generation_result>\n以上为服务端生成结果。请说明作业状态和产物，不要再次提交生成；取消和失败均不可宣称媒体已保存。";
    private GenerationConstants() { }
}
