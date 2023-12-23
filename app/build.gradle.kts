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
            api("com.ashampoo:kim:0.8.3")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        wasmJsMain.dependencies {
            implementation(npm("pako", "2.1.0"))
        }

        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.3.0")
        }
    }
}
