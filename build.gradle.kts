plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "io.github.lomy79"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Use the locally installed IDE: no need to download the whole SDK.
        local("/home/andrea/work/idea-IU-262.5752.32")

        // The Git plugin is bundled in the IDE: we need its API (git4idea).
        bundledPlugin("Git4Idea")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            // Leave the upper bound open: the plugin stays compatible with future
            // builds without rebuilding on every update.
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// Development sandbox only: skip the "Trust Project" dialog.
tasks.runIde {
    jvmArgs("-Didea.trust.all.projects=true")
}
