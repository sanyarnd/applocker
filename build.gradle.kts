import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import java.time.Duration

plugins {
    `java-library`
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.maven.publish)
}

/** JDK used to run the tests; defaults to the JDK running Gradle. */
val testJavaVersion: Provider<String> = providers.gradleProperty("testJavaVersion")

val mockitoAgent: Configuration by configurations.creating { isTransitive = false }

dependencies {
    // annotations only, not needed at runtime and not exposed as a transitive dependency
    compileOnly(libs.jspecify)
    testCompileOnly(libs.jspecify)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.instancio.junit)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    mockitoAgent(libs.mockito.core)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // "-options" silences warnings about old --release values on modern JDKs
    options.compilerArgs.addAll(listOf("-Xlint:all,-options,-processing", "-Werror", "-parameters"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        allErrorsAsWarnings = false
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.github.sanyarnd.applocker")
    }
}

tasks.compileJava {
    options.release = 11
}

tasks.compileTestJava {
    // JUnit 6 and Mockito 5 require Java 17+, the library itself stays Java 11 compatible
    options.release = 17
    // tests deliberately misuse the API (nulls, wrong types), so only Error Prone checks remain enabled
    options.errorprone.disable("NullAway")
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        source = "11"
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
        addStringOption("Xmaxwarns", "1000")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "io.github.sanyarnd.applocker",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}

tasks.test {
    useJUnitPlatform()
    // safety net: a hanging socket must never block CI for hours
    timeout = Duration.ofMinutes(15)
    // Mockito's inline mock maker must be attached as an agent on JDK 21+
    val agent: FileCollection = mockitoAgent
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${agent.singleFile}", "-Xshare:off") })
    if (testJavaVersion.isPresent) {
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(testJavaVersion.get())
            }
    }
    testLogging {
        events("skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

spotless {
    java {
        palantirJavaFormat(
            libs.versions.palantir.java.format
                .get(),
        ).formatJavadoc(true)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target(".gitignore", ".gitattributes", ".editorconfig", "gradle/*.toml", ".github/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    // signing is mandatory for Maven Central, but optional for local builds (publishToMavenLocal)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates(project.group.toString(), project.name, project.version.toString())

    pom {
        name = "Application Locker"
        description = "Provides a file-based locking mechanism with inter-process communication support"
        inceptionYear = "2019"
        url = "https://github.com/sanyarnd/applocker"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "sanyarnd"
                name = "Alexander Biryukov"
                email = "sanya.rnd@gmail.com"
                url = "https://github.com/sanyarnd"
            }
        }
        scm {
            url = "https://github.com/sanyarnd/applocker"
            connection = "scm:git:https://github.com/sanyarnd/applocker.git"
            developerConnection = "scm:git:ssh://git@github.com/sanyarnd/applocker.git"
        }
        issueManagement {
            system = "GitHub"
            url = "https://github.com/sanyarnd/applocker/issues"
        }
    }
}

publishing {
    repositories {
        // mirror of the Maven Central release, credentials are provided by GitHub Actions
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/sanyarnd/applocker")
            credentials(PasswordCredentials::class)
        }
    }
}
