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

val mockitoAgent: Configuration by configurations.creating { isTransitive = false }

dependencies {
    compileOnly(libs.jspecify)

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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.github.sanyarnd.applocker")
    }
}

tasks.compileJava {
    options.release = 11
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.sanyarnd.applocker")
    }
}

tasks.test {
    useJUnitPlatform()
    timeout = Duration.ofMinutes(15)
    // JDK 21+ warns when Mockito attaches its agent dynamically
    val agent: FileCollection = mockitoAgent
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${agent.singleFile}") })
    testLogging {
        events("skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

spotless {
    java {
        palantirJavaFormat(
            libs.versions.palantir.java.format
                .get(),
        ).formatJavadoc(true)
        removeUnusedImports()
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
    // allows publishToMavenLocal without a key
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
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/sanyarnd/applocker")
            credentials(PasswordCredentials::class)
        }
    }
}
