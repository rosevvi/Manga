package com.manga.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.GenerationMediaObject;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
/** 对象登记、发布和回收围栏，SQL 放在 XML。 */
public interface GenerationMediaObjectMapper extends BaseMapper<GenerationMediaObject> {
    /** 调用者须先锁 Run 和 Job。 */
    GenerationMediaObject lock(@Param("id") long id);
    /** 按创建时间窗口有界扫描，不枚举整个磁盘或 Bucket。 */
    List<Long> due(@Param("now") LocalDateTime now, @Param("limit") int limit);
    /** 与产物发布同事务关闭回收入口。 */
    int publish(@Param("id") long id, @Param("actor") String actor);
    /** 撤销对象发布权并领取回收租约。 */
    int claim(@Param("id") long id, @Param("token") String token, @Param("until") LocalDateTime until, @Param("actor") String actor);
    /** 活动 worker 的对象稍后再检查。 */
    int defer(@Param("id") long id, @Param("next") LocalDateTime next, @Param("actor") String actor);
    /** 删除完成或失败后按回收令牌推进，不能覆盖新领取者。 */
    int finish(@Param("id") long id, @Param("token") String token, @Param("deleted") boolean deleted,
            @Param("next") LocalDateTime next, @Param("actor") String actor);
    /** 防御性核对已经创建的 Artifact 引用。 */
    int references(@Param("id") long id);
}

