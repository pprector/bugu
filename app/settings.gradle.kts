pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        google()
    }
    // 插件版本统一在 settings 声明,与 server(Kotlin 2.3.21)版本对齐
    plugins {
        kotlin("jvm") version "2.3.21"
        kotlin("plugin.serialization") version "2.3.21"
        id("com.android.application") version "9.4.1"
    }
}

dependencyResolutionManagement {
    repositories {
        // Kuikly 2.1.21 编译产物的本地仓库(AAR 由 KuiklyUI 工程构建后同步)
        maven { url = uri("repo") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        google()
    }
}

rootProject.name = "bugu-app"
