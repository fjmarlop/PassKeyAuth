# PasskeyAuth v2 - Setup Script (Windows)
# Package: es.fjmarlop.corpsecauth

Write-Host "Iniciando setup de PasskeyAuth v2..." -ForegroundColor Green
Write-Host "Directorio: $PWD" -ForegroundColor Cyan

$BASE_PACKAGE = "es/fjmarlop/corpsecauth"

# ============================================================================
# 1. CREAR ESTRUCTURA DE MODULOS
# ============================================================================

Write-Host "`nCreando modulos..." -ForegroundColor Yellow

# Modulo: passkeyauth-core
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/main/java/$BASE_PACKAGE/core" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/main/java/$BASE_PACKAGE/core/auth" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/main/java/$BASE_PACKAGE/core/crypto" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/main/java/$BASE_PACKAGE/core/firebase" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/main/java/$BASE_PACKAGE/core/models" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/main/java/$BASE_PACKAGE/core/errors" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/main/java/$BASE_PACKAGE/core/storage" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/test/java/$BASE_PACKAGE/core" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/androidTest/java/$BASE_PACKAGE/core" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-core/src/main/res/values" | Out-Null

Write-Host "  OK passkeyauth-core" -ForegroundColor Green

# Modulo: passkeyauth-ui
New-Item -ItemType Directory -Force -Path "passkeyauth-ui/src/main/java/$BASE_PACKAGE/ui" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-ui/src/main/java/$BASE_PACKAGE/ui/components" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-ui/src/main/java/$BASE_PACKAGE/ui/screens" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-ui/src/main/java/$BASE_PACKAGE/ui/theme" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-ui/src/main/res/values" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-ui/src/main/res/drawable" | Out-Null
New-Item -ItemType Directory -Force -Path "passkeyauth-ui/src/test/java/$BASE_PACKAGE/ui" | Out-Null

Write-Host "  OK passkeyauth-ui" -ForegroundColor Green

# Modulo: sample
New-Item -ItemType Directory -Force -Path "sample/src/main/java/$BASE_PACKAGE/sample" | Out-Null
New-Item -ItemType Directory -Force -Path "sample/src/main/java/$BASE_PACKAGE/sample/ui" | Out-Null
New-Item -ItemType Directory -Force -Path "sample/src/main/res/values" | Out-Null
New-Item -ItemType Directory -Force -Path "sample/src/main/res/drawable" | Out-Null

Write-Host "  OK sample" -ForegroundColor Green

# Documentacion
New-Item -ItemType Directory -Force -Path "docs/adr" | Out-Null
New-Item -ItemType Directory -Force -Path "docs/security" | Out-Null
New-Item -ItemType Directory -Force -Path "docs/api" | Out-Null

Write-Host "  OK docs/" -ForegroundColor Green

# ============================================================================
# 2. SETTINGS.GRADLE.KTS
# ============================================================================

Write-Host "`nGenerando settings.gradle.kts..." -ForegroundColor Yellow

$settingsContent = @'
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PasskeyAuth"

include(":passkeyauth-core")
include(":passkeyauth-ui")
include(":sample")
'@

[System.IO.File]::WriteAllText("$PWD\settings.gradle.kts", $settingsContent)

# ============================================================================
# 3. GRADLE.PROPERTIES
# ============================================================================

Write-Host "Generando gradle.properties..." -ForegroundColor Yellow

$gradlePropsContent = @'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

android.useAndroidX=true
android.enableJetifier=false

kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx2048m

android.nonTransitiveRClass=true
android.nonFinalResIds=false

GROUP=es.fjmarlop.passkeyauth
VERSION_NAME=0.1.0-SNAPSHOT
POM_DESCRIPTION=Passwordless Authentication SDK for Android
POM_URL=https://github.com/fjmarlop/PasskeyAuth
POM_LICENCE_NAME=Apache License, Version 2.0
POM_LICENCE_URL=https://www.apache.org/licenses/LICENSE-2.0.txt
POM_DEVELOPER_ID=fjmarlop
POM_DEVELOPER_NAME=Francisco Martinez Lopez
'@

[System.IO.File]::WriteAllText("$PWD\gradle.properties", $gradlePropsContent)

# ============================================================================
# 4. LIBS.VERSIONS.TOML
# ============================================================================

Write-Host "Generando libs.versions.toml..." -ForegroundColor Yellow

New-Item -ItemType Directory -Force -Path "gradle" | Out-Null

$tomlContent = @'
[versions]
minSdk = "26"
targetSdk = "35"
compileSdk = "35"

agp = "8.7.3"
kotlin = "2.1.0"

