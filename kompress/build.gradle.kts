plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.maven.publish)
}

group = "io.github.muhammadunderscoreusman"
version = "1.0.0"

kotlin {

    android {
        namespace = "io.github.muhammadunderscoreusman"
        compileSdk {
            version = release(37)
        }
        minSdk = 24

    }

    val xcfName = "kompress"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

       sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)

            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.exifinterface)

            }
        }



        iosMain {
            dependencies {
            }
        }
    }

}

tasks.all {
    if (name.contains("iosX64Test", ignoreCase = true)) {
        enabled = false
    }
}


mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.muhammadunderscoreusman",
        artifactId = "Kompress",
        version = "1.0.0"
    )

    pom {
        name = "Kompress"
        description = "KMP/Android image compression library"
        url = "https://github.com/MuhammadUnderScoreUsman/Kompress"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "MuhammadUnderScoreUsman"
                name = "Muhammad Usman"
                url = "https://github.com/MuhammadUnderScoreUsman"
            }
        }

        scm {
            url = "https://github.com/MuhammadUnderScoreUsman/Kompress"
            connection = "scm:git:git://github.com/MuhammadUnderScoreUsman/Kompress.git"
            developerConnection =
                "scm:git:ssh://git@github.com/MuhammadUnderScoreUsman/Kompress.git"
        }
    }
}