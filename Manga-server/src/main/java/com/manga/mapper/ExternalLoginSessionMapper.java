package com.manga.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.manga.common.enums.ExternalIdentityProvider;
import com.manga.common.enums.ExternalLoginStatus;
import com.manga.entity.ExternalLoginSession;
import org.apache.ibatis.annotations.Param;

/**
 * 定义数据库扫码登录会话的数据访问契约。
 */
public interface ExternalLoginSessionMapper extends BaseMapper<ExternalLoginSession> {

    /** 按登录令牌查询外部登录会话。 */
    ExternalLoginSession findByLoginToken(@Param("loginToken") String loginToken);

    /** 统计等待中的登录场景。 */
    long countWaitingByScene(
            @Param("provider") ExternalIdentityProvider provider,
            @Param("sceneKey") String sceneKey,
            @Param("status") ExternalLoginStatus status);

    /** 确认指定场景的外部登录会话。 */
    int confirmByScene(
            @Param("sceneKey") String sceneKey,
            @Param("userId") long userId,
            @Param("actor") String actor,
            @Param("provider") ExternalIdentityProvider provider,
            @Param("waitingStatus") ExternalLoginStatus waitingStatus,
            @Param("confirmedStatus") ExternalLoginStatus confirmedStatus);

    /** 消费已确认的外部登录会话。 */
    int consumeConfirmed(
            @Param("loginToken") String loginToken,
            @Param("actor") String actor,
            @Param("confirmedStatus") ExternalLoginStatus confirmedStatus,
            @Param("consumedStatus") ExternalLoginStatus consumedStatus);

    /** 将等待中的外部登录会话标记为过期。 */
    int expireWaiting(
            @Param("loginToken") String loginToken,
            @Param("actor") String actor,
            @Param("waitingStatus") ExternalLoginStatus waitingStatus,
            @Param("expiredStatus") ExternalLoginStatus expiredStatus);
}