coreKtx = "1.15.0"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
appcompat = "1.7.0"
material = "1.12.0"

composeBom = "2024.12.01"
materialIconsExtended = "1.7.6"
navigationCompose = "2.8.5"

kotlinxCoroutines = "1.9.0"
kotlinxSerialization = "1.7.3"

firebaseBom = "33.7.0"
googleServices = "4.4.2"
securityCrypto = "1.1.0-alpha06"
biometric = "1.2.0-alpha05"

datastore = "1.1.1"

junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"
mockk = "1.13.13"
turbine = "1.2.0"
coroutinesTest = "1.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }

androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended", version.ref = "materialIconsExtended" }

androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
androidx-biometric = { group = "androidx.biometric", name = "biometric", version.ref = "biometric" }

androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[bundles]
compose = [
    "androidx-compose-ui",
    "androidx-compose-ui-graphics",
    "androidx-compose-ui-tooling-preview",
    "androidx-compose-material3",
    "androidx-compose-material-icons-extended"
]

compose-debug = [
    "androidx-compose-ui-tooling",
    "androidx-compose-ui-test-manifest"
]

firebase = [
    "firebase-auth",
    "firebase-firestore"
]

coroutines = [
    "kotlinx-coroutines-core",
    "kotlinx-coroutines-android"
]

testing-unit = [
    "junit",
    "mockk",
    "kotlinx-coroutines-test",
    "turbine"
]

testing-android = [
    "androidx-junit",
    "androidx-espresso-core",
    "mockk-android"
]

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
'@

[System.IO.File]::WriteAllText("$PWD\gradle\libs.versions.toml", $tomlContent)

# ============================================================================
# 5. BUILD.GRADLE.KTS RAIZ
# ============================================================================

Write-Host "Generando build.gradle.kts raiz..." -ForegroundColor Yellow

$rootBuildContent = @'
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
'@

[System.IO.File]::WriteAllText("$PWD\build.gradle.kts", $rootBuildContent)

# ============================================================================
# 6. BUILD CORE
# ============================================================================

Write-Host "Generando passkeyauth-core/build.gradle.kts..." -ForegroundColor Yellow

$coreBuildContent = @'
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
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
    
    testImplementation(libs.bundles.testing.unit)
    androidTestImplementation(libs.bundles.testing.android)
}
'@

[System.IO.File]::WriteAllText("$PWD\passkeyauth-core\build.gradle.kts", $coreBuildContent)

# ============================================================================
# 7. BUILD UI
# ============================================================================

Write-Host "Generando passkeyauth-ui/build.gradle.kts..." -ForegroundColor Yellow

$uiBuildContent = @'
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":passkeyauth-core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    debugImplementation(libs.bundles.compose.debug)

    testImplementation(libs.bundles.testing.unit)
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
'@

[System.IO.File]::WriteAllText("$PWD\passkeyauth-ui\build.gradle.kts", $uiBuildContent)

# ============================================================================
# 8. BUILD SAMPLE
# ============================================================================

Write-Host "Generando sample/build.gradle.kts..." -ForegroundColor Yellow

