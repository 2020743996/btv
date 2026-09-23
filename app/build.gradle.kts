// app/build.gradle.kts：app 模块的构建配置
// 这里声明：编译用哪个安卓版本、依赖哪些库
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.iptvplayer"
    // compileSdk：用哪个安卓版本的功能来编译代码
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.iptvplayer"
        // minSdk：支持的最低安卓版本（26 = 安卓 8.0）
        minSdk = 26
        // targetSdk：针对哪个安卓版本优化
        targetSdk = 35
        versionCode = 21
        versionName = "1.12.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release 签名：读取不入库的 keystore.properties（含 keystore 路径与密码）。
    // 文件不存在时打未签名包，开源使用者无需签名也能构建。
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val hasReleaseSigning = keystoreProps.getProperty("storeFile") != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 第一版不开启代码混淆，方便以后调试
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        // 用 Java 17 的语法特性编译
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // 开启 Jetpack Compose
        compose = true
        // User-Agent 使用 VERSION_NAME，确保发布版本与网络标识同步。
        buildConfig = true
    }
}

dependencies {
    // Compose BOM：统一管理所有 Compose 库的版本，避免版本冲突
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // Coil：异步加载并缓存 M3U tvg-logo 台标。
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Media3 ExoPlayer：Google 官方播放器，支持 HLS（m3u8）直播流
    val media3Version = "1.9.4"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // OkHttp：发送网络请求，下载 M3U 文件
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Aho-Corasick：内嵌的 TinyPinyin 拼音库（见 app/src/main/java/com/github/promeg/pinyinhelper/）用它做词典分词
    implementation("org.ahocorasick:ahocorasick:0.3.0")

    // 协程：在后台线程执行耗时操作（网络下载），不卡界面
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
