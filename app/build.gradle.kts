plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.goudy.inventoryapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.goudy.inventoryapp"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room: on-device database for parts and audit history; chosen for its compile-checked SQL
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // RecyclerView: draws the scrolling inventory list, recycling row views so long lists stay smooth
    implementation(libs.recyclerview)

    // Lifecycle ViewModel/LiveData: keep a screen's data across rotation without reloading it
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // WorkManager: runs the low-stock alert in the background, surviving app close and reboot
    implementation(libs.work.runtime)

    // ZXing: decodes part barcodes from the camera for scan-to-receive and checkout
    implementation(libs.zxing.embedded)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}