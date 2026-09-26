// settings.gradle.kts：整个项目的"总目录"
// 它告诉 Gradle：项目叫什么名字，包含哪些模块（module）
pluginManagement {
    // 插件（比如安卓构建插件）去哪里下载
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 所有依赖库去哪里下载
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "IptvPlayer"
include(":app")
include(":liquidglass-core")
include(":liquidglass-compose")

project(":liquidglass-core").projectDir = file("third_party/liquidglass/liquidglass-core")
project(":liquidglass-compose").projectDir = file("third_party/liquidglass/liquidglass-compose")
