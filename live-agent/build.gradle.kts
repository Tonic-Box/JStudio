plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Shares the wire-protocol constants with the client/native agent.
    implementation(project(":live-client"))
}

// The agent jar is loaded into a TARGET JVM via java.lang.instrument, so it must be self-contained
// (no external classpath). live-client has no runtime deps, so we unpack its protocol classes in.
tasks.jar {
    archiveBaseName.set("live-agent")
    // Build the project dependencies (live-client.jar) before unpacking them into the agent jar.
    dependsOn(configurations["runtimeClasspath"])
    manifest {
        attributes(
            "Agent-Class" to "com.tonic.live.agent.JavaAgent",
            "Premain-Class" to "com.tonic.live.agent.JavaAgent",
            "Can-Redefine-Classes" to "true",
            "Can-Retransform-Classes" to "true"
        )
    }
    from({
        configurations["runtimeClasspath"].filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
