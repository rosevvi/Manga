package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.CreativeEventOutbox;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提供 Creative Event Outbox 的待投递查询。
 */
public interface CreativeEventOutboxMapper extends BaseMapper<CreativeEventOutbox> {

    /** 按创建顺序查询有限数量的待投递记录。 */
    List<CreativeEventOutbox> findPending(@Param("limit") int limit);
}
