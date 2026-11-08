package com.manga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 跨实例共享的供应商容量与熔断行锁。 */
@Data
@TableName("manga_provider_call_gate")
public class ProviderCallGate {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 受保护的模型配置主键。 */
    private Long configId;
    /** 当前固定分钟窗开始时间，UTC。 */
    private LocalDateTime windowStartedAt;
    /** 窗口已受理请求数。 */
    private Integer windowRequests;
    /** 连续可重试故障数。 */
    private Integer consecutiveFailures;
    /** 熔断开放至此时刻，UTC；空表示闭合。 */
    private LocalDateTime openUntil;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
    /** 创建操作者。 */
    private String createdBy;
    /** 更新操作者。 */
    private String updatedBy;
}
