# 阶段 11 进度：设备端到端验收与交付

更新时间：2026-10-30 22:36  
结论：**阶段 11 已完成**。步骤 1–8 在 OPD2515 / API 37 真机手工执行并归档证据；步骤 9 干净构建、JVM、Lint 与 84 项设备自动化全部通过；步骤 10 交付说明已更新。无法自然制造的异常项由自动化覆盖，未写成手工通过。

## 1. 状态总览

| 项目 | 状态 | 证据 |
|---|---|---|
| 干净构建 `clean assembleDebug testDebugUnitTest lintDebug` | 通过，50s / 55 tasks | `docs/evidence/2026-10-30/phase11/device-manual/09-build.log` |
| JVM 单元测试 | 127 / 127（21 suites，0 failed / 0 errors / 0 skipped） | `docs/evidence/2026-10-30/phase11/unit-test-results/` |
| Lint | 0 errors / 2 warnings（既有） | `docs/evidence/2026-10-30/phase11/lint-results-debug.txt` |
| 设备连接 | OPD2515 / Android 17 / API 37 / serial <device-serial> | `phase11/device-manual/` |
| 设备手工验收 1–8 | **已执行并归档** | `PHASE11_DEVICE_ACCEPTANCE.md` 第 11 节 |
| 设备自动化回归（阶段 11） | **84 / 84**，0 failed / 0 errors / 0 skipped，1m 6s | `phase11/phase11-connected-test-results.xml` |
| 交付说明 | 已完成 | `docs/DELIVERY.md` |

阶段 10 之后没有生产代码变更；阶段 11 新增的只有手工验收证据与文档。

## 2. 十条要求逐项

| # | 要求 | 状态 | 证据/说明 |
|---|---|---|---|
| 1 | 清除应用数据，验证首装首页和演示数据标识 | 通过（以全新安装代替 `pm clear`） | `01-first-launch.png/.xml`；ColorOS 禁止 adb `pm clear`，包此前未安装，故全新安装等价 |
| 2 | 单个订阅、取消、全部订阅、重启持久化 | 通过 | `02-*`；force-stop 重启后 KIMI 保持订阅，全部订阅生效 |
| 3 | 首次同步、新优惠、涨价、降价、重复同步、网络失败 | 通过 | `03-*`；累计场景下首页显示最低价 ¥70，详情 welcome 变 110/80 |
| 4 | 只有已订阅产品收到一次通知 | 通过 | `04-*`；仅 KIMI 订阅，一条“价格下降，点击查看详情”；重复同步无第二条 |
| 5 | 点击通知进入对应详情 | 通过 | `05-notification-deeplink.*`；进入 KIMI 详情，返回回首页 |
| 6 | 折线图、历史最低、币种、周期、优惠时间 | 通过（2 个细项由自动化覆盖） | `06-detail-chart.*`；6 个点、CNY、月付、22:19–22:28；¥70 过期优惠带历史最低 |
| 7 | 去订阅 URL 与统计增量 | 通过（清除记录由自动化覆盖） | `07-*`；Via Custom Tab 打开 `https://kimi.moonshot.cn/`，KIMI +1，重启保留 |
| 8 | Custom Tab、非法 URL、无网络、空数据、来源失败 | 通过（非法 URL/无浏览器/DB 错误由自动化覆盖） | `08-*`、`03-network-failure-*`；离线缓存重启不崩溃；官方空态可切回 |
| 9 | 干净环境重新执行构建与测试命令 | 通过 | 见第 3 节；127/127、Lint 0/2、84/84 |
| 10 | 交付说明 8 项内容 | 已完成 | `docs/DELIVERY.md` 第 1–11 节 |

## 3. 步骤 9 原始结果

```bash
export GRADLE_USER_HOME="$PWD/.gradle-home"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
./gradlew --offline --no-daemon clean assembleDebug testDebugUnitTest lintDebug
./gradlew --offline --no-daemon connectedDebugAndroidTest
```

