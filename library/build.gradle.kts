plugins {
    id("com.android.library")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose.compiler)
    id("maven-publish")
}

android {
    namespace = "com.heckteck.updatekit"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
    }

    // Enable publishing for JitPack
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // Core Android dependencies (required)
    api(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.activity.compose)

    // Compose (required for UI)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3.icons)

    // Compose lifecycle (required for ViewModel integration)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Google Play In-App Update (required - core functionality)
    api(libs.play.app.update)
    api(libs.play.app.update.ktx)

    // Timber for logging (required)
    implementation(libs.timber)

    // Firebase Remote Config (compileOnly - only needed if using FirebaseUpdateVersionProvider)
    // Apps using Firebase example must add this dependency themselves
    compileOnly(platform(libs.firebase.bom))
    compileOnly(libs.firebase.config.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Maven publishing configuration for JitPack
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.iamhariomsharma"
                artifactId = "update-kit"
                version = "1.0.0"

                pom {
                    name.set("UpdateKit - Android Update Manager")
                    description.set("A source-agnostic library for handling Google Play In-App Updates")
                    url.set("https://github.com/iamhariomsharma/update-kit")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("iamhariomsharma")
                            name.set("Hariom Sharma")
                            email.set("sharmahariom2644@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/iamhariomsharma/update-kit.git")
                        developerConnection.set("scm:git:ssh://github.com/iamhariomsharma/update-kit.git")
                        url.set("https://github.com/iamhariomsharma/update-kit")
                    }
                }
            }
        }
    }
}
