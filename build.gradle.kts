import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform")
}

group = "com.danielribeiro.scrcpystudio"
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    intellijPlatform {
        androidStudio(providers.gradleProperty("androidStudioVersion").get())
        bundledPlugin("org.jetbrains.android")
        testFramework(TestFrameworkType.Platform)
    }

    implementation("org.jcodec:jcodec:0.2.5")
    implementation("org.jcodec:jcodec-javase:0.2.5")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.danielribeiro.scrcpystudio"
        name = "Scrcpy Studio"
        version = providers.gradleProperty("pluginVersion")
        description = """
            Mirror and control Android devices in Android Studio with scrcpy.
            Includes device discovery, an embedded protocol client with an
            external-window fallback, and MP4 recording.
        """.trimIndent()
        vendor {
            name = "Daniel Ribeiro"
        }
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    test {
        useJUnit()
    }
}
