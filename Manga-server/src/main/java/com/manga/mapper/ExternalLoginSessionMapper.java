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

    ExternalLoginSession findByLoginToken(@Param("loginToken") String loginToken);

    long countWaitingByScene(
            @Param("provider") ExternalIdentityProvider provider,
            @Param("sceneKey") String sceneKey,
            @Param("status") ExternalLoginStatus status);

    int confirmByScene(
            @Param("sceneKey") String sceneKey,
            @Param("userId") long userId,
            @Param("actor") String actor,
            @Param("provider") ExternalIdentityProvider provider,
            @Param("waitingStatus") ExternalLoginStatus waitingStatus,
            @Param("confirmedStatus") ExternalLoginStatus confirmedStatus);

    int consumeConfirmed(
            @Param("loginToken") String loginToken,
            @Param("actor") String actor,
            @Param("confirmedStatus") ExternalLoginStatus confirmedStatus,
            @Param("consumedStatus") ExternalLoginStatus consumedStatus);

    int expireWaiting(
            @Param("loginToken") String loginToken,
            @Param("actor") String actor,
            @Param("waitingStatus") ExternalLoginStatus waitingStatus,
            @Param("expiredStatus") ExternalLoginStatus expiredStatus);
}
