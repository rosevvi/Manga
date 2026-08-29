package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.common.enums.ExternalIdentityProvider;
import com.manga.entity.ExternalIdentity;
import org.apache.ibatis.annotations.Param;

/**
 * 定义第三方身份绑定的数据访问契约。
 */
public interface ExternalIdentityMapper extends BaseMapper<ExternalIdentity> {

    Long findUserId(
            @Param("provider") ExternalIdentityProvider provider,
            @Param("providerUserId") String providerUserId);
}
