# syntax=docker/dockerfile:1

# 第一阶段使用 Maven 和 JDK 21 构建可执行 JAR。
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode clean package -DskipTests

# 第二阶段只保留 JRE 和应用产物，减小运行镜像体积。
FROM eclipse-temurin:21-jre-alpine

ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS=""

RUN addgroup -S manga && adduser -S manga -G manga

WORKDIR /app

COPY --from=builder --chown=manga:manga /workspace/target/manga-server-*.jar app.jar

USER manga

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
