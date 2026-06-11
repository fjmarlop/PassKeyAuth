plugins {
    `java-library`
    // Kotlin Android plugin ya esta en classpath via otros modulos. Aplicamos
    // kotlin-jvm sin version (Gradle resuelve la del classpath compartido).
    id("org.jetbrains.kotlin.jvm")
}

// Modulo de Lint rules custom para enforcing del contrato del PasskeyAuth SDK.
// JVM-only (no Android library) — las rules se ejecutan en el lint runner, no en device.
// Se distribuye a consumers via `lintPublish` en passkeyauth-core.
//
// Ver ADR-012 para justificacion y catalogo de rules.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.lint.api)

    // Tests
    testImplementation(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

// Crear el service file IssueRegistry en META-INF/services tras `jar`.
// La AlternativeRoute es usar `@AutoService` con kapt, pero anade tooling
// pesado para una sola entrada. Mejor escribir el service file a mano.
tasks.jar {
    manifest {
        attributes(
            "Lint-Registry-v2" to "es.fjmarlop.corpsecauth.lint.PasskeyAuthIssueRegistry"
        )
    }
}
