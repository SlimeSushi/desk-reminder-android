plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

android {
    namespace = "cn.deskreminder.mobile"
    compileSdk = 36
    defaultConfig {
        applicationId = "cn.deskreminder.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(".signing/deskreminder-mobile.jks")
            storePassword = System.getenv("DESK_ANDROID_KEYSTORE_PASSWORD") ?: ""
            keyAlias = "deskreminder-mobile"
            keyPassword = System.getenv("DESK_ANDROID_KEYSTORE_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true; signingConfig = signingConfigs.getByName("release"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }
    testOptions.unitTests.all { it.maxHeapSize="2g"; it.systemProperty("robolectric.dependency.repo.url","https://repo.maven.apache.org/maven2"); it.systemProperty("user.timezone","Asia/Shanghai") }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom); androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.room:room-runtime:2.8.5")
    annotationProcessor("androidx.room:room-compiler:2.8.5")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
