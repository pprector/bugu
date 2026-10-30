# 服务端运行镜像(S6 部署):jar 由构建机提供(gradle bootJar),镜像只含 JRE
# 国内服务器拉不到基础镜像时,替换为镜像源前缀,如 docker.m.daocloud.io/library/eclipse-temurin:25-jre
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY server/build/libs/app.jar app.jar

# 生产配置经 docker-compose.prod.yml 的 SPRING_*/BUGU_* 环境变量注入
EXPOSE 8081

ENTRYPOINT ["java", "-Xmx1g", "-jar", "app.jar"]
