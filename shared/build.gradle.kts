import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.spendtracker.shared"
        compileSdk = 37
        minSdk = 26

        withHostTest {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // This target keeps the parsing core testable without an Android emulator.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room3.compiler)
    add("kspJvm", libs.androidx.room3.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
