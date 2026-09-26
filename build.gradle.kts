// 根 build.gradle.kts：只声明各插件用哪个版本，不在这里真正加载
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    // Compose 编译器插件：把 Compose 的界面代码翻译成安卓能执行的代码
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
