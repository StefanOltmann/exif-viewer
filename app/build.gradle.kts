plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {

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

            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
        }

        jvmTest.dependencies {
            implementation(libs.kotlinx.io)
        }
    }
}
