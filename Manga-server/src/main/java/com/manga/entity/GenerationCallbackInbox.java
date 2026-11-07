package com.manga.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.manga.agent.generation.GenerationProviderKey;
import com.manga.agent.generation.CallbackInboxStatus;
import lombok.Data;
import java.time.LocalDateTime;
/** 可重放的回调事实引用，不保存签名和供应商原始响应。 */
@Data
@TableName("manga_generation_callback_inbox")
public class GenerationCallbackInbox {
    /** Inbox 主键。 */ @TableId(type = IdType.AUTO) private Long id;
    /** 来源供应商。 */ private GenerationProviderKey provider;
    /** 中继唯一事件 ID。 */ private String eventId;
    /** 供应商作业 ID。 */ private String providerJobId;
    /** 防止同 ID 换正文的摘要。 */ private String payloadSha256;
    /** Inbox 处理状态。 */ private CallbackInboxStatus status;
    /** 未绑定供应商作业时的下次消费时间，避免阻塞后续回调。 */ private LocalDateTime nextAttemptAt;
    /** 创建时间。 */ private LocalDateTime createdAt;
    /** 更新时间。 */ private LocalDateTime updatedAt;
    /** 创建操作者。 */ private String createdBy;
    /** 最后更新操作者。 */ private String updatedBy;
}
