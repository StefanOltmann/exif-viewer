import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {

        moduleName = "app"

        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }

        binaries.executable()
    }

    /* Only to execute tests. */
    jvm()

    sourceSets {

        val wasmJsMain by getting

        commonMain.dependencies {
            implementation(libs.ashampoo.kim)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        wasmJsMain.dependencies {
            implementation(npm("pako", "2.1.0"))
        }

        jvmTest.dependencies {
            implementation(libs.kotlinx.io)
        }
    }
}
