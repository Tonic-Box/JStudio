plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
}

dependencies {
    // The app (and YABR) are provided by JStudio's class loader at runtime; never bundle them.
    compileOnly(rootProject)
    compileOnly("com.github.Tonic-Box:YABR:main-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

// A plain (thin) jar: only the plugin's own classes. App/YABR/gson come from the parent class loader.
tasks.named<Jar>("jar") {
    archiveBaseName.set("sample-plugin")
}

// Drops the built jar into ~/.jstudio/plugins so JStudio picks it up on next launch.
tasks.register<Copy>("copyToPluginsDir") {
    dependsOn(tasks.named("jar"))
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(File(System.getProperty("user.home"), ".jstudio/plugins"))
}
