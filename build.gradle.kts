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
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.nexus)

    // Redis dependency
    implementation(libs.jedis)

    compileOnly(libs.vault.api) {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // Connection pool and JDBC/Mongo drivers are compile-only and declared under
    // `libraries:` in plugin.yml instead of being shaded: the server downloads only what
    // the configured backend needs, and Paper's per-plugin library classloader keeps
    // another plugin's copy from winning the load order.
    compileOnly(libs.hikaricp)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.h2)
    compileOnly(libs.mysql.connector)
    compileOnly(libs.mongodb.driver.sync)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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

    shadowJar {
        relocate("redis.clients.jedis", "org.glstudio.teleports.libs.jedis")
        mergeServiceFiles()
    }
}