import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("com.vanniktech.maven.publish")
}

val mavenPublishing = extensions.getByType<MavenPublishBaseExtension>()

mavenPublishing.apply {
    publishToMavenCentral()
    
    coordinates(
        project.group.toString(),
        project.name,
        project.version.toString()
    )

    pom {
        name.set("Parikshan - ${project.name}")
        description.set("End-to-end UI automation for Compose Multiplatform")
        url.set("https://github.com/aryapreetam/parikshan")
        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("aryapreetam")
                name.set("Preetam Bhosle")
            }
        }
        scm {
            url.set("https://github.com/aryapreetam/parikshan")
        }
    }

    if (project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }
}
