import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    jacoco
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "es.fjmarlop.corpsecauth.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Habilitar coverage en debug para JaCoCo
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    // IMPORTANTE: Habilitar BuildConfig en library module
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    // Permitir instalacion de APKs de test (-t flag) via Gradle. Sin esto, algunos
    // devices (especialmente Samsung con Play Protect estricto) rechazan instalar
    // APKs de test con INSTALL_FAILED_VERIFICATION_FAILURE.
    // Documentado en ADR-011 y testing.md.
    installation {
        installOptions += listOf("-t")
    }

    // Resolver duplicados en META-INF al empaquetar tests instrumented.
    // Causa: dependencias transitivas (junit-jupiter, mockk) traen LICENSE.md propios.
    // Fix estandar de Android: excluirlos del APK de test (no afectan al SDK release).
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)

    implementation(libs.bundles.coroutines)
    implementation(libs.androidx.datastore.preferences)

    // Tests JVM puros: lo mas rapido, lo que mas usaremos.
    // Cubre: orquestacion (EnrollmentManager), mappers, modelos, sealed states.
    testImplementation(libs.bundles.testing.unit)

    // Tests JVM con Context Android via Robolectric: solo para SecureStorage.
    // NO usar para crypto/biometric (ver ADR-004 y ADR-011).
    testImplementation(libs.bundles.testing.robolectric)

    // Tests instrumentados en device: SOLO para validacion en hardware real
    // (AndroidKeyStore con StrongBox/TEE, BiometricPrompt smoke tests).
    androidTestImplementation(libs.bundles.testing.android)

    // Lint rules custom del SDK (ADR-012). lintPublish empaqueta las rules
    // en el AAR del core para que los consumers reciban las checks automaticamente
    // cuando ejecutan ./gradlew lint en su app.
    lintPublish(project(":passkeyauth-lint"))
}

// La configuracion lintPublish solo admite UN jar. Sin isTransitive=false,
// kotlin-stdlib (dependencia automatica del plugin kotlin-jvm) se anade
// como segunda entrada y falla el build (prepareLintJarForPublish).
// Las dependencias de runtime del modulo lint son provistas por el lint
// runner del consumer (que ya tiene Kotlin), no necesitamos empaquetarlas.
configurations.named("lintPublish") {
    isTransitive = false
}

// =====================================================================
// Publicación Maven Central (plugin vanniktech)
// =====================================================================
//
// Validación local (sin firma):
//   .\gradlew.bat :passkeyauth-core:publishToMavenLocal
//
// Deploy a Central Portal (requiere credenciales + clave GPG, ver DEVELOPMENT.md):
//   .\gradlew.bat publishAndReleaseToMavenCentral
//
// El plugin auto-detecta el módulo Android library y configura la variante
// release con sources + javadoc JAR (javadoc vacío al no haber Dokka — evita
// el bug "PermittedSubclasses requires ASM9" del generador de AGP).
// =====================================================================
// Javadoc JAR vacío. El generador de AGP 9 (javaDocReleaseGeneration con Dokka
// interno + ASM antiguo) falla con "PermittedSubclasses requires ASM9" en clases
// sealed. Desactivamos el javadoc de vanniktech (publishJavadocJar=false) y
// adjuntamos un jar vacío — válido para Central hasta integrar Dokka.
val javadocJar = tasks.register<Jar>("emptyJavadocJar") {
    archiveClassifier.set("javadoc")
}

mavenPublishing {
    // Central Portal es el host por defecto en vanniktech 0.30+.
    // automaticRelease=false (default): sube el deployment pero no lo libera —
    // se confirma manualmente en el portal tras validar.
    publishToMavenCentral()
    signAllPublications()

    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = false
        )
    )

    coordinates("io.github.fjmarlop", "passkeyauth-core", libs.versions.passkeyauth.get())

    pom {
        name.set("PasskeyAuth Core")
        description.set(
            "SDK Android de autenticación passwordless con biometría hardware-backed " +
                "y device binding. Backend-agnóstico (Firebase como default)."
        )
        url.set("https://github.com/fjmarlop/PassKeyAuth")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("fjmarlop")
                name.set("Francisco Javier Marmolejo López")
                url.set("https://github.com/fjmarlop")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/fjmarlop/PassKeyAuth.git")
            developerConnection.set("scm:git:ssh://github.com:fjmarlop/PassKeyAuth.git")
            url.set("https://github.com/fjmarlop/PassKeyAuth")
        }
    }
}

// Adjunta el javadoc JAR vacío a la publicación creada por vanniktech.
publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(javadocJar)
    }
}

// =====================================================================
// JaCoCo Coverage Report (ADR-011)
// =====================================================================
//
// Ejecucion:
//   .\gradlew.bat passkeyauth-core:jacocoTestReport
//
// Reportes:
//   build/reports/jacoco/jacocoTestReport/html/index.html
//   build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
//
// Exclusiones: data classes, exceptions, fakes, generated code, BuildConfig.
// Filosofia: medir cobertura del codigo "que hace cosas", no del que solo declara estructura.
// =====================================================================
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Genera reporte de cobertura JaCoCo para tests JVM unitarios."

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val excludes = listOf(
        // Modelos y data classes — no son logica
        "**/models/**",
        // Excepciones — wrappers de mensajes, no logica
        "**/errors/**",
        // Generated
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        // Tests y fakes
        "**/*Test*.*",
        "**/fakes/**",
        // Android framework
        "android/**/*.*",
        // Companion objects (factory methods)
        "**/*\$Companion.*"
    )

    classDirectories.setFrom(
        files(
            fileTree("$buildDir/tmp/kotlin-classes/debug") { exclude(excludes) },
            fileTree("$buildDir/intermediates/javac/debug/classes") { exclude(excludes) }
        )
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    // AGP 9 ubica el .exec bajo outputs/unit_test_code_coverage/, no bajo jacoco/.
    // El include cubre ambos por compatibilidad.
    executionData.setFrom(
        fileTree(buildDir) {
            include(
                "**/jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        }
    )
}
