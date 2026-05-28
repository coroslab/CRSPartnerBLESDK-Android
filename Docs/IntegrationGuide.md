# CRSPartnerBLESDK Android 接入说明

中文 | [English](IntegrationGuide.en.md)

本文面向 Android 第三方 App 接入，仅覆盖 SDK public API 和必要 App 配置。

## 1. 接入前准备

### 1.1 要求

- Android API 24 或更高版本。
- 用户已安装并登录 COROS App。
- 目标 COROS 设备已授权，并能被 COROS App 保持连接。
- SDK 当前支持的 COROS App 包名：`com.yf.smart.coros.dist`。
- SDK 在绑定 COROS App BLE IPC 服务前会校验 COROS App 签名。新增 COROS App 渠道包前，请先与 COROS 同步。

SDK manifest 会自动合并 `android.permission.INTERNET`。

### 1.2 Open Platform Registration

接入前请向 COROS Open Platform 确认以下注册项：

| Field | 说明 |
| --- | --- |
| `clientId` | COROS 为第三方 App 分配的客户端 ID。 |
| Android Package Name | SDK 运行时读取，必须与服务端登记一致。 |
| Signing Certificate | 第三方 App 使用的 debug/release 签名需与服务端登记一致。 |
| Callback URI | 第三方 App 接收 COROS App 授权回跳的 URI，例如 `yourapp://coros/ble/callback`。 |
| Server Environment | 生产环境或测试环境。 |
| Partner User ID | 第三方 App 自己的稳定用户标识。 |

`partnerUserId` 变化代表第三方 App 登录用户变化。账号切换后应重新初始化 SDK，并重新完成授权。

## 2. App 配置

### 2.1 Gradle 仓库

在 `settings.gradle` 中添加 COROS Maven 仓库：

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

如果项目仍使用旧版仓库配置，也可以把相同 Maven 仓库添加到根目录 `build.gradle` 的 `allprojects.repositories`。

### 2.2 SDK 依赖

在 App 模块中添加 SDK AAR 依赖：

```gradle
dependencies {
    implementation "com.coros.partner:crs-partner-ble-sdk:0.1.0"
}
```

### 2.3 回调 Deeplink

注册第三方 App 已登记的回调 URI：

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

上面只是示例。正式接入时请使用 COROS 为 partner 分配并登记的 scheme、host 和 path。

## 3. 初始化

在 App 启动后、使用任何 SDK 能力前初始化：

```kotlin
CRSPartnerBLESDK.initialize(
    context = applicationContext,
    config = SdkInitConfig(
        clientId = "<COROS Open Platform clientId>",
        partnerUserId = "<stable partner user id>",
        environment = Environment.PRODUCTION,
        logConfig = LogConfig(
            enableConsoleLog = false,
            enableFileLog = true
        )
    )
)
```

| 参数 | 说明 |
| --- | --- |
| `clientId` | COROS 分配的客户端 ID，不能为空。 |
| `partnerUserId` | 第三方 App 用户标识，不能为空，同一用户生命周期内应保持稳定。 |
| `environment` | `Environment.PRODUCTION` 或 `Environment.TEST`，默认生产环境。 |
| `logConfig.enableConsoleLog` | 是否输出 Logcat 日志，生产环境建议关闭。 |
| `logConfig.enableFileLog` | 是否写入 SDK 文件日志，默认关闭。 |
| `logConfig.logDirectory` | 自定义日志目录，不传时写入 `cacheDir/crs_partner_ble_sdk/logs/`。 |

## 4. 授权

调用 `authorize()` 发起授权。SDK 会向服务端获取授权信息并拉起 COROS App。用户在 COROS App 完成授权后，COROS App 会通过回调 URI 回跳第三方 App。

```kotlin
val result = CRSPartnerBLESDK.authorize(
    AuthorizationRequest(scope = Scope.BLE_HEART_BROADCAST)
)
```

第三方 App 收到回跳后，把完整回调 URL 转交给 SDK：

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

请将回调 URL 原样传入 `handleOpenURL()`，不要提前解析、改写或持久化。`authorize()` 返回的是授权流程中服务端返回的可授权设备信息；最终授权结果以 `handleOpenURL()` 返回值为准。

## 5. 查询已授权设备

```kotlin
val devices = CRSPartnerBLESDK.listAuthorizedDevices()

for (device in devices) {
    println(device.deviceName)
    println(device.deviceModel)
    println(device.connectionState)
}
```

当前心率 API 面向当前唯一已授权设备。如果没有授权设备、存在多台授权设备，或设备未就绪，SDK 会抛出 `PartnerBleSdkException`。

## 6. 设备连接状态监听

```kotlin
val callback = object : OnDeviceConnectionStateChangeCallback {
    override fun onChange(
        deviceName: String,
        deviceModel: String,
        connectionState: DeviceConnectionState
    ) {
        // 主线程回调。
    }
}

CRSPartnerBLESDK.addDeviceConnectionStateCallback(callback)
CRSPartnerBLESDK.removeDeviceConnectionStateCallback(callback)
```

