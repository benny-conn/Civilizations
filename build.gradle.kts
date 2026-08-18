import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.4.10"
    id("com.gradleup.shadow") version "9.5.1"
}

group = "io.bennyc"
version = "0.0.16-BETA"

val paperVersion = providers.gradleProperty("paperVersion").get()
val sqliteJdbcVersion = "3.53.2.1"
val pluginMainClass = "io.bennyc.civilizations.CivilizationsPlugin"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")

    implementation(kotlin("stdlib"))

    // Civilizations owns its database driver instead of depending on Paper internals.
    implementation("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")

    testImplementation(kotlin("test"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.processResources {
    val resourceValues = mapOf(
        "version" to project.version,
        "mainClass" to pluginMainClass,
    )

    inputs.properties(resourceValues)
    filesMatching("plugin.yml") {
        expand(resourceValues)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    relocate("kotlin", "io.bennyc.civilizations.lib.kotlin")

    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<Copy>("deployTestServerPlugin") {
    group = "development"
    description = "Builds and copies Civilizations into the local ignored test server."
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("server/plugins"))
    rename { "Civilizations.jar" }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
