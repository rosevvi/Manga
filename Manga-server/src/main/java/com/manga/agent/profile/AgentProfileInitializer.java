package com.manga.agent.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 首次启动建立内置发布定义；以后启动保留管理员的发布和启停选择。 */
@Component
@RequiredArgsConstructor
public class AgentProfileInitializer implements ApplicationRunner {
    private final AgentProfileManagementService profiles;

    /** 数据库迁移完成后幂等初始化。 */
    @Override
    public void run(ApplicationArguments arguments) { profiles.initializeBuiltIns(); }
}
