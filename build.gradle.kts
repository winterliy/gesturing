plugins {
    java
}

group = "dev.ledge"
version = "0.3.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Paper 1.16.5 has no userdev bundle: compile against the patched (Spigot-mapped) server jar produced by Paper itself.
// Obtain it with: java -jar paper-1.16.5-794.jar (cache/patched_1.16.5.jar) and pass -PserverJar=<path>.
val serverJar = providers.gradleProperty("serverJar").orElse("libs/server-1.16.5.jar")


dependencies {
    annotationProcessor("com.pkware.jabel:jabel-javac-plugin:1.0.1-2")
    compileOnly("com.pkware.jabel:jabel-javac-plugin:1.0.1-2")
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly(files(serverJar))
    testImplementation("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

// Source uses Java 17 syntax; Jabel rewrites it so the jar loads on Java 8 (the era's usual server Java).
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    options.release.set(8)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
