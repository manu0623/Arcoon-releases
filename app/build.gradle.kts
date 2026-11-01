import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

// ---- Arcoon: build numbers ----
// Every customRelease build gets the next number (Arcoon 3.0.1, 3.0.2, …), so the app can tell a
// newer build apart and offer it as an update.
val arcoonVersionFile = rootProject.file("arcoon-version.properties")
val arcoonBuild: Int = Properties().apply {
    if (arcoonVersionFile.exists()) arcoonVersionFile.inputStream().use { load(it) }
}.getProperty("build", "1").trim().toInt()
val arcoonVersionName = "3.0.$arcoonBuild"
// Release channel: "test" (default) publishes GitHub pre-releases and skips the slow code
// shrinking step; "stable" (arcoon.channel=stable in local.properties) makes a fully optimized
// normal release.
val arcoonLocalProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val arcoonStable = arcoonLocalProperties.getProperty("arcoon.channel", "test").trim().equals("stable", ignoreCase = true)

val envPropertiesFile = rootProject.file(".env")
val envProperties = Properties().apply {
    if (envPropertiesFile.exists()) {
        load(envPropertiesFile.inputStream())
    }
}

fun envString(key: String, default: String = ""): String =
    envProperties.getProperty(key, System.getenv(key) ?: default)

