plugins {
    java
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))

    compileOnly("io.papermc.paper:paper-api:26.2.build.135-stable")
    compileOnly("net.momirealms:craft-engine-core:26.9.1")
    compileOnly("net.momirealms:craft-engine-bukkit:26.9.1")
    compileOnly("net.momirealms:custom-fishing:2.3.24")
    compileOnly("net.momirealms:custom-crops:3.6.52")
    compileOnly("su.nightexpress.excellenteconomy:ExcellentEconomy:2.8.0")

    testImplementation("io.papermc.paper:paper-api:26.2.build.135-stable")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("EllanAchievements")
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
