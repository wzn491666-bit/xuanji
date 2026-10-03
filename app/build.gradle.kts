// 璇玑 Android APP — app 模块构建文件
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.xuanji.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xuanji.android"
        minSdk = 26
        // 必须保持 28：targetSdk ≥ 29 时 Android 禁止执行 APP 私有目录里的程序，
        // proot 和 Ubuntu 里的 node 就跑不起来（Aether、DSH 手机版同样卡在 28）
        targetSdk = 28
        versionCode = 34
        versionName = "0.2.5"
        // proot 只提供了 arm64 版本，现在的手机基本都是 arm64
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // The bundled proot runtime executes from app-private storage, so this
    // privately distributed APK intentionally targets API 28.
    lint { disable += "ExpiredTargetSdkVersion" }

    // rootfs 已经是 gzip 压缩包。用 .tgz 而不是 .tar.gz：打包工具会自动把 .gz 资源解压掉
    androidResources { noCompress += listOf("tgz") }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.commons.compress)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.jsoup)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}

// Bundle the canonical reviewed documents, so offline notices cannot drift from GitHub.
abstract class GenerateProjectDocumentation : Sync() {
    @get:org.gradle.api.tasks.OutputDirectory
    abstract val documentationDirectory: org.gradle.api.file.DirectoryProperty
}
val projectDocumentationAssets = layout.buildDirectory.dir("generated/project-documentation-assets")
val generateProjectDocumentation by tasks.registering(GenerateProjectDocumentation::class) {
    documentationDirectory.set(projectDocumentationAssets)
    into(documentationDirectory)
    from(rootProject.file("docs/TECHNICAL_ARCHITECTURE.md")) { into("documentation"); rename { "technical-architecture.md" } }
    from(rootProject.file("docs/PRIVACY.md")) { into("documentation"); rename { "privacy.md" } }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) { into("documentation"); rename { "third-party-notices.md" } }
    from(rootProject.file("LICENSE")) { into("documentation") }
    from(rootProject.file("NOTICE")) { into("documentation") }
    from(rootProject.file("docs/open-source")) { into("documentation/open-source") }
    doFirst {
            listOf("LICENSE", "NOTICE", "docs/TECHNICAL_ARCHITECTURE.md", "docs/PRIVACY.md", "THIRD_PARTY_NOTICES.md", "docs/open-source/RELEASE_CHECKLIST.md", "docs/open-source/INVENTORY.md").forEach {
            check(rootProject.file(it).isFile) { "Required offline project documentation is missing: $it" }
        }
    }
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateProjectDocumentation) { it.documentationDirectory }
}
