plugins {
    id("java")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.tonic.ui"
version = "6.0-SNAPSHOT"

application {
    mainClass.set("com.tonic.ui.JStudio")
}

repositories {
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")

    implementation("com.formdev:flatlaf:3.2.5")
    implementation("com.fifesoft:rsyntaxtextarea:3.3.4")
    implementation("org.tinyjee.jgraphx:jgraphx:3.4.1.3")

    implementation("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    implementation("com.github.javaparser:javaparser-core:3.25.5")

    // Use local YABR with debug prints for call graph debugging
    //implementation("com.tonic:YABR:1.0.0")
    implementation("com.github.Tonic-Box:YABR:main-SNAPSHOT")

    // CLI dependencies
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")
    implementation("org.jline:jline:3.25.0")

    // Groovy for DSL support
    implementation("org.apache.groovy:groovy:4.0.15")

    // JSON parsing for theme configuration
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.tonic.ui.JStudio"
    }
}

tasks.shadowJar {
    archiveBaseName.set("JStudio")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.tonic.ui.JStudio"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register<JavaExec>("runCli") {
    description = "Run JStudio CLI"
    group = "application"
    mainClass.set("com.tonic.cli.HeadlessRunner")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
        cacheDynamicVersionsFor(0, "seconds")
    }
}

tasks.register("refreshDependencies") {
    description = "Force refresh all dependencies"
    group = "build"
    doLast {
        configurations.all {
            resolutionStrategy.cacheChangingModulesFor(0, "seconds")
        }
        println("Dependencies will be refreshed on next build")
    }
}
