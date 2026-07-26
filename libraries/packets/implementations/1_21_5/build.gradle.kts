plugins {
    id("java-library")
    id("io.papermc.paperweight.userdev")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    paperweight.paperDevBundle("1.21.5-R0.1-SNAPSHOT")
    compileOnly(project(":libraries:packets:packets-api"))

    constraints {
        compileOnly("net.kyori:adventure-text-serializer-ansi:4.26.1")
    }

    testImplementation(project(":libraries:packets"))
    testImplementation(project(":libraries:packets:packets-api"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.1.0")
    testImplementation("org.junit.platform:junit-platform-console-standalone:6.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    test {
        useJUnitPlatform()
    }
}