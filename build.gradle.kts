import java.security.MessageDigest

plugins {
    id("java")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}
group = "com.tonic.ui"
version = "20.0-SNAPSHOT"

application {
    mainClass.set("com.tonic.ui.JStudio")
}

repositories {
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")

    implementation("com.formdev:flatlaf:3.2.5")
    implementation("com.fifesoft:rsyntaxtextarea:3.3.4")
    implementation("com.fifesoft:autocomplete:3.3.1")
    implementation("org.tinyjee.jgraphx:jgraphx:3.4.1.3")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    implementation("com.github.javaparser:javaparser-core:3.25.5")

    //implementation("com.tonic:YABR:1.0.1")
    implementation("com.github.Tonic-Box:YABR:main-SNAPSHOT")

    // CLI dependencies
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")
    implementation("org.jline:jline:3.25.0")

    // Groovy for DSL support
    implementation("org.apache.groovy:groovy:4.0.15")

    // JSON parsing for theme configuration
    implementation("com.google.code.gson:gson:2.10.1")

    // Live JVM debugging client (attach + protocol to the pure-Java agent)
    implementation(project(":live-client"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.test {
    useJUnitPlatform()
}



// ============================================================================
// JStudio Live agent (pure-Java java.lang.instrument): an arch-independent jar bundled in JStudio.jar and
// loaded into a target JVM via attach. Built by the :live-agent module and staged here for packaging.
// ============================================================================
sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("native-resources"))

// Bundle the Java agent jar at agent/live-agent.bin (non-.jar name so the shadow plugin doesn't unpack it).
// JStudio extracts and loads it via java.lang.instrument at attach time.
val stageJavaAgent by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the pure-Java fallback Live agent jar for bundling."
    from(project(":live-agent").tasks.named("jar"))
    into(layout.buildDirectory.dir("native-resources/agent"))
    // Bundle under a non-.jar extension: the shadow plugin unpacks nested *.jar resources when merging.
    // JStudio extracts this back to a real .jar at runtime.
    rename { "live-agent.bin" }
}

tasks.named("processResources") { dependsOn(stageJavaAgent) }

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.tonic.ui.JStudio"
        attributes["Implementation-Version"] = project.version.toString()
    }
}

tasks.shadowJar {
    archiveBaseName.set("JStudio")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "com.tonic.ui.JStudio"
        attributes["Implementation-Version"] = project.version.toString()
    }
}

// Emits build/libs/JStudio.jar.sha256 (sha256sum format) next to the shadow jar, for the
// self-update integrity check. Upload both files as release assets.
val shadowJarChecksum by tasks.registering {
    val shadowJar = tasks.shadowJar
    dependsOn(shadowJar)
    val jarFile = shadowJar.flatMap { it.archiveFile }
    val checksumFile = layout.buildDirectory.file("libs/JStudio.jar.sha256")
    inputs.file(jarFile)
    outputs.file(checksumFile)
    doLast {
        val jar = jarFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        jar.inputStream().use { input ->
            val buffer = ByteArray(16384)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        checksumFile.get().asFile.writeText("$hex  ${jar.name}\n")
    }
}

tasks.shadowJar {
    finalizedBy(shadowJarChecksum)
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
