package com.manga.service.storage;

/** 定义媒体文件保存到不同存储后端的统一契约。 */
public interface StorageStrategy {

    /** 返回策略类型，供存储服务选择实现。 */
    String getType();

    /** 保存二进制文件并返回站内或公网可访问地址。 */
    String storeBytes(byte[] data, String subDir, String extension);

    /** 使用服务端生成的稳定名称保存，恢复转存时覆盖同一对象而不生成重复文件。 */
    String storeNamedBytes(byte[] data, String subDir, String extension, String stableName);
    /** 在写入前获得可持久化位置，物理位置不能包含访问密钥。 */
    default GeneratedMediaReference generatedReference(String jobId, long epoch) {
        return generatedReference(jobId, epoch, GeneratedMediaFormat.PNG);
    }
    /** 解析指定格式的物理对象位置。 */
    GeneratedMediaReference generatedReference(String jobId, long epoch, GeneratedMediaFormat format);
    /** 校验引用后写入已登记位置。 */
    void writeGenerated(byte[] data, GeneratedMediaReference reference);
    /** 幂等删除受限生成对象，不能删除任意路径或 URL。 */
    void deleteGenerated(GeneratedMediaReference reference);
}
