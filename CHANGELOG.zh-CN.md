# 变更记录

## 0.1.2

- 移除 SDK 对 Gson、OkHttp、Retrofit、kotlinx-coroutines 和 JUnit 的模块依赖，保留 protobuf 生成类处理 BLE 协议编解码，改为 SDK 内部轻量实现现有网络、JSON 和调度能力。
- 精简并补强混淆规则，保留 SDK public API 和 AIDL bridge 生成类，移除旧 Gson/Retrofit 相关规则。
- 补充 Android API 23+ 兼容性说明；本地授权缓存依赖 Android Keystore `KeyGenParameterSpec` + AES/GCM。

## 0.1.1

- 更新文档和 Demo 依赖示例，使用 SDK 版本 `0.1.1`。

## 0.1.0

- 初始 Android 发布包，提供 Partner 授权、已授权设备查询、设备连接状态监听和心率广播能力。
- 增加 Kotlin suspend API 和 Java callback 风格 API。
- 在 `Demo/` 下增加可独立构建的 Android Demo。
- 增加中英文接入文档。
