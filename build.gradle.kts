import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

version = "1.0"
group = "net.onyxium"

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.mattmalec.com/repository/releases/") }
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.30")
    compileOnly("com.velocitypowered:velocity-api:3.2.0-SNAPSHOT")

    implementation("com.mattmalec:Pterodactyl4J:2.BETA_140")

    annotationProcessor("org.projectlombok:lombok:1.18.30")
    annotationProcessor("com.velocitypowered:velocity-api:3.2.0-SNAPSHOT")

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
}

val sourcesJar by tasks.registering(Jar::class) {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val shadowJar = (tasks["shadowJar"] as ShadowJar).apply {
    relocate("com.mattmalec.pterodactyl4j", "net.onyxium.flexnet.com.mattmalec.pterodactyl4j")
}

val build = (tasks["build"] as Task).apply {
    arrayOf(sourcesJar, shadowJar).forEach { dependsOn(it) }
}