package com.manga;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 验证应用上下文能够正常创建。
 */
@SpringBootTest
@ActiveProfiles("test")
class MangaApplicationTests {

    /**
     * 确认基础 Bean 配置不存在启动错误。
     */
    @Test
    void contextLoads() {
    }
}
