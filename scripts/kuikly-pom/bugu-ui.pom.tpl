<?xml version="1.0" encoding="UTF-8"?>
<!--
  由 scripts/publish-kuikly-aar.sh 渲染生成,请勿手工编辑。
  依赖必须与 KuiklyUI/bugu-ui/build.2.1.21.gradle.kts 中 commonMain 的声明保持一致;
  若那边新增依赖,这里同步加,否则宿主解析会缺传递依赖。
-->
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.bugu.kuikly</groupId>
  <artifactId>bugu-ui</artifactId>
  <version>${BUGU_UI_VERSION}</version>
  <packaging>aar</packaging>
  <dependencies>
    <dependency>
      <groupId>com.tencent.kuikly-open</groupId>
      <artifactId>core</artifactId>
      <version>${KUIKLY_CORE_VERSION}</version>
    </dependency>
    <dependency>
      <groupId>com.tencent.kuikly-open</groupId>
      <artifactId>compose</artifactId>
      <version>${KUIKLY_CORE_VERSION}</version>
    </dependency>
    <dependency>
      <groupId>com.tencent.kuikly-open</groupId>
      <artifactId>core-annotations</artifactId>
      <version>${KUIKLY_CORE_VERSION}</version>
    </dependency>
    <dependency>
      <groupId>org.jetbrains.kotlinx</groupId>
      <artifactId>kotlinx-datetime</artifactId>
      <version>${KOTLINX_DATETIME_VERSION}</version>
    </dependency>
  </dependencies>
</project>
