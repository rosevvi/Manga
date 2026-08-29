package com.manga;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Manga 后端服务启动入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.manga.mapper")
public class MangaApplication {

    /**
     * 启动 Spring Boot 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(MangaApplication.class, args);
    }
}