同一个 callback 重复注册时，SDK 会先移除旧监听，再注册新监听。

## 7. 心率广播

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
            // 心率广播启动成功后，采样流或保活失败会回调到这里。
        }
    }
)

heartBroadcast.stop()
```

`sportStartTimeInSec` 用于标识一次运动/广播会话。同一次开始、保活、结束需要保持一致。`HeartBroadcastResult.stop()` 可重复调用，SDK 只会执行一次停止逻辑。

## 8. 撤销授权

```kotlin
lifecycleScope.launch {
    CRSPartnerBLESDK.revoke()
}
```

撤销后，如需继续访问授权设备，需要重新发起授权流程。

## 9. 释放资源

```kotlin
override fun onDestroy() {
    CRSPartnerBLESDK.release()
    super.onDestroy()
}
```

`release()` 会取消 SDK 后台任务、移除设备连接状态监听、清理本地心率广播状态并释放 BLE bridge 连接。释放后再次使用 SDK API 前，需要重新调用 `initialize()`。

## 10. Java Callback API

Kotlin suspend API 是主 API。Java 接入方可以使用 callback overload：

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

SDK 同时提供 `handleOpenURL`、`startHeartBroadcast`、`listAuthorizedDevices` 和 `revoke` 的 callback overload。`initialize`、`addDeviceConnectionStateCallback`、`removeDeviceConnectionStateCallback` 和 `release` 是同步 API。

## 11. 错误处理

SDK 业务异常统一通过 `PartnerBleSdkException` 抛出。

```kotlin
try {
    CRSPartnerBLESDK.listAuthorizedDevices()
} catch (exception: PartnerBleSdkException) {
    val code = exception.code
    val serverCode = exception.serverCode
    val traceId = exception.traceId
}
```

完整错误码如下：

| Value | Code | 含义 | 建议处理 |
| --- | --- | --- | --- |
| 0 | `SDK_ERROR_CODE_OK` | 成功。 | 无需处理。 |
| 1 | `SDK_ERROR_CODE_INVALID_ARGUMENT` | SDK 入参非法，例如 `clientId` 为空、回调 URL 无效或 `sportStartTimeInSec` 非法。 | 检查调用参数。 |
| 2 | `SDK_ERROR_CODE_NOT_AUTHORIZED` | 授权缺失、本地授权不可用或授权已失效。 | 重新调用 `authorize()` 并完成授权。 |
| 3 | `SDK_ERROR_CODE_DEVICE_NOT_READY` | 授权设备未就绪，例如设备数量不满足心率广播要求。 | 调用 `listAuthorizedDevices()` 检查设备状态，并引导用户检查 COROS App 和设备连接。 |
| 4 | `SDK_ERROR_CODE_COMMAND_TIMEOUT` | 命令执行超时。 | 稍后重试，并检查 COROS App 和设备连接。 |
| 5 | `SDK_ERROR_CODE_ACK_TIMEOUT` | 等待手表 ACK 超时。 | 稍后重试，并检查设备是否保持连接。 |
| 6 | `SDK_ERROR_CODE_STREAM_INTERRUPTED` | BLE bridge、Binder 或心率数据流中断。 | 检查 COROS App 和设备连接，必要时重新开始心率广播。 |
| 7 | `SDK_ERROR_CODE_REBIND_REQUIRED` | COROS App BLE bridge 不可用，或设备需要回到 COROS App 恢复连接/重新绑定。 | 引导用户打开 COROS App 检查登录、绑定和设备连接状态。 |
| 8 | `SDK_ERROR_CODE_PERMISSION_REVOKED` | 本地授权缓存不可用，可能是系统密钥变化或权限状态变化。 | 清理授权态后重新授权。 |
| 9 | `SDK_ERROR_CODE_INVALID_PAYLOAD` | 本地 payload 编解码失败，或 COROS App 返回数据格式不符合预期。 | 记录日志和 `traceId`，联系 COROS 排查。 |
| 10 | `SDK_ERROR_CODE_ENVIRONMENT_MISMATCH` | 当前 SDK 环境与 deeplink 或授权 payload 环境不一致。 | 确认 `Environment` 与 partner 服务端配置一致。 |
| 11 | `SDK_ERROR_CODE_BLUETOOTH_POWERED_OFF` | 系统蓝牙关闭。 | 引导用户开启蓝牙。 |
| 12 | `SDK_ERROR_CODE_BLUETOOTH_UNAUTHORIZED` | 系统蓝牙权限未授权。 | 引导用户授予蓝牙权限。 |
| 53 | `SDK_ERROR_CODE_WATCH_USER_EXIT_LOCKED` | 手表端已主动退出当前会话，同一 `sportStartTimeInSec` 不可再次拉起。 | 生成新的运动开始时间后再开始心率广播。 |
| 54 | `SDK_ERROR_CODE_COMMAND_CHANNEL_AUTH_FAILED` | 命令通道鉴权失败。 | 重新授权后再重试。 |
| 999 | `SDK_ERROR_CODE_INTERNAL_ERROR` | SDK 本地未知错误。 | 收集日志、`traceId` 和复现步骤，联系 COROS 排查。 |
| 1000 | `SDK_ERROR_CODE_NOT_INITIALIZED` | 尚未调用 `initialize()`。 | 先完成 SDK 初始化。 |
| 1001 | `SDK_ERROR_CODE_SERVER_ERROR` | 服务端内部错误。 | 稍后重试，持续失败时联系 COROS。 |
| 1019 | `SDK_ERROR_CODE_SERVER_LOGIN_INVALID` | COROS App 登录态无效或缺失。 | 引导用户登录 COROS App 后重试。 |
| 1031 | `SDK_ERROR_CODE_SERVER_INVALID_PARAMETER` | 服务端认为请求参数非法或枚举值不支持。 | 检查 partner 配置和请求参数。 |
| 1037 | `SDK_ERROR_CODE_SERVER_NO_DATA` | 服务端无可用数据。 | 检查授权、设备和 partner 配置。 |
| 1042 | `SDK_ERROR_CODE_SERVER_RATE_LIMITED` | 服务端限流。 | 降低请求频率后重试。 |
| 1047 | `SDK_ERROR_CODE_SERVER_INVALID_CONTENT` | 服务端返回内容无效或不完整。 | 重新授权或联系 COROS 排查。 |
| 5002 | `SDK_ERROR_CODE_SERVER_INVALID_CLIENT` | `clientId` 无效或未注册。 | 确认 COROS 分配的 `clientId` 和服务端登记状态。 |
| 5004 | `SDK_ERROR_CODE_SERVER_INVALID_SCOPE` | 请求的 scope 无效，或该 partner 未开通该 scope。 | 确认 partner scope 配置。 |
| 5026 | `SDK_ERROR_CODE_SERVER_EXPIRED` | 请求、签名或授权数据已过期。 | 重新发起授权或请求。 |
| 5030 | `SDK_ERROR_CODE_SERVER_INVALID_SIGNATURE` | 签名校验失败，包含 partner 签名或 COROS App 签名不匹配。 | 检查包名、签名证书和服务端登记信息。 |
| 5031 | `SDK_ERROR_CODE_SERVER_KEY_UNAVAILABLE` | 服务端密钥不可用。 | 稍后重试或联系 COROS。 |
| 5032 | `SDK_ERROR_CODE_SERVER_EXPIRED_KEY` | 服务端密钥已过期。 | 重新授权，仍失败时联系 COROS。 |
| 5033 | `SDK_ERROR_CODE_SERVER_UNAUTHORIZED_DEVICE` | 设备未被授权给当前 partner/user。 | 重新授权并确认用户选择的设备。 |
| 5034 | `SDK_ERROR_CODE_SERVER_DEVICE_DISCONNECTED` | 服务端判断设备未连接。 | 引导用户检查 COROS App 内设备连接。 |
| 5035 | `SDK_ERROR_CODE_SERVER_INVALID_AUTHORIZED_DEVICES` | 服务端返回的授权设备列表无效。 | 重新授权或联系 COROS 排查。 |
| 5036 | `SDK_ERROR_CODE_SERVER_USER_DENIED` | 用户在 COROS App 中拒绝授权。 | 尊重用户选择，可在合适时机重新发起授权。 |
| 5037 | `SDK_ERROR_CODE_SERVER_INVALID_GRANT` | grant 无效。 | 重新授权。 |
| 5038 | `SDK_ERROR_CODE_SERVER_GRANT_REVOKED` | grant 已被撤销。 | 重新授权。 |
| 9015 | `SDK_ERROR_CODE_SERVER_DEVICE_NOT_EXIST` | 服务端认为设备不存在。 | 检查用户设备绑定状态，必要时重新授权。 |

## 12. 日志

文件日志默认关闭。接入调试时可开启：

```kotlin
LogConfig(
    enableConsoleLog = true,
    enableFileLog = true
)
```

默认日志文件：

```text
cacheDir/crs_partner_ble_sdk/logs/crs_partner_ble_sdk.log
```

上传或转发日志前，请遵守第三方 App 自身隐私政策，并获取必要授权。

## 13. 接入检查清单

- `clientId` 已在 COROS Open Platform 登记。
- Android 包名和签名证书已登记。
- 回调 URI 已登记，并与 Android manifest 一致。
- `partnerUserId` 非空，并稳定绑定当前登录用户。
- `Environment.TEST` 或 `Environment.PRODUCTION` 与 partner 配置一致。
- 回调 URL 已原样转交给 `handleOpenURL()`。
- 心率能力只在授权成功后调用。
- 页面退出、运动结束或账号切换时停止心率广播。
- 不再需要 SDK 能力时调用 `release()`。
