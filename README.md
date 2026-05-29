# CRSPartnerBLESDK Android

English | [中文](README.zh-CN.md)

CRSPartnerBLESDK is the COROS Partner BLE SDK for Android apps. It lets partner apps authorize COROS devices, list authorized devices, observe device connection state, and receive real-time heart rate broadcasts.

## Requirements

- Android API 23 or later. The SDK encrypts the local authorization cache with Android Keystore `KeyGenParameterSpec` + AES/GCM, which requires API 23 or later.
- Kotlin or Java Android project.
- COROS App installed, signed in, and connected to the target COROS device.
- COROS Open Platform `clientId`, Android package/signature registration, and callback URI registration.

## Gradle

Confirm Maven Central is enabled:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

Add the SDK dependency in your app module:

```gradle
dependencies {
    implementation "com.coros.partner:crs-partner-ble-sdk:0.1.2"
}
```

The SDK uses protobuf-generated classes for BLE protocol encoding and decoding. The Maven Central POM brings the `com.google.protobuf:protobuf-java` runtime dependency transitively.

## Basic Usage

```kotlin
CRSPartnerBLESDK.initialize(
    context = applicationContext,
    config = SdkInitConfig(
        clientId = "<clientId>",
        partnerUserId = "<partnerUserId>",
        environment = Environment.PRODUCTION
    )
)

val result = CRSPartnerBLESDK.authorize(
    AuthorizationRequest(scope = Scope.BLE_HEART_BROADCAST)
)
```

Forward the complete callback URL from your Activity intent:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val callbackUrl = intent.data?.toString() ?: return
    lifecycleScope.launch {
        CRSPartnerBLESDK.handleOpenURL(callbackUrl)
    }
}
```

Start heart rate broadcast:

```kotlin
val heartBroadcast = CRSPartnerBLESDK.startHeartBroadcast(
    sportStartTimeInSec = System.currentTimeMillis() / 1000,
    callback = object : OnHeartRateChangeCallback {
        override fun onChange(hrSample: HrSample) {
            println(hrSample.heartRate)
        }

        override fun onFailure(exception: PartnerBleSdkException) {
            println(exception)
        }
    }
)

heartBroadcast.stop()
```

## Documentation

See [English Integration Guide](Docs/IntegrationGuide.en.md) or [中文接入说明](Docs/IntegrationGuide.md).

## Demo

The Android demo app is in [Demo](Demo). It is a standalone Gradle project:

```sh
cd Demo
./gradlew :CRSPartnerDemoApp:assembleDebug
```

The demo uses the sample package `com.coros.partner.app` and callback URI `crspartnerbledemo://coros/ble/callback`. Register your own package, signing certificate, and callback URI before running a full production authorization flow.

## Version

Current version: `0.1.2`.