android {
    namespace = "com.nendo.argosy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nendo.argosy"
        minSdk = 26
        targetSdk = 35
        versionCode = 400 + arcoonBuild
        versionName = arcoonVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        buildConfigField("String", "TITLEDB_API_SECRET", "\"${envString("TITLEDB_API_SECRET")}\"")
        buildConfigField("String", "TITLEDB_API_URL", "\"${envString("TITLEDB_API_URL", "https://api.argosy.dev")}\"")
        buildConfigField("String", "CHEATSDB_API_SECRET", "\"${envString("CHEATSDB_API_SECRET")}\"")
        val ucdataPath = envString("UCDATA_PATH").ifEmpty { null }?.let {
            it.replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { match ->
                match.groupValues[1].toInt(16).toChar().toString()
            }
        } ?: ""
        buildConfigField("String", "UCDATA_PATH", "\"$ucdataPath\"")
        buildConfigField("String", "DISCORD_APP_ID", "\"${envString("DISCORD_APP_ID")}\"")
        buildConfigField("Boolean", "DISCORD_SDK_ENABLED", envString("DISCORD_SDK_ENABLED", "false"))
        buildConfigField("String", "SOCIAL_API_URL", "\"https://api.argosy.dev/\"")
        // QuayPass server signing pubkey(s): comma-separated base64 Ed25519
        // public keys. Multiple values supported for zero-downtime rotation.
        // Empty in dev/CI when no server keypair has been provisioned;
        // QuayPassCredentialManager fails closed when empty.
        buildConfigField(
            "String",
            "QUAYPASS_SERVER_PUBKEYS",
            "\"${envString("QUAYPASS_SERVER_PUBKEYS")}\""
        )
        buildConfigField("int", "DOLPHIN_SYS_VERSION", "2")
        buildConfigField("int", "PPSSPP_SYS_VERSION", "1")
        buildConfigField("int", "ARMSX2_SYS_VERSION", "2")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("customRelease") {
            // test builds skip shrinking (minutes instead of ~13); stable builds are fully optimized
            isMinifyEnabled = arcoonStable
            isShrinkResources = arcoonStable
            isDebuggable = false
            applicationIdSuffix = ".release"
            matchingFallbacks += listOf("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            buildConfigField("String", "SOCIAL_API_URL", "\"https://api.argosy.dev/\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isPseudoLocalesEnabled = true
            buildConfigField("String", "SOCIAL_API_URL", "\"https://api.argosy.dev/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        baseline = file("lint-baseline.xml")
        error += listOf(
            "MissingTranslation",
            "ExtraTranslation",
            "StringFormatMatches",
            "StringFormatInvalid",
            "ImpliedQuantity",
            "Untranslatable"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = project.hasProperty("allAbis")
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Feed the Compose compiler a stability config for pervasive external types (flows,
// java.time, ...) and the app's immutable state packages. This short-circuits
// StabilityInferencer's deep recursion over the UI state graph, which is otherwise the
// dominant compile hotspot: a cold :app:compileDebugKotlin drops from >42 min to ~12 min.
// See compose_stability_config.conf for the rationale and safety notes.
//   -PnoStability     disable the config (to benchmark the compile cost)
//   -PcomposeMetrics  emit stability metrics/reports under build/compose_metrics
composeCompiler {
    if (!project.hasProperty("noStability")) {
        stabilityConfigurationFiles.add(
            layout.projectDirectory.file("compose_stability_config.conf")
        )
    }
    featureFlags.add(org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.IntrinsicRemember.disabled())
    featureFlags.add(org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.OptimizeNonSkippingGroups.disabled())
    featureFlags.add(org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.PausableComposition.disabled())
    if (project.hasProperty("composeMetrics")) {
        val dir = layout.buildDirectory.dir("compose_metrics")
        metricsDestination.set(dir)
        reportsDestination.set(dir)
    }
}

/**
 * Version code prefix per ABI. An x86 device cannot run the arm builds, so those sit above the
 * universal apk rather than below it, and a store offering several picks the native one.
 */
val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 4,
    "x86_64" to 5
)

android.applicationVariants.all {
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
        val abi = output.getFilter("ABI")
        val baseVersionCode = android.defaultConfig.versionCode ?: 0
        output.versionCodeOverride = if (abi != null) {
            (abiCodes[abi] ?: 0) * 1_000_000 + baseVersionCode
        } else {
            3 * 1_000_000 + baseVersionCode
        }
    }
}

val runIntegrationTests = project.hasProperty("runIntegrationTests")

tasks.withType<Test> {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    maxHeapSize = "2g"
    if (name == "testDebugUnitTest" || name == "testReleaseUnitTest") {
        if (!runIntegrationTests) {
            exclude("**/integration/**")
        }
    }
    listOf(
        "/opt/homebrew/opt/libsodium/lib",
        "/opt/homebrew/lib",
        "/usr/local/opt/libsodium/lib",
        "/usr/local/lib",
        "/usr/lib/x86_64-linux-gnu"
    ).firstOrNull { File(it).isDirectory }?.let { libDir ->
        systemProperty("jna.library.path", libDir)
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // TV Compose
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)
    annotationProcessor(libs.kotlin.metadata.jvm)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.bundles.network)
    ksp(libs.moshi.kotlin)
    implementation(libs.socketio.client) {
        exclude(group = "org.json", module = "json")
    }

    // CBOR (QuayPass BLE wire format)
    implementation(libs.upokecenter.cbor)

    // Media playback (Jellyfin)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    // animated GIF / WebP wallpapers in Cocoon themes
    implementation("io.coil-kt:coil-gif:2.7.0")

    // QR code generation + scanning
    implementation("com.google.zxing:core:3.5.4")
    implementation(libs.bundles.camerax)

    // Discord Social SDK (optional AAR -- place in app/libs/)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Color extraction
    implementation(libs.androidx.palette)

    // Archive extraction (7z, tar, zstd, etc.)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.zstd.jni) { artifact { type = "aar" } }

    // Steam (JavaSteam)
    implementation(libs.bundles.steam)

    // QuayPass credential Ed25519 verification (Conscrypt lacks an Ed25519 KeyFactory)
    implementation(libs.bouncycastle)

    // argosy-sigil — title id / serial extraction (replaces in-tree :libchdr +
    // Iso9660Utils + AesXts + ZArchiveReader + GameCubeHeaderParser.parseRomHeader)
    implementation(project(":sigil"))
    // Libretro (built-in emulation) - local module for customization
    implementation(project(":libretrodroid"))

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Netplay (Phase 2 wiring) -- XChaCha20-Poly1305 AEAD + UPnP port mapping
    implementation("com.goterl:lazysodium-android:5.2.0@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("org.bitlet:weupnp:0.1.4")

    // Testing
    testImplementation(libs.junit)
    // The zstd aar ships Android natives only; unit tests need the JVM jar.
    testImplementation(libs.zstd.jni)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("net.java.dev.jna:jna:5.14.0")
    testImplementation("org.json:json:20260814")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}

val verifyQuayPassReleaseConfig = tasks.register("verifyQuayPassReleaseConfig") {
    doLast {
        // Arcoon (personal build): QuayPass check-in is simply off without the server keys,
        // so this is a warning instead of stopping the build.
        if (envString("QUAYPASS_SERVER_PUBKEYS").isBlank()) {
            logger.warn("QUAYPASS_SERVER_PUBKEYS is empty: QuayPass check-in is disabled in this build.")
        }
    }
}

val verifyReleaseSigningConfig = tasks.register("verifyReleaseSigningConfig") {
    doLast {
        if (!keystorePropertiesFile.exists()) {
            throw GradleException(
                "keystore.properties is missing. A release build would fall back to the debug " +
                    "signing key, and an APK signed with that key cannot install over an " +
                    "existing release. Restore the file before building a release."
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyQuayPassReleaseConfig)
    dependsOn(verifyReleaseSigningConfig)
}

// ---- Arcoon: after each customRelease build ----
// 1. a copy of the APK goes to the Desktop folder, 2. it is published as a GitHub release (when a
// token is set in local.properties) so the app on the Thor can update itself, 3. the build number
// goes up by one for next time.
val arcoonApkDir = file(arcoonLocalProperties.getProperty("arcoon.apkFolder", "C:/Users/manu0/Desktop/arcoon launcher apks"))
val arcoonApkName = "Arcoon-$arcoonVersionName.apk"

val arcoonCopyApk = tasks.register("arcoonCopyApk") {
    doLast {
        val outDir = layout.buildDirectory.dir("outputs/apk/customRelease").get().asFile
        val apk = outDir.listFiles()?.filter { it.name.endsWith(".apk") }?.maxByOrNull { it.lastModified() }
        if (apk == null) {
            logger.warn("Arcoon: no customRelease APK found to copy")
            return@doLast
        }
        arcoonApkDir.mkdirs()
        apk.copyTo(File(arcoonApkDir, arcoonApkName), overwrite = true)
        logger.lifecycle("Arcoon: copied $arcoonApkName to $arcoonApkDir")
    }
}

val arcoonPublishRelease = tasks.register("arcoonPublishRelease") {
    mustRunAfter(arcoonCopyApk)
    doLast {
        val token = arcoonLocalProperties.getProperty("arcoon.githubToken")?.trim().orEmpty()
        val repo = arcoonLocalProperties.getProperty("arcoon.releasesRepo", "manu0623/Arcoon-releases").trim()
        val apk = File(arcoonApkDir, arcoonApkName)
        if (token.isEmpty()) {
            logger.lifecycle("Arcoon: no arcoon.githubToken in local.properties, skipping the GitHub release")
            return@doLast
        }
        if (!apk.exists()) {
            logger.warn("Arcoon: $arcoonApkName not found, skipping the GitHub release")
            return@doLast
        }
        fun open(url: String, method: String, type: String): HttpURLConnection =
            (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = method
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", type)
                connectTimeout = 30_000
                readTimeout = 300_000
            }
        val tag = "v$arcoonVersionName"
        val body = "{\"tag_name\":\"$tag\",\"name\":\"Arcoon $arcoonVersionName\"," +
            "\"body\":\"Arcoon build $arcoonBuild (${if (arcoonStable) "stable" else "test"})\",\"draft\":false,\"prerelease\":${!arcoonStable}}"
        val create = open("https://api.github.com/repos/$repo/releases", "POST", "application/json")
        create.outputStream.use { it.write(body.toByteArray()) }
        val code = create.responseCode
        val reply = (if (code in 200..299) create.inputStream else create.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) {
            logger.warn("Arcoon: GitHub release failed ($code): ${reply.take(300)}")
            return@doLast
        }
        val id = Regex("\"id\"\\s*:\\s*(\\d+)").find(reply)?.groupValues?.get(1)
        if (id == null) {
            logger.warn("Arcoon: GitHub release created but its id was not found")
            return@doLast
        }
        val upload = open(
            "https://uploads.github.com/repos/$repo/releases/$id/assets?name=$arcoonApkName",
            "POST", "application/vnd.android.package-archive"
        )
        upload.setFixedLengthStreamingMode(apk.length())
        upload.outputStream.use { out -> apk.inputStream().use { it.copyTo(out) } }
        val uploaded = upload.responseCode
        if (uploaded in 200..299) {
            logger.lifecycle("Arcoon: published $tag (${if (arcoonStable) "release" else "pre-release"}) to github.com/$repo")
        } else {
            logger.warn("Arcoon: APK upload failed ($uploaded): ${upload.errorStream?.bufferedReader()?.readText()?.take(300)}")
        }
    }
}

val arcoonBumpBuild = tasks.register("arcoonBumpBuild") {
    mustRunAfter(arcoonCopyApk, arcoonPublishRelease)
    doLast {
        arcoonVersionFile.writeText(
            "# Arcoon build number: goes up by one after every customRelease build.\nbuild=${arcoonBuild + 1}\n"
        )
        logger.lifecycle("Arcoon: next build will be 3.0.${arcoonBuild + 1}")
    }
}

tasks.matching { it.name == "assembleCustomRelease" }.configureEach {
    finalizedBy(arcoonCopyApk, arcoonPublishRelease, arcoonBumpBuild)
}
