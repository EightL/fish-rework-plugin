plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.fish-rework"
version = "1.0.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    // JitPack allows us to build directly from GitHub
    maven("https://jitpack.io") 
}

dependencies {
    // Paper API target for 26.1.1
    compileOnly("io.papermc.paper:paper-api:26.1.1.build.29-alpha")

    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
        options.release.set(25)
    }
    shadowJar {
        archiveClassifier.set("")
    }
    build {
        dependsOn(shadowJar)
    }
}
