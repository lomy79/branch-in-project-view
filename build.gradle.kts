plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "io.github.lomy79"
version = "0.2.0"

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
            // Lowest supported released build (2025.3). Verified by the plugin verifier.
            sinceBuild = "253"
            // Leave the upper bound open: the plugin stays compatible with future
            // builds without rebuilding on every update.
            untilBuild = provider { null }
        }
    }

    // Marketplace signing (optional but recommended). Values come from env vars,
    // so no secret is stored in the repo. See JetBrains "Plugin Signing".
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // Marketplace publishing: ./gradlew publishPlugin with PUBLISH_TOKEN set.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    // `./gradlew verifyPlugin` checks API compatibility against released IDEs.
    pluginVerification {
        ides {
            recommended()
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
