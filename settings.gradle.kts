rootProject.name = "JStudio"

includeBuild("../YABR-fixed") {
    dependencySubstitution {
        substitute(module("com.github.Tonic-Box:YABR")).using(project(":"))
    }
}

