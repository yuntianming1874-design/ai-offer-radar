# 阶段 9 进度：设置、异常状态与数据源边界

更新时间：2026-10-30  
状态：**实现完成，JVM/Lint/构建与设备仪器测试均通过**。

## 目标与完成条件

目标：用 DataStore 保存数据源类型、同步偏好和通知提示状态；保证数据源切换不改变历史数据可信度；演示与官方数据隔离；首页展示数据来源、最后成功同步时间和失败原因；无网络、空数据、数据库异常都有可恢复入口。

完成条件：

- 无网络、空数据和来源失败时界面不崩溃；
- 旧数据不会被描述成实时数据；
- 演示快照永远不会被重新标记成官方历史。

## 9 条步骤的实现与证据

| # | 要求 | 实现 | 验证 |
|---|---|---|---|
| 1 | DataStore 保存设置 | `AppSettings` + `AppSettingsStore` + `DataStoreAppSettingsStore`；保存 `dataSourceMode`、`autoSyncEnabled`、`notificationPromptState` | `DataStoreAppSettingsStoreTest` 3 个测试；设置页可读写 |
| 2 | 切换不改变历史可信度 | 模式只过滤首页展示；`SourceInfo` 类型不可变；快照只追加 | `HomeViewModelTest.switchingDataSource_neverRelabelsStoredDemoHistory`、`LocalRadarRepositoryTest.applyingSync_cannotRelabelAnExistingSourceType` |
| 3 | 演示/官方隔离 | 双 sourceId（`demo-local` / `official-unconfigured`）；source/product/plan/offer ID 的双向 `demo-` 前缀规则；`SettingsAwareSyncService` 按模式路由 | `RemotePayloadNormalizerTest` 9 个测试；`HomeViewModelTest` 模式过滤测试 |
| 4 | 展示来源、最后成功同步、失败原因 | 首页常驻 `DataSourceStatusCard`；失败原因来自 `SyncMetadata.errorMessage` | `HomeViewModelTest.lastSuccessfulSyncAndFailureReason_areExposed`、`HomeScreenTest` 状态测试 |
| 5 | 无网络但展示缓存并提示过期 | `offline_cached`/`sync_failed_cached` 文案包含“数据可能已过期”；状态卡显示当前离线 | `HomeViewModelTest.offlineWithCache_keepsContentAndSkipsSync`、`HomeScreenTest.offlineWithCache` |
| 6 | 空/同步失败/DB 异常可恢复 | 空态与错误态有重新同步；官方模式有“切回演示数据”；DB 异常有“重试读取”并重新订阅 Flow；详情/统计也有重试 | `HomeViewModelTest.databaseReadFailure_isRecoverableThroughRetry`、`HomeScreenTest`、`DetailScreenTest`/`StatisticsScreenTest`（设备通过） |
| 7 | 数据库/同步/点击不在主线程 | Room suspend/Flow、DataStore、`withTransaction`、点击记录均走后台执行器；`OfferSyncService`/`NotifyingSyncService` 显式 `withContext(Dispatchers.IO)`；新增 StrictMode 校验测试 | `MainThreadOperationsTest`（设备通过） |
| 8 | 配置变更、进程重建、返回导航 | `recreate()`、连续新 Activity 启动、DataStore 新实例重载、设置页返回/系统返回测试 | `SettingsNavigationTest`（设备通过）、`DataStoreAppSettingsStoreTest`、`AppRoutesTest` |
| 9 | 文档写明官方适配器来源要求 | `docs/DATA_SOURCE_INTEGRATION.md` 明确 sourceUrl、fetchedAt、原文标题/摘要，缺失则不能标官方 | 文档已提交；README/HANDOFF 已链接 |

## 代码改动地图

### 新增

- `core/model/AppSettings.kt`
- `core/datastore/AppSettingsStore.kt`
- `core/datastore/DataStoreAppSettingsStore.kt`
- `core/network/UnconfiguredOfficialRemoteDataSource.kt`
- `data/SettingsAwareSyncService.kt`
- `test/.../SettingsAwareSyncServiceTest.kt`
- `feature/settings/SettingsViewModel.kt`
- `feature/settings/SettingsScreen.kt`
- `test/.../SettingsViewModelTest.kt`
- `test/.../DataStoreAppSettingsStoreTest.kt`
- `androidTest/.../SettingsScreenTest.kt`
- `androidTest/.../SettingsNavigationTest.kt`
- `androidTest/.../MainThreadOperationsTest.kt`
- `docs/DATA_SOURCE_INTEGRATION.md`

