plugins {
    // Resolves a JDK 21 toolchain automatically when the machine running the build only has a
    // newer (or older) JDK installed, so the build is reproducible without a documented
    // "install JDK 21 first" step. See build.gradle.kts for why the toolchain is pinned at 21.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "notification-service"
