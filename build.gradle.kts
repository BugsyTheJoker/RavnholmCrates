
plugins {
    java
}

// ============================================================
// RAVNHOLM CRATES
// ============================================================

group = "dk.ravnholm"
version = "1.0.0"

// ============================================================
// REPOSITORIES
// ============================================================

repositories {

    mavenCentral()

    maven(
        "https://repo.papermc.io/repository/maven-public/"
    )
}

// ============================================================
// DEPENDENCIES
// ============================================================

dependencies {

    // Paper API
    compileOnly(
        "io.papermc.paper:paper-api:26.2.build.+"
    )

    // FancyHolograms 2.12.0
    // Hentes fra projektets libs-mappe.
    // Pakkes IKKE ind i RavnholmCrates.jar.
    compileOnly(
        files("libs/FancyHolograms-2.12.0.jar")
    )
}

// ============================================================
// JAVA
// ============================================================

java {

    toolchain.languageVersion.set(
        JavaLanguageVersion.of(25)
    )
}

// ============================================================
// KOMPILERING
// ============================================================

tasks.withType<JavaCompile> {

    options.encoding = "UTF-8"
}
