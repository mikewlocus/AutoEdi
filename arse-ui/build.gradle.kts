plugins {
    id("java")
    id("com.moowork.node") version "1.3.1"
}

apply(plugin = "com.moowork.node")

node {
    version = "16.15.1"
    npmVersion = "8.12.2"
    distBaseUrl = "https://nodejs.org/dist"
    download = true
    workDir = file("${project.buildDir}/node")
    npmWorkDir = file("${project.buildDir}/npm")
}

tasks.build {
    dependsOn("npm_run_build")
}