plugins {
    `java-library`
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.lombok)
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.nexus.core)
    implementation(libs.nexus.storage)
    implementation(libs.nexus.network)

    // Shaded and relocated; Gson and SLF4J come from Paper.
    implementation(libs.jedis) {
        exclude(group = "com.google.code.gson")
        exclude(group = "org.slf4j")
    }

    // Soft dependency: the expansion is only touched when the plugin is present at runtime.
    compileOnly(libs.placeholderapi)

    // Loaded by the server through the libraries block in plugin.yml.
    compileOnly(libs.hikaricp)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.h2)
    compileOnly(libs.mysql.connector)
    compileOnly(libs.mongodb.driver.sync)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf(
            "version" to version,
            "hikariVersion" to libs.versions.hikari.get(),
            "sqliteVersion" to libs.versions.sqlite.get(),
            "h2Version" to libs.versions.h2.get(),
            "mysqlVersion" to libs.versions.mysql.get(),
            "mongodbVersion" to libs.versions.mongodb.get(),
            "postgresqlVersion" to libs.versions.postgresql.get(),
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveClassifier = "plain"
    }

    shadowJar {
        archiveClassifier = ""

        relocate("redis.clients", "org.glstudio.teleports.libs.jedis")
        relocate("org.apache.commons.pool2", "org.glstudio.teleports.libs.pool2")
        relocate("org.json", "org.glstudio.teleports.libs.json")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}
