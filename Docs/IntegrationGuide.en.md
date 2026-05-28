# CRSPartnerBLESDK Android Integration Guide

[中文](IntegrationGuide.md) | English

This guide is for Android partner app integration. It covers the public SDK APIs and required app configuration.

## 1. Before You Start

### 1.1 Requirements

- Android API 24 or later.
- The user must have COROS App installed and signed in.
- The target COROS device must be authorized and remain connectable by COROS App.
- The SDK currently supports the COROS App package `com.yf.smart.coros.dist`.
- The SDK validates the COROS App signature before binding to the BLE IPC service. Contact COROS before using a new COROS App channel package.

The SDK manifest merges `android.permission.INTERNET` automatically.

### 1.2 Open Platform Registration

Confirm the following values with COROS Open Platform before integration:

| Value | Description |
| --- | --- |
| `clientId` | Client identifier assigned to the partner app by COROS. |
| Android package name | Read by the SDK at runtime and must match server registration. |
| Signing certificate | The release/debug certificate used by the partner app must match server registration. |
| Callback URI | URI used to receive COROS App authorization callbacks, for example `yourapp://coros/ble/callback`. |
| Server environment | Production or test. |
| Partner user ID | Stable user identifier from the partner app. |

Changing `partnerUserId` means the partner app login user has changed. Reinitialize the SDK and run authorization again after account switching.

## 2. App Configuration

### 2.1 Gradle Repository

Add the COROS Maven repository in `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            allowInsecureProtocol = true
            url = uri("http://192.168.29.237/nexus/repository/coros/")
        }
        google()
        mavenCentral()
    }
}
```

If your project uses the legacy repository style, add the same Maven repository in the root `build.gradle` `allprojects.repositories` block.

### 2.2 SDK Dependency

Add the SDK AAR dependency in your app module:

```gradle
dependencies {
    implementation "com.coros.partner:crs-partner-ble-sdk:0.1.0"
}
```

### 2.3 Callback Deeplink

Register the callback URI assigned to your partner app:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="yourapp"
            android:host="coros"
            android:path="/ble/callback" />
    </intent-filter>
</activity>
```

The values above are examples. Use the exact scheme, host, and path registered with COROS.

## 3. Initialize

Initialize the SDK after app startup and before using any SDK feature:

```kotlin
CRSPartnerBLESDK.initialize(
    context = applicationContext,
    config = SdkInitConfig(
        clientId = "<clientId from COROS Open Platform>",
        partnerUserId = "<stable partner user id>",
        environment = Environment.PRODUCTION,
        logConfig = LogConfig(
            enableConsoleLog = false,
            enableFileLog = true
        )
    )
)
```

| Parameter | Description |
| --- | --- |
| `clientId` | Client identifier assigned by COROS. Must be non-empty. |
| `partnerUserId` | Partner app user identifier. Must be non-empty and stable for the same user. |
| `environment` | `Environment.PRODUCTION` or `Environment.TEST`. Defaults to production. |
| `logConfig.enableConsoleLog` | Prints SDK logs to Logcat. Disable in production unless needed. |
| `logConfig.enableFileLog` | Writes SDK logs to file. Disabled by default. |
| `logConfig.logDirectory` | Optional custom log directory. Defaults to `cacheDir/crs_partner_ble_sdk/logs/`. |

## 4. Authorization

Call `authorize()` to start authorization. The SDK requests authorization information from the server and opens COROS App. After the user completes authorization, COROS App returns to the partner app through the callback URI.

```kotlin
val result = CRSPartnerBLESDK.authorize(
    AuthorizationRequest(scope = Scope.BLE_HEART_BROADCAST)
)
```

Forward the complete callback URL to the SDK:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val callbackUrl = intent.data?.toString() ?: return
    lifecycleScope.launch {
        val result = CRSPartnerBLESDK.handleOpenURL(callbackUrl)
        val devices = result.devices
    }
}
```

Do not parse, modify, or persist the callback URL before forwarding it. `authorize()` returns the devices available during the authorization flow; the final authorization result comes from `handleOpenURL()`.

## 5. List Authorized Devices

```kotlin
val devices = CRSPartnerBLESDK.listAuthorizedDevices()

for (device in devices) {
    println(device.deviceName)
    println(device.deviceModel)
    println(device.connectionState)
}
```

The current heart rate API works with the single currently authorized device. If there is no authorized device, more than one authorized device, or the device is not ready, the SDK throws `PartnerBleSdkException`.

## 6. Device Connection State

```kotlin
val callback = object : OnDeviceConnectionStateChangeCallback {
    override fun onChange(
        deviceName: String,
        deviceModel: String,
        connectionState: DeviceConnectionState
    ) {
        // Main-thread callback.
    }
}

CRSPartnerBLESDK.addDeviceConnectionStateCallback(callback)
CRSPartnerBLESDK.removeDeviceConnectionStateCallback(callback)
```

Registering the same callback again removes the old listener before adding the new one.

## 7. Heart Rate Broadcast

