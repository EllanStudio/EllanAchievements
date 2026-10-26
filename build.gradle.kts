plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
}

allprojects {
    group = "dev.ellan.achievements"
    version = providers.gradleProperty("pluginVersion").orElse("1.0.0").get()

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.nightexpressdev.com/releases")
        maven("https://repo.momirealms.net/releases/")
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(25)
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(25)
            options.encoding = "UTF-8"
            options.compilerArgs.add("-parameters")
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
