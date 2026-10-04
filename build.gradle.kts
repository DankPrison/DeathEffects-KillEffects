import com.dank420girl.build.RelocatedJar

plugins {
    base
}

/**
 * A plugin built from the core. Everything else that differs between the plugins is in the core's Variant
 * class (a test checks that both agree); the plugins' own folders only hold the main class and the API events.
 */
class PluginVariant(val name: String, val alias: String, val kind: String, val description: String) {
    val id = name.lowercase()
}

val pluginVariants = mapOf(
    "deatheffects" to PluginVariant("DeathEffects", "de", "death", "Players pick an effect that plays where they die."),
    "killeffects" to PluginVariant("KillEffects", "ke", "kill", "Players pick an effect that plays where their victims die."),
)

allprojects {
    group = property("group") as String
    version = property("version") as String
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            content { includeGroup("org.spigotmc") }
        }
    }

    dependencies {
        // Compiling against the oldest widely available API keeps the plugins working on every version;
        // anything newer is reached through reflection after checking that the server supports it.
        "compileOnly"("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") { isTransitive = false }
        // Only needed so the compiler can resolve chat component overloads of the API.
        "compileOnly"("net.md-5:bungeecord-chat:1.16-R0.4") { isTransitive = false }

        "testImplementation"("junit:junit:4.13.2")
        "testImplementation"("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") { isTransitive = false }
        "testImplementation"("net.md-5:bungeecord-chat:1.16-R0.4") { isTransitive = false }
        "testRuntimeOnly"("org.yaml:snakeyaml:1.33")
        "testRuntimeOnly"("com.google.guava:guava:21.0")
        "testRuntimeOnly"("commons-lang:commons-lang:2.6")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options", "-Xlint:-deprecation", "-Xlint:-processing"))
    }

    tasks.withType<Test>().configureEach {
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

project(":core") {
    // Test helpers shared with the plugin modules: name lists of several Minecraft versions.
    apply(plugin = "java-test-fixtures")
    dependencies {
        "testFixturesCompileOnly"("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") { isTransitive = false }
    }
}

// Each plugin jar contains its own copy of the core, moved into the plugin's package, so both plugins can
// run on one server (even in different versions) without sharing any classes.
configure(subprojects.filter { it.name in pluginVariants.keys }) {
    val variant = pluginVariants.getValue(name)
    val pluginName = variant.name
    val pluginPackage = "com.dank420girl.$name"
    val coreProject = project(":core")

    dependencies {
        "implementation"(coreProject)
        "testImplementation"(testFixtures(coreProject))
    }

    tasks.named<ProcessResources>("processResources") {
        // One plugin.yml for both plugins, filled in for this one.
        val properties = mapOf(
            "version" to project.version.toString(),
            "name" to variant.name,
            "id" to variant.id,
            "alias" to variant.alias,
            "singular" to variant.id.removeSuffix("s"),
            "kind" to variant.kind,
            "description" to variant.description,
        )
        inputs.properties(properties)
        from(coreProject.file("src/main/templates/plugin.yml")) {
            expand(properties)
        }
        // The jars contain code and data based on EffectLib and XSeries, whose MIT licenses ask for their notices.
        from(rootProject.files("LICENSE", "THIRD-PARTY-NOTICES.md")) {
            into("META-INF")
        }
    }

    // The plain jar without the shared code is only useful inside the build.
    tasks.named<Jar>("jar") {
        archiveClassifier.set("plain")
    }

    val sourceSets = extensions.getByType<SourceSetContainer>()
    val pluginJar = tasks.register<RelocatedJar>("pluginJar") {
        group = "build"
        description = "Builds $pluginName.jar with the shared code moved into the plugin's own package."
        sources.from(sourceSets.getByName("main").output)
        sources.from(coreProject.tasks.named("jar"))
        fromPackage.set("com.dank420girl.core")
        toPackage.set("$pluginPackage.core")
        manifestAttributes.put("Implementation-Title", pluginName)
        manifestAttributes.put("Implementation-Version", project.version.toString())
        // No net.minecraft classes are linked, so Paper does not need to remap the jar.
        manifestAttributes.put("paperweight-mappings-namespace", "mojang")
        archiveFile.set(rootProject.layout.buildDirectory.file("libs/$pluginName-${project.version}.jar"))
    }

    tasks.named("assemble") {
        dependsOn(pluginJar)
    }
}
