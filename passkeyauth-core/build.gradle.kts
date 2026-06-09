plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    jacoco
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
