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
    implementation "com.coros.partner:crs-partner-ble-sdk:0.1.1"
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

Complete error codes:

| Value | Code | Meaning | Suggested handling |
| --- | --- | --- | --- |
| 0 | `SDK_ERROR_CODE_OK` | Success. | No action required. |
| 1 | `SDK_ERROR_CODE_INVALID_ARGUMENT` | Invalid SDK input, such as empty `clientId`, invalid callback URL, or invalid `sportStartTimeInSec`. | Check the call parameters. |
| 2 | `SDK_ERROR_CODE_NOT_AUTHORIZED` | Authorization is missing, locally unavailable, or expired. | Call `authorize()` again and complete authorization. |
| 3 | `SDK_ERROR_CODE_DEVICE_NOT_READY` | Authorized device is not ready, such as an unsupported authorized-device count for heart rate broadcast. | Call `listAuthorizedDevices()` and ask the user to check COROS App/device connection. |
| 4 | `SDK_ERROR_CODE_COMMAND_TIMEOUT` | Command execution timed out. | Retry later and check COROS App/device connection. |
| 5 | `SDK_ERROR_CODE_ACK_TIMEOUT` | Timed out while waiting for watch ACK. | Retry later and check whether the device remains connected. |
| 6 | `SDK_ERROR_CODE_STREAM_INTERRUPTED` | BLE bridge, Binder, or heart rate stream was interrupted. | Check COROS App/device connection and restart heart rate broadcast if needed. |
| 7 | `SDK_ERROR_CODE_REBIND_REQUIRED` | COROS App BLE bridge is unavailable, or the device needs COROS App to recover connection/rebinding. | Ask the user to open COROS App and check login, binding, and device connection state. |
| 8 | `SDK_ERROR_CODE_PERMISSION_REVOKED` | Local authorization cache is unavailable, possibly because system keys or permission state changed. | Clear authorization state and authorize again. |
| 9 | `SDK_ERROR_CODE_INVALID_PAYLOAD` | Local payload encode/decode failed, or COROS App returned an unexpected payload format. | Record logs and `traceId`, then contact COROS support. |
| 10 | `SDK_ERROR_CODE_ENVIRONMENT_MISMATCH` | Current SDK environment does not match the deeplink or authorization payload environment. | Confirm that `Environment` matches the partner server configuration. |
| 11 | `SDK_ERROR_CODE_BLUETOOTH_POWERED_OFF` | System Bluetooth is off. | Ask the user to enable Bluetooth. |
| 12 | `SDK_ERROR_CODE_BLUETOOTH_UNAUTHORIZED` | System Bluetooth permission is not granted. | Ask the user to grant Bluetooth permission. |
| 53 | `SDK_ERROR_CODE_WATCH_USER_EXIT_LOCKED` | The watch exited the current session, and the same `sportStartTimeInSec` cannot be started again. | Generate a new sport start time and start heart rate broadcast again. |
| 54 | `SDK_ERROR_CODE_COMMAND_CHANNEL_AUTH_FAILED` | Command channel authentication failed. | Re-authorize and retry. |
| 999 | `SDK_ERROR_CODE_INTERNAL_ERROR` | Unknown local SDK error. | Collect logs, `traceId`, and reproduction steps, then contact COROS support. |
| 1000 | `SDK_ERROR_CODE_NOT_INITIALIZED` | `initialize()` has not been called. | Initialize the SDK first. |
| 1001 | `SDK_ERROR_CODE_SERVER_ERROR` | Server internal error. | Retry later. Contact COROS if the issue persists. |
| 1019 | `SDK_ERROR_CODE_SERVER_LOGIN_INVALID` | COROS App login state is invalid or missing. | Ask the user to sign in to COROS App and retry. |
| 1031 | `SDK_ERROR_CODE_SERVER_INVALID_PARAMETER` | Server rejected the request parameters or enum values. | Check partner configuration and request parameters. |
| 1037 | `SDK_ERROR_CODE_SERVER_NO_DATA` | Server has no available data. | Check authorization, device state, and partner configuration. |
| 1042 | `SDK_ERROR_CODE_SERVER_RATE_LIMITED` | Server rate limit was reached. | Reduce request frequency and retry. |
| 1047 | `SDK_ERROR_CODE_SERVER_INVALID_CONTENT` | Server returned invalid or incomplete content. | Re-authorize or contact COROS support. |
| 5002 | `SDK_ERROR_CODE_SERVER_INVALID_CLIENT` | `clientId` is invalid or not registered. | Confirm the COROS-assigned `clientId` and server registration. |
| 5004 | `SDK_ERROR_CODE_SERVER_INVALID_SCOPE` | Requested scope is invalid or not enabled for this partner. | Confirm partner scope configuration. |
| 5026 | `SDK_ERROR_CODE_SERVER_EXPIRED` | Request, signature, or authorization data has expired. | Start authorization or the request again. |
| 5030 | `SDK_ERROR_CODE_SERVER_INVALID_SIGNATURE` | Signature verification failed, including partner signature or COROS App signature mismatch. | Check package name, signing certificate, and server registration. |
| 5031 | `SDK_ERROR_CODE_SERVER_KEY_UNAVAILABLE` | Server key is unavailable. | Retry later or contact COROS support. |
| 5032 | `SDK_ERROR_CODE_SERVER_EXPIRED_KEY` | Server key has expired. | Re-authorize. Contact COROS if the issue persists. |
| 5033 | `SDK_ERROR_CODE_SERVER_UNAUTHORIZED_DEVICE` | Device is not authorized for the current partner/user. | Re-authorize and confirm the selected device. |
| 5034 | `SDK_ERROR_CODE_SERVER_DEVICE_DISCONNECTED` | Server considers the device disconnected. | Ask the user to check device connection in COROS App. |
| 5035 | `SDK_ERROR_CODE_SERVER_INVALID_AUTHORIZED_DEVICES` | Server returned an invalid authorized-device list. | Re-authorize or contact COROS support. |
| 5036 | `SDK_ERROR_CODE_SERVER_USER_DENIED` | User denied authorization in COROS App. | Respect the user choice and retry authorization when appropriate. |
| 5037 | `SDK_ERROR_CODE_SERVER_INVALID_GRANT` | Grant is invalid. | Re-authorize. |
| 5038 | `SDK_ERROR_CODE_SERVER_GRANT_REVOKED` | Grant was revoked. | Re-authorize. |
| 9015 | `SDK_ERROR_CODE_SERVER_DEVICE_NOT_EXIST` | Server considers the device nonexistent. | Check user device binding state and re-authorize if needed. |

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
