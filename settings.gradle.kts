plugins {
    // Downloads the JDKs the agent tests run on (8, 11, 17) when they are not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "oeHostsAgent"
