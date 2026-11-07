package com.manga.service.storage;

/** 定义媒体文件保存到不同存储后端的统一契约。 */
public interface StorageStrategy {

    /** 返回策略类型，供存储服务选择实现。 */
    String getType();

    /** 保存二进制文件并返回站内或公网可访问地址。 */
    String storeBytes(byte[] data, String subDir, String extension);

    /** 使用服务端生成的稳定名称保存，恢复转存时覆盖同一对象而不生成重复文件。 */
    String storeNamedBytes(byte[] data, String subDir, String extension, String stableName);
}
