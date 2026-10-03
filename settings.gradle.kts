// 璇玑 Android APP — 项目设置文件
// 这个文件告诉 Android Studio 项目叫什么名字、模块在哪里

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "xuanji"
include(":app")
