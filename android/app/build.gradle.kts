plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

// No signing material lives in the repository. CI supplies production keys for trusted tags.
val releaseKeystore = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningEnabled = releaseSigningValues.all { !it.isNullOrBlank() }
val previewBuild = providers.gradleProperty("previewBuild").map { it.toBooleanStrict() }.orElse(false).get()
val vivoResearchBuild = providers.gradleProperty("vivoResearchBuild").map { it.toBooleanStrict() }.orElse(false).get()
val privateVivoBuild = providers.gradleProperty("privateVivoBuild").map { it.toBooleanStrict() }.orElse(false).get()
val vivoPackage = vivoResearchBuild || privateVivoBuild
if (listOf(previewBuild, vivoResearchBuild, privateVivoBuild).count { it } > 1) {
    throw GradleException("Choose only one of previewBuild, vivoResearchBuild or privateVivoBuild.")
}
if (vivoPackage && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    throw GradleException("Public release must use com.qq.knitushka. Use assembleVivo -PprivateVivoBuild=true for a private APK.")
}
if (releaseSigningValues.any { !it.isNullOrBlank() } && !releaseSigningEnabled) {
    throw GradleException("Release signing requires all four ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD environment variables.")
}

android {
    namespace = "com.qq.knitushka"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = if (vivoPackage) "com.example.tjktest.widgetdemoapp" else "com.qq.knitushka"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = providers.gradleProperty("androidVersionName").orElse("1.1").get()
        manifestPlaceholders["appLabel"] = "@string/app_name"
        manifestPlaceholders["vivoResearchEnabled"] = vivoPackage.toString()
        manifestPlaceholders["standardWidgetEnabled"] = (!vivoPackage).toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("githubRelease") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (vivoResearchBuild) {
                versionNameSuffix = "-vivo-research"
                manifestPlaceholders["appLabel"] = "КНИТУшка — Vivo probe"
            }
            if (previewBuild) {
                applicationIdSuffix = ".preview"
                versionNameSuffix = "-preview"
                manifestPlaceholders["appLabel"] = "КНИТУшка (тест)"
            }
        }
        release {
            if (releaseSigningEnabled) signingConfig = signingConfigs.getByName("githubRelease")
            optimization {
                enable = true
                // Optimize the whole program together. Experimental package-scoped optimization
                // broke Kotlin superclass access after repackaging in the 1.0.0 release.
            }
        }
        create("vivo") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isDebuggable = false
            // Separate private artifact, never the public release. CI may supply the
            // production key; local builds retain the installed test certificate.
            signingConfig = signingConfigs.getByName(if (releaseSigningEnabled) "githubRelease" else "debug")
            manifestPlaceholders["appLabel"] = "КНИТУшка"
        }
    }
    sourceSets.getByName("vivo") {
        java.srcDir("src/debug/java")
        res.srcDir("src/debug/res")
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

androidComponents {
    beforeVariants { variant ->
        // Never produce a demo-identity APK as a side effect of public/CI builds.
        if (variant.buildType == "vivo") variant.enable = privateVivoBuild
        else if (privateVivoBuild) variant.enable = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.viewpager2:viewpager2:1.0.0")
    add("vivoImplementation", "androidx.viewpager2:viewpager2:1.0.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.02.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
