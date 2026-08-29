import com.android.tools.build.apkzlib.zip.ZFile
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.support.listFilesOrdered
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    signing
}

group = "app.revanced.bilibili"

dependencies {
    implementation(libs.revanced.patcher)
    implementation(libs.smali)
    // Used in JsonGenerator.
    implementation(libs.gson)
}

tasks.jar {
    archiveBaseName = "${rootProject.name}-${project.name}"
    exclude("app/revanced/generator")

    manifest {
        attributes["Name"] = "BiliRoamingZQ Patches"
        attributes["Description"] = "Patches for BiliRoamingZQ."
        attributes["Version"] = version
        attributes["Timestamp"] = System.currentTimeMillis().toString()
        attributes["Source"] = "git@github.com:HSSkyBoy/BiliRoamingZQ.git"
        attributes["Author"] = "Kofua"
        attributes["License"] = "GNU General Public License v3.0"
    }
}

tasks.register("buildDexJar") {
    description = "Build and add a DEX to the JAR file"
    group = "build"

    dependsOn(tasks.build)

    doLast {
        val d8Name = OperatingSystem.current().getScriptName("d8")
        val sdkDir = System.getenv("ANDROID_HOME").orEmpty().ifEmpty {
            rootProject.file("local.properties").takeIf { it.exists() }
                ?.inputStream()?.let { Properties().apply { load(it) } }
                ?.getProperty("sdk.dir")
        }.orEmpty().ifEmpty { error("Android sdk not found.") }
        val d8 = File(sdkDir).resolve("build-tools")
            .listFilesOrdered().last().resolve(d8Name).absolutePath

        val patchesJar = configurations.archives.get().allArtifacts.files.files.first().absolutePath
        val workingDirectory = layout.buildDirectory.dir("libs").get().asFile

        exec {
            workingDir = workingDirectory
            val classpath = configurations.runtimeClasspath.get().files.flatMap { listOf("--classpath", it.absolutePath) }
            commandLine = listOf(d8, "--release") + classpath + patchesJar
        }

        ZFile.openReadWrite(File(patchesJar)).use {
            it.add("classes.dex", File(workingDirectory, "classes.dex").inputStream())
        }
    }
}

tasks.register<JavaExec>("generatePatchesFiles") {
    description = "Generate patches files"

    dependsOn(tasks.build)

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.revanced.generator.Main")
}

tasks.publish {
    dependsOn("buildDexJar")
    dependsOn("generatePatchesFiles")
}

tasks.register("dist") {
    group = "build"
    dependsOn("buildDexJar")
    dependsOn("generatePatchesFiles")
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/HSSkyBoy/BiliRoamingZQ")
            credentials {
                username = project.findProperty("gpr.user") as String?
                    ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String?
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("revanced-patches-publication") {
            from(components["java"])

            pom {
                name = "BiliRoamingZQ Patches"
                description = "Patches for BiliRoamingZQ."

                licenses {
                    license {
                        name = "GNU General Public License v3.0"
                        url = "https://www.gnu.org/licenses/gpl-3.0.en.html"
                    }
                }
                developers {
                    developer {
                        id = "zjns"
                        name = "Kofua"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/HSSkyBoy/BiliRoamingZQ.git"
                    developerConnection = "scm:git:git@github.com:HSSkyBoy/BiliRoamingZQ.git"
                    url = "https://github.com/HSSkyBoy/BiliRoamingZQ"
                }
            }
        }
    }
}

signing {
    useGpgCmd()

    sign(publishing.publications["revanced-patches-publication"])
}
