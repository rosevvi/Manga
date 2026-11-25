package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.entity.AssetVisual;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;
import java.util.List;

/** 资产的一项图片及其当前产物。 */
public interface AssetVisualMapper extends BaseMapper<AssetVisual> {
    /** 串行化同一图片项的编辑和发起生成。 */
    AssetVisual lock(@Param("id") long id);

    /** 查出图片项上等待审批、执行或停止的运行，供列表与写入检查共用。 */
    List<Long> busyVisualIds(@Param("projectId") long projectId, @Param("ids") Collection<Long> ids);

}
