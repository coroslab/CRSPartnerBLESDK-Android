# CRSPartnerBLESDK Android

[English](README.md) | 中文

CRSPartnerBLESDK 是 COROS Partner BLE Android SDK，用于第三方 App 接入 COROS 授权、已授权设备查询、设备连接状态监听和实时心率广播能力。

## 要求

- Android API 24 或更高版本。
- Kotlin 或 Java Android 项目。
- 用户已安装并登录 COROS App，目标 COROS 设备可由 COROS App 连接。
- 已在 COROS Open Platform 登记 `clientId`、Android 包名/签名和回调 URI。

## Gradle

添加 COROS Maven 仓库：

```gradle
dependencyResolutionManagement {
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

在 App 模块中添加 SDK 依赖：

```gradle
dependencies {
    implementation "com.coros.partner:crs-partner-ble-sdk:0.1.1"
}
```

## 基础用法

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

在 Activity 中把完整回调 URL 转交给 SDK：

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val callbackUrl = intent.data?.toString() ?: return
    lifecycleScope.launch {
        CRSPartnerBLESDK.handleOpenURL(callbackUrl)
    }
}
```

开始心率广播：

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

## 文档

参见 [中文接入说明](Docs/IntegrationGuide.md) 或 [English Integration Guide](Docs/IntegrationGuide.en.md)。

## Demo

Android Demo 位于 [Demo](Demo)，是一个可独立构建的 Gradle 工程：

```sh
cd Demo
./gradlew :CRSPartnerDemoApp:assembleDebug
```

Demo 使用示例包名 `com.coros.partner.app` 和回调 URI `crspartnerbledemo://coros/ble/callback`。正式跑通授权流程前，请在服务端登记你自己的包名、签名证书和回调 URI。

## 版本

当前版本：`0.1.1`。
