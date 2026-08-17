plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "eu.xnull.airkeys"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.xnull.airkeys"
        minSdk = 23
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat) { version { strictly("1.7.0") } }
    implementation(libs.material) { version { strictly("1.12.0") } }
    implementation(libs.activity) { version { strictly("1.9.3") } }
    implementation(libs.constraintlayout) { version { strictly("2.2.0") } }
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
