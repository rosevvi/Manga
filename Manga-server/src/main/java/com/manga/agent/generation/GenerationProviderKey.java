package com.manga.agent.generation;
/** 图片供应商路由，不接受模型提供任意服务地址。 */
public enum GenerationProviderKey {
    /** 无费用的确定性流程模拟器。 */ FAKE,
    /** OpenAI Images API，首个模型为 gpt-image-2。 */ OPENAI,
    /** Agnes Video 2.5 系列异步 API。 */ AGNES
}
