plugins {
    `java-library`
}

group = "com.fish-rework"
version = "1.1.2"

data class PaperBuildTarget(
    val apiVersion: String,
    val javaVersion: Int,
)

val targetMinecraftVersion = providers.gradleProperty("targetMinecraftVersion").orElse("26.1.1").get()
val paperBuildTarget = when (targetMinecraftVersion) {
    "26.1.1" -> PaperBuildTarget(apiVersion = "26.1.1.build.29-alpha", javaVersion = 25)
    "1.21.11" -> PaperBuildTarget(apiVersion = "1.21.11-R0.1-SNAPSHOT", javaVersion = 21)
    else -> throw GradleException(
        "Unsupported targetMinecraftVersion '$targetMinecraftVersion'. Supported values: 26.1.1, 1.21.11"
    )
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    // JitPack allows us to build directly from GitHub
    maven("https://jitpack.io") 
}

dependencies {
    // Switch target with -PtargetMinecraftVersion=<version>
    compileOnly("io.papermc.paper:paper-api:${paperBuildTarget.apiVersion}")

    // Loaded by Paper through plugin.yml libraries at runtime.
    compileOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(paperBuildTarget.javaVersion))
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
        options.release.set(paperBuildTarget.javaVersion)
    }
}
