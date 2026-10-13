plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "8.3.8"
    id("io.freefair.lombok") version "8.14"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.nexus)

    // Redis dependency
    implementation(libs.jedis)

    compileOnly(libs.vault.api) {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // Soft dependency: the expansion is only touched when the plugin is present at runtime.
    compileOnly(libs.placeholderapi)

    compileOnly(libs.hikaricp)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.h2)
    compileOnly(libs.mysql.connector)
    compileOnly(libs.mongodb.driver.sync)

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
    testImplementation(libs.paper.api)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.8")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveClassifier = "plain"
    }

    shadowJar {
        archiveClassifier = ""

        relocate("redis.clients.jedis", "org.glstudio.teleports.libs.jedis")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}
