import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform")
}

group = "com.danielribeiro.scrcpystudio"
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("ideTargetVersion").get())
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
            Mirror and control Android devices in Android Studio or IntelliJ IDEA with scrcpy.
            Includes device tabs, device controls, an embedded protocol client
            with an external-window fallback, screenshots, and MP4 recording.
        """.trimIndent()
        vendor {
            name = "Daniel Ribeiro"
        }
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2025.1")
            create(IntelliJPlatformType.AndroidStudio, providers.gradleProperty("androidStudioVersion").get())
            create(IntelliJPlatformType.AndroidStudio, "2026.1.2.1")
            recommended()
        }
    }
}

tasks {
    test {
        useJUnit()
    }
}