```text
BUILD SUCCESSFUL in 50s
55 actionable tasks: 55 executed
JVM: 21 suites / 127 tests / 0 failures / 0 errors / 0 skipped
Lint: 0 errors, 2 warnings

Starting 84 tests on OPD2515 - 17
Finished 84 tests on OPD2515 - 17
BUILD SUCCESSFUL in 1m 6s
84 tests / 0 failures / 0 errors / 0 skipped
```

两条 Lint warning 为既有问题：

- `NotificationPermissionState.kt`：`POST_NOTIFICATIONS` 在 API 33 才存在，minSdk 26 下的 InlinedApi 提示；
- `colors.xml`：`radar_blue` 未使用。

证据：

- `device-manual/09-build.log`
- `phase11/unit-test-results/`（21 个 XML）
- `phase11/lint-results-debug.txt`
- `device-manual/09-connected.log`
- `phase11/phase11-connected-test-results.xml`

## 4. 设备连接与 ColorOS 限制

2026-10-30 19:09 首次检查无设备；22:00 左右重连后，默认 5037 adb server 状态异常，`adb devices` 为空但 `system_profiler` 可见设备。改用 5038 端口确认设备后，清理两个 adb server 并重启 5037，设备恢复。

ColorOS 实测限制：

```text
adb shell pm clear --user 0 com.aioffer.radar
SecurityException: ... does not have permission android.permission.CLEAR_APP_USER_DATA

adb shell pm revoke com.aioffer.radar android.permission.POST_NOTIFICATIONS
SecurityException: ... does not have permission android.permission.REVOKE_RUNTIME_PERMISSIONS
```

因此没有用 adb 清数据/撤销通知权限。阶段 11 首装通过“此前未安装 + 全新安装”完成；通知权限通过 App 内“设置 → 开启通知”授予。

安装/启动期间需要处理：

- `com.oplus.appdetail/...InstallGuideActivity`：点“继续安装/完成”；
- `com.oplus.securitypermission/...AppStartConfirmDialogActivity`：点“30天内允许”；
- 不要禁用 `com.oplus.appdetail`，否则系统安装器无法继续安装无 launcher icon 的 test APK。

## 5. 交付文档范围

`docs/DELIVERY.md` 已覆盖：

- 构建、安装、运行、测试命令（含沙箱变量、ColorOS 安装注意）；
- 清数据首装预期与 ColorOS `pm clear` 限制；
- 6 个演示场景的触发方式与实际累计行为（首页最低价 vs 详情 welcome）；
- 通知权限、渠道、去重、PENDING/SENT 语义；
- 详情、折线图、历史最低、币种、计费周期、时间；
- 官网 Custom Tab、URL 校验、点击统计；
- 无网络、空数据、来源失败、DB 错误、无浏览器/非法 URL；
- 真实数据源必需字段与接入步骤；
- FCM 未配置的生产能力缺口；
- WorkManager 非实时限制；
- 真实/演示数据隔离规则；
- 功能、测试、未验证项对照表。

## 6. 未验证项

- API 26–36、其他 ROM、通知渠道关闭、省电/长期 soak；
- 真实官方数据源（当前是 fail-closed 占位，未接入）；
- FCM/服务端推送（未配置）；
- release 签名、CI、Play 配置；
- 深链“已有 saved state + 全新 intent”的罕见路径；
- 手工未单独执行、仅自动化覆盖：切换其他套餐后的图表更新、少于两个历史点、清除点击记录、无浏览器、非法 URL、数据库读取失败。

这些项目在 `DELIVERY.md` 第 10 节中均为“未验证/未实现”，不得在验收结论中写成通过。

## 7. 相关文件

- [DELIVERY.md](DELIVERY.md)
- [PHASE11_DEVICE_ACCEPTANCE.md](PHASE11_DEVICE_ACCEPTANCE.md)
- [HANDOFF.md](HANDOFF.md)
- [DATA_SOURCE_INTEGRATION.md](DATA_SOURCE_INTEGRATION.md)
- `docs/evidence/2026-10-30/phase11/`
- `docs/evidence/2026-10-30/phase11/device-manual/`