$sampleBuildContent = @'
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "es.fjmarlop.corpsecauth.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "es.fjmarlop.corpsecauth.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":passkeyauth-core"))
    implementation(project(":passkeyauth-ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material)

    debugImplementation(libs.bundles.compose.debug)

    testImplementation(libs.bundles.testing.unit)
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
'@

[System.IO.File]::WriteAllText("$PWD\sample\build.gradle.kts", $sampleBuildContent)

# ============================================================================
# 9. MANIFESTS
# ============================================================================

Write-Host "Generando AndroidManifest.xml..." -ForegroundColor Yellow

$coreManifest = @'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.INTERNET" />
</manifest>
'@

$uiManifest = @'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
'@

$sampleManifest = @'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.PasskeyAuthSample">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PasskeyAuthSample">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
'@

[System.IO.File]::WriteAllText("$PWD\passkeyauth-core\src\main\AndroidManifest.xml", $coreManifest)
[System.IO.File]::WriteAllText("$PWD\passkeyauth-ui\src\main\AndroidManifest.xml", $uiManifest)
[System.IO.File]::WriteAllText("$PWD\sample\src\main\AndroidManifest.xml", $sampleManifest)

# ============================================================================
# 10. PROGUARD
# ============================================================================

Write-Host "Generando ProGuard rules..." -ForegroundColor Yellow

$coreProguard = @'
-keep public class es.fjmarlop.corpsecauth.core.PasskeyAuth { *; }
-keep public class es.fjmarlop.corpsecauth.core.models.** { *; }
-keep public class es.fjmarlop.corpsecauth.core.errors.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
'@

$coreConsumer = '-keep public class es.fjmarlop.corpsecauth.core.PasskeyAuth { *; }'
$uiConsumer = '-keep public class es.fjmarlop.corpsecauth.ui.PasskeyAuthUI { *; }'

[System.IO.File]::WriteAllText("$PWD\passkeyauth-core\proguard-rules.pro", $coreProguard)
[System.IO.File]::WriteAllText("$PWD\passkeyauth-core\consumer-rules.pro", $coreConsumer)
[System.IO.File]::WriteAllText("$PWD\passkeyauth-ui\consumer-rules.pro", $uiConsumer)

# ============================================================================
# 11. STRINGS
# ============================================================================

Write-Host "Generando strings.xml..." -ForegroundColor Yellow

$coreStrings = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="passkeyauth_error_generic">Error de autenticacion</string>
    <string name="passkeyauth_error_no_biometric">Biometria no disponible</string>
</resources>
'@

$uiStrings = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="enrollment_title">Configurar Autenticacion</string>
    <string name="biometric_prompt_title">Verificacion Biometrica</string>
</resources>
'@

$sampleStrings = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">PasskeyAuth Sample</string>
</resources>
'@

[System.IO.File]::WriteAllText("$PWD\passkeyauth-core\src\main\res\values\strings.xml", $coreStrings)
[System.IO.File]::WriteAllText("$PWD\passkeyauth-ui\src\main\res\values\strings.xml", $uiStrings)
[System.IO.File]::WriteAllText("$PWD\sample\src\main\res\values\strings.xml", $sampleStrings)

# ============================================================================
# 12. DOCS
# ============================================================================

Write-Host "Generando documentacion..." -ForegroundColor Yellow

$adrTemplate = @'
# ADR-XXX: [Titulo]

**Fecha:** YYYY-MM-DD
**Estado:** Propuesto

## Contexto
[Descripcion]

## Decision
[Opcion elegida]

## Consecuencias
[Pros y contras]
'@

$adr001 = @'
# ADR-001: Estructura Multi-Modulo

**Fecha:** 2026-01-17
**Estado:** Aceptado

## Contexto
Libreria Android con logica separada de UI.

## Decision
3 modulos: passkeyauth-core, passkeyauth-ui, sample

## Consecuencias
+ Consumidores eligen core solo o core+ui
+ Testing simplificado
- Mayor complejidad inicial
'@

$readme = @'
# PasskeyAuth - Android Passwordless SDK

Autenticacion sin contrasenias con biometria y Firebase.

## Caracteristicas

- Autenticacion biometrica
- Cifrado hardware-backed
- UI opcional Compose

## Instalacion
```gradle
dependencies {
    implementation("es.fjmarlop.passkeyauth:core:0.1.0")
    implementation("es.fjmarlop.passkeyauth:ui:0.1.0")
}
```

## Estado

Version: 0.1.0-SNAPSHOT (en desarrollo)

## Licencia

Apache License 2.0
'@

$changelog = @'
# Changelog

## [No Publicado]

### Aniadido
- Estructura multi-modulo inicial
'@

[System.IO.File]::WriteAllText("$PWD\docs\adr\template.md", $adrTemplate)
[System.IO.File]::WriteAllText("$PWD\docs\adr\001-module-structure.md", $adr001)
[System.IO.File]::WriteAllText("$PWD\README.md", $readme)
[System.IO.File]::WriteAllText("$PWD\CHANGELOG.md", $changelog)

# ============================================================================
# 13. GITIGNORE
# ============================================================================

Write-Host "Generando .gitignore..." -ForegroundColor Yellow

$gitignoreContent = @'
*.apk
*.aar
*.dex
*.class
bin/
gen/
out/
.gradle/
build/
local.properties
*.iml
.idea/
*.jks
*.keystore
.externalNativeBuild
.cxx/
google-services.json
sample/google-services.json
.DS_Store
*~
*.swp
*.bak
'@

[System.IO.File]::WriteAllText("$PWD\.gitignore", $gitignoreContent)

# ============================================================================
# FINALIZACION
# ============================================================================

Write-Host "`nSetup completado!" -ForegroundColor Green
Write-Host "`nSiguientes pasos:" -ForegroundColor Cyan
Write-Host "1. Eliminar modulo app: Remove-Item -Recurse -Force app/" -ForegroundColor White
Write-Host "2. Sync Gradle en Android Studio" -ForegroundColor White
Write-Host "3. Build: .\gradlew.bat build" -ForegroundColor White
Write-Host "`nListo para codificar!" -ForegroundColor Green