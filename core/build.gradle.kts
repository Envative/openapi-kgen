dependencies {
    implementation(Dep.ICU)

    api(Dep.SWAGGER_PARSER)
    implementation(Dep.KOTLIN_POET)


    // rxjava
    api("io.reactivex.rxjava2:rxandroid:2.0.1")
    api("io.reactivex.rxjava2:rxjava:2.2.3")
    api("io.reactivex.rxjava2:rxkotlin:2.1.0")
}

tasks.processResources {
    filesMatching("version.properties") {
        expand(project.properties)
    }
}