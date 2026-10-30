# 2026-10-29 阶段 8 / P0-3 设备验收证据

设备：OPD2515，Android 17 / API 37，adb 序列号 <device-serial>。

## 结果

| 项目 | 结果 |
|---|---|
| 干净构建 | BUILD SUCCESSFUL，54s |
| 单元测试 | 88 / 88，0 failed / 0 errors / 0 skipped |
| 仪器测试 | 50 / 50，0 failed / 0 errors / 0 skipped，66.5s |
| Lint | 0 errors，2 warnings |

## 文件

- `connected-test-results.xml`：50 项仪器测试原始 JUnit XML。
- `connected-tests.log`：`connectedDebugAndroidTest` 的 Gradle 输出。
- `unit-test-results/`：14 个单元测试类的 JUnit XML。
- `lint-results-debug.txt`：lint 文本报告。
- `build.log`：干净构建输出。
- `logcat.txt`：测试期间过滤后的设备 logcat。
- `device.txt`：设备型号与系统版本。

## 复现命令

```bash
export GRADLE_USER_HOME="$PWD/.gradle-home"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
export ANDROID_SERIAL=<device-serial>

./gradlew --offline --no-daemon \
  clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest

./gradlew --offline --no-daemon connectedDebugAndroidTest
```

本机 ColorOS 在安装 debug APK 时会弹出“继续安装”确认，`connectedDebugAndroidTest` 会在此处等待；需在设备上确认后才能进入测试。

## 本轮新增/恢复的 P0-3 用例

- `NotificationDeepLinkTest.existingActivity_sameProduct_reopensDetailAndBackReturnsHome`
- `NotificationDeepLinkTest.existingActivity_differentProduct_showsNewProductAndBackReturnsHome`
- `NotificationDispatcherTest.permissionDenied_keepsEventPendingUntilGranted`
- `AndroidNotificationSenderTest.channelWithImportanceNone_isRecognizedAsBlocked`

## 说明

- `AndroidNotificationSenderTest` 验证渠道 `IMPORTANCE_NONE` 被识别为不可投递；它不依赖测试进程的 `POST_NOTIFICATIONS` 授权状态。
- 权限拒绝用例通过 `FakeNotificationSender` 控制 `canNotify()`，验证事件不会被误标为 `SENT`。
- `logcat.txt` 是过滤后的系统/应用日志，不是完整设备日志。
