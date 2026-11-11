import com.diffplug.spotless.LineEnding
import com.diffplug.spotless.kotlin.KtfmtStep
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    id("com.diffplug.spotless") version "8.11.0"
    id("com.gradle.plugin-publish") version "2.2.1"
    id("org.gradle.plugin-compatibility") version "1.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.11"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
    options.release = 8
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    compilerOptions {
        explicitApi()
        moduleName = project.name
        apiVersion = KotlinVersion.KOTLIN_1_9
        languageVersion = KotlinVersion.KOTLIN_1_9
    }
}
tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions {
        javaParameters = true
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.assertj:assertj-core:3.27.7")

    testImplementation(gradleTestKit())
}

testing {
    suites {
        val test = getByName<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

configurations.getByName("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}

configurations.getByName("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

sourceSets["integrationTest"].apply {
    compileClasspath += sourceSets["main"].output + sourceSets["test"].output
    runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
}

tasks.withType<Test>().configureEach {
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

tasks.named<Test>("integrationTest") {
    providers.gradleProperty("compat.gradle.version").orNull?.let {
        systemProperty("compat.gradle.version", it)
    }
}

gradlePlugin {
    testSourceSets(sourceSets["test"], sourceSets["integrationTest"])
    website = "https://github.com/damianmalczewski/downshift-gradle-plugin"
    vcsUrl = "https://github.com/damianmalczewski/downshift-gradle-plugin.git"
    plugins {
        create("downshift") {
            id = "io.github.malczuuu.downshift"
            implementationClass = "io.github.malczuuu.downshift.DownshiftPlugin"
            displayName = "Downshift Gradle Plugin"
            description =
                "Gradle plugin running already compiled tests against older lines of BOM-managed dependencies."
            tags = listOf("testing", "compatibility", "BOM", "platform", "dependencies")
            compatibility {
                features {
                    configurationCache = true
                    isolatedProjects = true
                }
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "Downshift Gradle Plugin"
            description =
                "Gradle plugin running already compiled tests against older lines of BOM-managed dependencies."
            url = "https://github.com/damianmalczewski/downshift-gradle-plugin"
            inceptionYear = "2026"
            licenses {
                license {
                    name = "The Apache License, Version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            developers {
                developer {
                    id = "damianmalczewski"
                    name = "Damian Malczewski"
                    url = "https://github.com/damianmalczewski"
                }
            }
            scm {
                connection = "scm:git:git://github.com/damianmalczewski/downshift-gradle-plugin.git"
                developerConnection = "scm:git:ssh://github.com/damianmalczewski/downshift-gradle-plugin.git"
                url = "https://github.com/damianmalczewski/downshift-gradle-plugin"
            }
            issueManagement {
                system = "GitHub Issues"
                url = "https://github.com/damianmalczewski/downshift-gradle-plugin/issues"
            }
        }
    }
}

if (getBooleanProperty("sign")) {
    signing {
        useInMemoryPgpKeys(System.getenv("SIGNING_KEY"), System.getenv("SIGNING_PASSWORD"))
        sign(publishing.publications)
    }
} else {
    tasks.withType<Sign>().configureEach {
        enabled = false
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        licenseHeaderFile("${rootProject.rootDir}/gradle/LicenseHeader.kt")

        ktfmt().metaStyle().configure {
            it.setMaxWidth(120)
            it.setRemoveUnusedImports(true)
            it.setTrailingCommaManagementStrategy(KtfmtStep.TrailingCommaManagementStrategy.COMPLETE)
        }
        endWithNewline()
        lineEndings = LineEnding.UNIX
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")

        ktlint().editorConfigOverride(mapOf("max_line_length" to "120"))
        endWithNewline()
        lineEndings = LineEnding.UNIX
    }
}

tasks.withType<Jar>().configureEach {
    if (name == "javadocJar") {
        enabled = false
    }
    manifest {
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] = project.version
        attributes["Created-By"] = "Gradle ${gradle.gradleVersion}"
    }
    from("${rootProject.rootDir}/LICENSE") {
        into("META-INF/")
        rename { "LICENSE.txt" }
    }
}

// coverage is measured by unit tests only, integration tests run the plugin in separate TestKit builds
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("integrationTest")
        }
    }
}

tasks.named<Task>("check") {
    finalizedBy(tasks.named("koverHtmlReport"), tasks.named("koverXmlReport"))
}

tasks.register("allTest") {
    description = "Runs all tests (unit + integration)."
    group = "verification"
    dependsOn(tasks.named<Test>("test"), tasks.named<Test>("integrationTest"))
}

defaultTasks("spotlessApply", "build")

fun getBooleanProperty(name: String, defaultValue: Boolean = false): Boolean =
    if (hasProperty(name)) findProperty(name)?.toString() != "false" else defaultValue
