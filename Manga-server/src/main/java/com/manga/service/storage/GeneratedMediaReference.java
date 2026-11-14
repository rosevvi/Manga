package com.manga.service.storage;

/** 已登记的生成对象位置；删除时只使用策略、命名空间和受限对象名，不解析任意 URL。 */
public record GeneratedMediaReference(
        /** 存储策略编码。 */ String storageType,
        /** 物理存储位置的摘要，不包含访问凭据。 */ String namespace,
        /** 服务端生成的 UUID 与 owner epoch 组合名称，不含目录或扩展名。 */ String name,
        /** 发布时使用的可访问地址。 */ String url,
        /** 持久化的物理对象格式。 */ GeneratedMediaFormat format
) {
    /** 兼容原图片引用。 */
    public GeneratedMediaReference(String storageType, String namespace, String name, String url) {
        this(storageType, namespace, name, url, GeneratedMediaFormat.PNG);
    }
}
