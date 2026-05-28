# ============================================================
# ServerA — Spring Boot 后端镜像
# 构建: docker build -f docker/serverA/Dockerfile.backend -t middleware-backend .
# ============================================================

# ---------- 阶段1: Maven 构建 ----------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 先复制 pom 文件利用 Docker 缓存层
COPY pom.xml .
COPY middleware-server/pom.xml middleware-server/
COPY middleware-pojo/pom.xml middleware-pojo/
COPY middleware-common/pom.xml middleware-common/
COPY aliyun-oss-spring-boot-starter/pom.xml aliyun-oss-spring-boot-starter/
COPY aliyun-oss-spring-boot-autoconfigure/pom.xml aliyun-oss-spring-boot-autoconfigure/
COPY flexmark-jacolp-starter/pom.xml flexmark-jacolp-starter/
COPY flexmark-jacolp-autoconfigure/pom.xml flexmark-jacolp-autoconfigure/

RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY middleware-server/src middleware-server/src/
COPY middleware-pojo/src middleware-pojo/src/
COPY middleware-common/src middleware-common/src/
# COPY aliyun-oss-spring-boot-starter/src aliyun-oss-spring-boot-starter/src/
COPY aliyun-oss-spring-boot-autoconfigure/src aliyun-oss-spring-boot-autoconfigure/src/
# COPY flexmark-jacolp-starter/src flexmark-jacolp-starter/src/
COPY flexmark-jacolp-autoconfigure/src flexmark-jacolp-autoconfigure/src/

RUN mvn clean package -pl middleware-server -am -DskipTests -B -Dmaven.test.skip=true

# ---------- 阶段2: 运行镜像 ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建数据目录
RUN mkdir -p /app/data/markdown/input /app/data/markdown/output

# 复制 jar
COPY --from=builder /build/middleware-server/target/*.jar app.jar

# 复制 Docker 环境配置文件
COPY application-docker.yml /app/config/application.yml

# 健康检查 (需要 actuator)
EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} \
  -Dspring.config.additional-location=/app/config/ \
  -jar app.jar"]
