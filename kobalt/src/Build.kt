import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.Project
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.archive.Archives
import com.beust.kobalt.buildScript
import com.beust.kobalt.file
import com.beust.kobalt.plugin.apt.apt
import com.beust.kobalt.plugin.apt.kapt
import com.beust.kobalt.plugin.java.javaCompiler
import com.beust.kobalt.plugin.kotlin.kotlinCompiler
import com.beust.kobalt.plugin.packaging.assemble
import com.beust.kobalt.project

val kobalt_version = "1.0.18"
val jenkins_version = "2.7.1"

val bs = buildScript {
    repos("https://repo.jenkins-ci.org/public/")
    //plugins(file(homeDir("src/github.com/rhencke/kobalt-jpi/kobaltBuild/libs/kobalt-jpi-0.1-SNAPSHOT.jar")))
}

val sezpoz = project {
    name = "sezpoz"
    artifactId = name
    group = "net.java.sezpoz"
    version = "1.12-kotlinfix"
    description = "SezPoz Library"
    directory = "sezpoz"
    javaCompiler {
        args("-proc:none")
    }
    kapt{}
    assemble {
        jar {}
    }
}

val p = project(sezpoz) {

    name = "pull-request-test-results-plugin"
    group = "com.github.rhencke"
    artifactId = name
    version = "1.0-SNAPSHOT"
    directory = "plugin"


    dependencies {
        //apt(file("sezpoz/kobaltBuild/libs/sezpoz-1.12-kotlinfix.jar"))

        // Work around https://youtrack.jetbrains.com/issue/KT-16931
        provided(file("lib/jenkins-core-justclasses-$jenkins_version.jar"))

        provided("org.jenkins-ci.main:jenkins-core:$jenkins_version")

        native("org.jenkins-ci.main:jenkins-war:$jenkins_version")

        compile("org.jenkins-ci.plugins:junit:1.19")

    }

    dependenciesTest {
        compile("org.testng:testng:6.9.9")
    }

    assemble {
        war {
            name = Archives.defaultArchiveName(project) + ".jpi"
            manifest {
                attributes("Group-Id", group!!)
                attributes("Short-Name", name)
                attributes("Long-Name", name) // Display name - TODO
                attributes("Extension-Name", name)
                attributes("Plugin-Version", version!!)
                attributes("Jenkins-Version", jenkins_version)
                attributes("Plugin-Dependencies", "junit:1.19") // TODO: Derive from deps/optional deps.
            }
        }
    }
}
