plugins {
    java
}

dependencies {
    implementation(gradleApi())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
