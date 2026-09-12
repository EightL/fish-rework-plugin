plugins {
    `java-library`
}

group = "com.fish-rework"
version = "1.5.1"

// One JAR for Java 21 and newer servers. Newer optional features are resolved at runtime.
// Compile against the oldest supported API; Paper remaps its legacy attribute/enum references.
// Legacy deployment scripts may still pass targetMinecraftVersion; it does not change the build.

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/") {
        content {
            includeGroup("me.clip")
        }
    }
    maven("https://repo.codemc.io/repository/creatorfromhell/")
    // JitPack allows us to build directly from GitHub
    maven("https://jitpack.io") 
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.19")
    compileOnly("me.clip:placeholderapi:2.12.3")

    // Loaded by Paper through plugin.yml libraries at runtime.
    compileOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
    }
    withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
        options.release.set(21)
    }
}
