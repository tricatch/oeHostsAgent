plugins {
    java
}

group = "tricatch.oehosts"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Everything but the JDK 18 resolver provider runs on Java 8: the premain class, the loader, the
// mapping table and the JDK 8 / 9-17 hooks (LegacyResolver).
tasks.compileJava {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options") // "source value 8 is obsolete"
}

// java.net.spi.InetAddressResolverProvider (JEP 418) exists from JDK 18; only the JDK loads this
// class (through META-INF/services), so older JDKs never see its class file version.
val jdk18 = sourceSets.create("jdk18") {
    compileClasspath += sourceSets.main.get().output
}
tasks.named<JavaCompile>(jdk18.compileJavaTaskName) {
    options.release = 18
}

// The application the agent tests start, on every supported JDK (so compiled for 8).
val testApp = sourceSets.create("testApp")
tasks.named<JavaCompile>(testApp.compileJavaTaskName) {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

tasks.compileTestJava {
    options.release = 18
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveFileName = "oehosts-agent-${project.version}.jar"
    from(jdk18.output)
    from("LICENSE")
    manifest {
        attributes(
            "Premain-Class" to "tricatch.oehosts.agent.Agent",
            "Implementation-Title" to "oeHosts-Agent",
            "Implementation-Version" to project.version,
        )
    }
}

// The agent tests start child JVMs (JDK 8, 11, 17 and the build JDK) with -javaagent:<the built jar>.
val testJdks = listOf(8, 11, 17)
tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar, testApp.classesTaskName)
    val agentJar = tasks.jar.flatMap { it.archiveFile }
    val appClasses = testApp.output.classesDirs
    val launchers = testJdks.associateWith { v ->
        javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(v) }.map { it.executablePath.asFile.absolutePath }
    }
    inputs.file(agentJar)
    inputs.files(appClasses)
    doFirst {
        systemProperty("oehosts.test.agentJar", agentJar.get().asFile.absolutePath)
        systemProperty("oehosts.test.appClasses", appClasses.asPath)
        launchers.forEach { (v, exe) -> systemProperty("oehosts.test.java.$v", exe.get()) }
    }
}
