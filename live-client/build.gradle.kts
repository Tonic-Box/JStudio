plugins {
    `java-library`
}

group = "com.tonic.live"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

// Uses the JDK's com.sun.tools.attach (jdk.attach module) - no external dependencies.
tasks.withType<JavaCompile> {
    options.compilerArgs.add("--add-modules")
    options.compilerArgs.add("jdk.attach")
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