```kotlin
val sportStartTimeInSec = System.currentTimeMillis() / 1000

val heartBroadcast = CRSPartnerBLESDK.startHeartBroadcast(
    sportStartTimeInSec = sportStartTimeInSec,
    callback = object : OnHeartRateChangeCallback {
        override fun onChange(hrSample: HrSample) {
            println("heart rate: ${hrSample.heartRate}")
            println("timestamp: ${hrSample.timestampInSec}")
        }

        override fun onFailure(exception: PartnerBleSdkException) {
            // Triggered after start succeeds if the sample stream or keep-alive fails.
        }
    }
)

heartBroadcast.stop()
```

`sportStartTimeInSec` identifies one sport or broadcast session. Keep the same value for the same start, keep-alive, and stop sequence. `HeartBroadcastResult.stop()` is idempotent.

## 8. Revoke Authorization

```kotlin
lifecycleScope.launch {
    CRSPartnerBLESDK.revoke()
}
```

After revocation, authorization must be performed again before accessing authorized devices.

## 9. Release Resources

```kotlin
override fun onDestroy() {
    CRSPartnerBLESDK.release()
    super.onDestroy()
}
```

`release()` cancels SDK background tasks, removes connection callbacks, clears local heart rate broadcast state, and releases the BLE bridge connection. Call `initialize()` again before using SDK APIs after release.

## 10. Java Callback API

Kotlin suspend APIs are the primary APIs. Java integrations can use callback overloads:

```java
CRSPartnerBLESDK.INSTANCE.authorize(
        new AuthorizationRequest(Scope.BLE_HEART_BROADCAST),
        new SdkResultCallback<AuthorizationResult>() {
            @Override
            public void onSuccess(AuthorizationResult result) {
            }

            @Override
            public void onFailure(PartnerBleSdkException exception) {
            }
        }
);
```

The SDK also provides callback overloads for `handleOpenURL`, `startHeartBroadcast`, `listAuthorizedDevices`, and `revoke`. `initialize`, `addDeviceConnectionStateCallback`, `removeDeviceConnectionStateCallback`, and `release` are synchronous APIs.

## 11. Error Handling

SDK business errors are thrown as `PartnerBleSdkException`.

```kotlin
try {
    CRSPartnerBLESDK.listAuthorizedDevices()
} catch (exception: PartnerBleSdkException) {
    val code = exception.code
    val serverCode = exception.serverCode
    val traceId = exception.traceId
}
```

Common handling:

| Code | Meaning | Suggested handling |
| --- | --- | --- |
| `SDK_ERROR_CODE_INVALID_ARGUMENT` | Invalid SDK input. | Check `clientId`, `partnerUserId`, callback URL, or `sportStartTimeInSec`. |
| `SDK_ERROR_CODE_NOT_INITIALIZED` | SDK has not been initialized. | Call `initialize()` first. |
| `SDK_ERROR_CODE_NOT_AUTHORIZED` | Authorization is missing or unavailable. | Run authorization again. |
| `SDK_ERROR_CODE_DEVICE_NOT_READY` | Authorized device is not ready. | Call `listAuthorizedDevices()` and ask the user to check COROS App/device connection. |
| `SDK_ERROR_CODE_BLUETOOTH_POWERED_OFF` | System Bluetooth is off. | Ask the user to enable Bluetooth. |
| `SDK_ERROR_CODE_BLUETOOTH_UNAUTHORIZED` | Bluetooth permission is unavailable. | Ask the user to grant Bluetooth permission. |
| `SDK_ERROR_CODE_REBIND_REQUIRED` | COROS App BLE bridge or device binding needs recovery. | Ask the user to open COROS App and check device status. |
| `SDK_ERROR_CODE_COMMAND_CHANNEL_AUTH_FAILED` | Command channel authentication failed. | Re-authorize and retry. |
| `SDK_ERROR_CODE_WATCH_USER_EXIT_LOCKED` | The watch exited the current session. | Use a new `sportStartTimeInSec`. |
| `SDK_ERROR_CODE_SERVER_*` | Server-side error or invalid partner configuration. | Check partner configuration, retry if appropriate, and provide `traceId` to COROS support. |

## 12. Logs

File logs are disabled by default. Enable them during integration and troubleshooting:

```kotlin
LogConfig(
    enableConsoleLog = true,
    enableFileLog = true
)
```

Default log file:

```text
cacheDir/crs_partner_ble_sdk/logs/crs_partner_ble_sdk.log
```

Before uploading or forwarding logs, follow your app privacy policy and obtain any required authorization.

## 13. Integration Checklist

- `clientId` is registered on COROS Open Platform.
- Android package name and signing certificate are registered.
- Callback URI is registered and matches the Android manifest.
- `partnerUserId` is non-empty and stable for the current login user.
- `Environment.TEST` or `Environment.PRODUCTION` matches partner configuration.
- The callback URL is forwarded to `handleOpenURL()` as-is.
- Heart rate features are called only after authorization succeeds.
- Heart rate broadcast is stopped when the page exits, the activity ends, or the account switches.
- `release()` is called when the SDK is no longer needed.
