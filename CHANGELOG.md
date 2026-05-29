# Changelog

## 0.1.2

- Removed SDK module dependencies on Gson, OkHttp, Retrofit, kotlinx-coroutines, and JUnit. The SDK keeps protobuf-generated classes for BLE protocol encoding/decoding and uses lightweight internal networking, JSON, and scheduling implementations.
- Simplified and strengthened obfuscation rules. The SDK keeps public APIs and AIDL bridge generated classes, and removes old Gson/Retrofit rules.
- Added Android API 23+ compatibility notes. The local authorization cache depends on Android Keystore `KeyGenParameterSpec` + AES/GCM.

## 0.1.1

- Updated documentation and demo dependency examples to use SDK version `0.1.1`.

## 0.1.0

- Initial Android release package with Partner authorization, authorized device query, device connection state callbacks, and heart rate broadcast APIs.
- Added Kotlin suspend APIs and Java callback-style APIs.
- Added a standalone Android demo app under `Demo/`.
- Added English and Chinese integration documentation.
