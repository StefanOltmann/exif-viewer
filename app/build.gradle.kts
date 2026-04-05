plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {

    compilerOptions {

        /* Make the code safer */
        progressiveMode = true
        extraWarnings = true
    }

    /* Only to execute tests. */
    jvm {
        compilerOptions {
            allWarningsAsErrors = true
        }
    }

    wasmJs {

        outputModuleName = "app"

        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }

        binaries.executable()
    }

    sourceSets {

        val wasmJsMain by getting

        commonMain.dependencies {
            implementation(libs.kim)
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
