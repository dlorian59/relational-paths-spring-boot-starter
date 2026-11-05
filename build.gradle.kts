plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.59alex"
version = "0.1.0"
val testJavaVersion = providers.gradleProperty("testJavaVersion").map(String::toInt)
    .getOrElse(JavaVersion.current().majorVersion.toInt())
repositories { mavenLocal(); mavenCentral() }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain.languageVersion.set(JavaLanguageVersion.of(testJavaVersion))
    withSourcesJar()
    withJavadocJar()
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}
dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    api("io.github.59alex:relational-paths:0.3.0")
    api("org.springframework.boot:spring-boot-starter")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.1.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
val examples = sourceSets.create("examples")
configurations[examples.implementationConfigurationName].extendsFrom(configurations.implementation.get())
examples.compileClasspath += sourceSets.main.get().output
examples.runtimeClasspath += sourceSets.main.get().output
sourceSets.test {
    compileClasspath += examples.output
    runtimeClasspath += examples.output
}
tasks.test { useJUnitPlatform() }
val example = tasks.register<JavaExec>("example") {
    group = "verification"
    description = "Runs the Spring-injected order report without a database"
    classpath = examples.runtimeClasspath
    mainClass.set("example.reports.ReportApplication")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(testJavaVersion)) })
}
tasks.check { dependsOn(example) }
tasks.withType<Jar>().configureEach { into("META-INF") { from("LICENSE") } }
publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name.set("relational-paths-spring-boot-starter")
                description.set("Spring Boot auto-configuration for explicit relational metadata and bounded JOIN planning")
                url.set("https://github.com/dlorian59/relational-paths-spring-boot-starter")
                licenses { license { name.set("MIT License"); url.set("https://opensource.org/licenses/MIT") } }
                developers { developer { id.set("dlorian59"); name.set("Aleksandr Kashevarov") } }
                scm { url.set("https://github.com/dlorian59/relational-paths-spring-boot-starter") }
            }
        }
    }
}
