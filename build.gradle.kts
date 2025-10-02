plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "dev.aledlb"
version = "1.2.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi-repo"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compileOnly("org.spongepowered:configurate-yaml:4.1.2")
    compileOnly("org.spongepowered:configurate-extra-kotlin:4.1.2")
    compileOnly("org.jetbrains.exposed:exposed-core:0.44.1")
    compileOnly("org.jetbrains.exposed:exposed-dao:0.44.1")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    compileOnly("org.jetbrains.exposed:exposed-java-time:0.44.1")
    compileOnly("com.zaxxer:HikariCP:5.0.1")
    compileOnly("org.xerial:sqlite-jdbc:3.44.1.0")
    compileOnly("com.mysql:mysql-connector-j:8.2.0")
    compileOnly("org.postgresql:postgresql:42.7.7")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    compileOnly("ch.qos.logback:logback-classic:1.5.13")

    compileOnly("com.google.code.gson:gson:2.10.1")

    compileOnly("me.clip:placeholderapi:2.11.5")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
