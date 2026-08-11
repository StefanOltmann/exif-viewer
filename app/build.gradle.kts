plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

detekt {
    source.setFrom("src", "build.gradle.kts")
    config.setFrom("../detekt.yml")
    allRules = true
    parallel = true
}

kotlin {

    compilerOptions {

        /* Make the code safer */
        progressiveMode = true
        extraWarnings = true
        allWarningsAsErrors = true
    }

    /* Only to execute tests. */
    jvm()

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

// region Code coverage
kover {
    reports {
        total {
            verify {
                rule {
                    minBound(100)
                }
            }
        }
    }
}
// endregion
