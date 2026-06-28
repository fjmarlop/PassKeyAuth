import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "es.fjmarlop.corpsecauth.ui"
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
    }

    buildFeatures {
        compose = true
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
}

dependencies {
    // Core module
    api(project(":passkeyauth-core"))

    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Testing
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// =====================================================================
// Publicación Maven Central (plugin vanniktech)
// =====================================================================
//
// Validación local (sin firma):
//   .\gradlew.bat :passkeyauth-ui:publishToMavenLocal
//
// Deploy a Central Portal (requiere credenciales + clave GPG, ver DEVELOPMENT.md):
//   .\gradlew.bat publishAndReleaseToMavenCentral
// =====================================================================
// Javadoc JAR vacío — mismo workaround que passkeyauth-core.
// javaDocReleaseGeneration falla con "PermittedSubclasses requires ASM9" en sealed classes.
val javadocJar = tasks.register<Jar>("emptyJavadocJar") {
    archiveClassifier.set("javadoc")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = false
        )
    )

    coordinates("io.github.fjmarlop", "passkeyauth-ui", libs.versions.passkeyauth.get())

    pom {
        name.set("PasskeyAuth UI")
        description.set(
            "Componentes Jetpack Compose para PasskeyAuth: pantallas de sign-in y " +
                "enrollment, launcher híbrido y theming zero-config."
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

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(javadocJar)
    }
}