### 修改

- `AppContainer.kt`：双数据源、模式感知同步、自动同步偏好、四个 ViewModel factory。
- `AiOfferRadarApplication.kt`：不再无条件调度周期任务。
- `HomeViewModel.kt`：设置流、模式过滤、来源/同步状态、DB 错误重试、通知提示状态、模式切换清理旧错误。
- `HomeScreen.kt`：状态卡、设置入口、官方空态、DB 错误、通知“稍后”、来源按实际 offer 标记。
- `SettingsScreen.kt` / `SettingsViewModel.kt`：数据源切换、同步偏好、同步状态、通知权限与提示状态、可信度说明。
- `RemotePayloadNormalizer.kt`：抓取时间、demo 前缀双向隔离、空白标题校验。
- `LocalRadarRepository.kt` / `SourceInfoDao.kt`：来源类型不可变守卫。
- `OfferSyncService.kt`：写库前再次校验来源类型。
- `OfferSyncWorker.kt`：支持取消/启用周期任务。
- `DetailViewModel.kt` / `DetailScreen.kt` / `StatisticsViewModel.kt` / `StatisticsScreen.kt`：错误重试。
- `AiOfferRadarApp.kt`：`settings` 路由与入口。
- `strings.xml`：新增设置、数据源、数据库错误、官方空态、通知提示等文案。

## 验证记录

执行环境：macOS，JDK 17，Android SDK 35，Gradle 离线模式。

```bash
export GRADLE_USER_HOME=$PWD/.gradle-home
export ANDROID_USER_HOME=$PWD/.android-home
export GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches
./gradlew --offline --no-daemon clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

结果（JVM/Lint：2026-10-30 01:52；设备：2026-10-30 13:16）：
- 构建：`BUILD SUCCESSFUL`；clean 全量 81 tasks，最后一次增量 63 tasks
- JVM 单元测试：**21 suites / 122 tests / 0 failures / 0 errors / 0 skipped**
- Lint：**0 errors / 2 warnings**（既有 `POST_NOTIFICATIONS` API 标记、未使用 `radar_blue`）
- AndroidTest APK：编译打包成功
- 设备仪器测试：**77 / 77，0 failed / 0 errors / 0 skipped**（OPD2515 / Android 17 / API 37，serial <device-serial>，1m 53s）

设备测试证据：

- `docs/evidence/2026-10-30/phase9-connected-test-results.xml`
- `docs/evidence/2026-10-30/phase9-connected-tests.log`
- `docs/evidence/2026-10-30/phase9-device.txt`
- `docs/evidence/2026-10-30/phase9-unit-test-results/`（21 个 JVM XML）
- `docs/evidence/2026-10-30/phase9-lint-results-debug.txt`

复跑命令：

```bash
./gradlew --offline --no-daemon connectedDebugAndroidTest
```

## 已知边界

- 官方适配器仍未实现；`OFFICIAL` 模式是“未配置”状态，不会产生实时数据。这是阶段 9 的诚实边界，不是线上功能。
- 通知提示状态只记录“是否提示过”，系统权限本身仍以 `NotificationManagerCompat` 为准；用户撤销权限后状态会重新反映为未授权。
- 自动同步偏好只控制 WorkManager 周期任务；手动“立即同步”不受影响。
- 进程重建通过 DataStore 新实例与 Activity 重新启动验证，没有在仪器测试内真实 `killProcess`（会杀死测试进程）。
- 历史最低仍是同组同价近似（既有阶段 8 限制）。

## 下一步

1. 设备回归已完成一次（77 / 77，2026-10-30 13:16）；后续改动首页状态卡、官方空态、DB 错误重试、设置导航或 StrictMode 覆盖时仍须重跑全量设备测试。
2. 接入真实官方适配器时按 `docs/DATA_SOURCE_INTEGRATION.md` 执行，先把 `officialAdapterConfigured` 改成真实值并替换占位数据源。
3. 删除或更新 `isDemoMode` 相关历史描述，统一以 `AppSettings.dataSourceMode` 和实际 `SourceInfo.type` 为准。
