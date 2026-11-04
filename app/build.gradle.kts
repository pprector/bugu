import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    // AGP 9 内置 Kotlin 支持；Compose 编译器需要官方 Compose Compiler Gradle 插件
    kotlin("plugin.compose")
    // API DTO(kotlinx-serialization)与离线上报队列持久化
    kotlin("plugin.serialization")
}

group = "com.bugu"

// 极光 JPush AppKey 不进 git,经 local.properties 的 jpush.appKey 注入(联验时填入)
val localProps: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val jpushAppKey: String = localProps.getProperty("jpush.appKey") ?: "PLACEHOLDER-APPKEY"

// Release 签名只从本机 gitignored 的 local.properties 读取;keystore 文件必须放在仓库外。
val releaseStoreFilePath: String? = localProps.getProperty("bugu.release.storeFile")
val releaseStorePassword: String? = localProps.getProperty("bugu.release.storePassword")
val releaseKeyAlias: String? = localProps.getProperty("bugu.release.keyAlias")
val releaseKeyPassword: String? = localProps.getProperty("bugu.release.keyPassword")
val releaseStoreFile: File? = releaseStoreFilePath?.let { rootProject.file(it) }
val releaseSigningConfigured: Boolean =
    releaseStoreFile?.isFile == true &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

// 版本号只维护这一处;每次对外发布 versionCode 必须 +1,versionName 按语义化版本递增。
val versionProps: Properties = Properties().apply {
    val file = rootProject.file("version.properties")
    require(file.isFile) { "缺少 app/version.properties,无法确定 versionCode/versionName" }
    file.inputStream().use { load(it) }
}
val appVersionCode: Int = versionProps.getProperty("versionCode")?.toIntOrNull()
    ?: error("app/version.properties 的 versionCode 必须是整数")
val appVersionName: String = versionProps.getProperty("versionName")?.takeIf { it.isNotBlank() }
    ?: error("app/version.properties 缺少 versionName")
version = appVersionName

android {
    namespace = "com.bugu"
    // Compose BOM 2026.09 依赖面向 android-37.2 编译(AGP 9 支持次版本 SDK)
    compileSdk = 37
    compileSdkMinor = 2

    signingConfigs {
        create("release") {
            releaseStoreFile?.let { storeFile = it }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    defaultConfig {
        // 安装身份(com.bugu):与极光平台绑定的 Android 包名一致;namespace/Kotlin 包名已统一为 com.bugu
        applicationId = "com.bugu"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        manifestPlaceholders["JPUSH_APPKEY"] = jpushAppKey
        // 极光 SDK AAR 的 manifest 引用 ${JPUSH_CHANNEL},统计维度用,固定 developer
        manifestPlaceholders["JPUSH_CHANNEL"] = "developer"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 签名配置存在但字段缺失时由下方 taskGraph 检查给出明确错误,绝不回退 debug 签名。
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

// 只有执行 release 相关任务时才要求签名;debug/test 不依赖 release 密钥。
gradle.taskGraph.whenReady {
    val releaseTasks = setOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "installRelease",
        "validateSigningRelease",
    )
    if (allTasks.any { it.name in releaseTasks } && !releaseSigningConfigured) {
        throw GradleException(
            "Release 签名未配置:请在 app/local.properties 设置 " +
                "bugu.release.storeFile/storePassword/keyAlias/keyPassword,并确认 keystore 文件存在。" +
                "构建不会静默回退到 debug 签名。",
        )
    }
}

dependencies {
    // API 层(搬自参考工程 client/shared 纯 Kotlin 实现):Ktor + kotlinx-serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-android:3.6.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // 断网上报重试队列(WorkManager)与生命周期协程
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    // 极光 JPush(mavenCentral);6.x 要求通知权限(已声明)与 minSdk 21+
    implementation("cn.jiguang.sdk:jpush:6.3.0")
    implementation("cn.jiguang.sdk:jcore:5.6.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // JVM 单测(纯逻辑):AGP 内置 Kotlin 下无 kotlin("test") 助手,显式 junit4 版 kotlin-test
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.21")
    testImplementation("junit:junit:4.13.2")
}
