import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Segredos de build: variáveis de ambiente (CI) ou local.properties (máquina local).
// Nada disso vai para o git.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(env: String, prop: String, default: String = ""): String =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(prop)?.takeIf { it.isNotBlank() }
        ?: default

fun String.asBuildConfigString() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseKeystore = secret("KEYSTORE_FILE", "release.storeFile")

android {
    namespace = "com.controleinfantil.kids"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.controleinfantil.kids"
        minSdk = 26          // Android 8.0 — necessário para várias APIs de Device Owner
        targetSdk = 34
        // A CI passa a versão (número do build e a tag); localmente fica o padrão.
        versionCode = secret("VERSION_CODE", "versionCode", "1").toInt()
        versionName = secret("VERSION_NAME", "versionName", "0.1.0")

        // Os placeholders mantêm o app compilando sem configuração; ele só avisa que
        // o Supabase não foi configurado (ver SupabaseClient.isConfigured).
        buildConfigField("String", "SUPABASE_URL",
            secret("SUPABASE_URL", "supabase.url", "https://SEU-PROJETO.supabase.co").asBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY",
            secret("SUPABASE_ANON_KEY", "supabase.anonKey", "COLE_SUA_ANON_KEY_AQUI").asBuildConfigString())
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore.isNotEmpty()) {
                storeFile = file(releaseKeystore)
                storePassword = secret("KEYSTORE_PASSWORD", "release.storePassword")
                keyAlias = secret("KEY_ALIAS", "release.keyAlias")
                keyPassword = secret("KEY_PASSWORD", "release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Sem keystore configurado o release sai sem assinatura (não instala);
            // a CI sempre fornece a chave.
            if (releaseKeystore.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Corrotinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Localização
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Rede (chamadas REST ao Supabase)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    // WebRTC (fork mantido; o Google parou de publicar o org.webrtc no Maven).
    // A versão pode precisar de ajuste ao compilar — ver README.
    implementation("io.github.webrtc-sdk:android:125.6422.07")
}
