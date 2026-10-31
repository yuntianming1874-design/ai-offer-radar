# 2026-10-30 阶段 9/10/11/12 设备验收与历史证据

设备：OPD2515，Android 17 / API 37，adb 序列号 <device-serial>。

> 2026-10-31 的 endpoint 配置化 + CI 定时采集验收证据在
> [`../2026-10-31/README.md`](../2026-10-31/README.md)；本文件保留阶段 9–12 的历史基线。

## 阶段 12 GLM 官方源验收（2026-10-31 00:13）

| 项目 | 结果 |
|---|---|
| `clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` | `BUILD SUCCESSFUL`，81 tasks，54s |
| JVM 单元测试 | **134 / 134**，0 failed / 0 errors / 0 skipped（22 suites） |
| Lint | 0 errors，2 warnings |
| 采集器 payload 测试 | **4 / 4** |
| 设备仪器测试 | **86 / 86**，0 failed / 0 errors / 0 skipped，1m 5s |
| 采集器实测 | 3 products / 3 plans / 9 offers；5 分钟缓存；health ok |
| GLM 官方模式真机验收 | **已执行**：同步、年付/季付价格、失败缓存态、切回演示隔离 |

文件：

- `phase12/collector-payload.json`：真实采集输出（9 offers，季/年总价 + 月均）。
- `phase12/collector-health.json`：采集服务健康检查。
- `phase12/collector-serve.log`：`--serve` 启动与请求日志。
- `phase12/collector-tests.txt`：`node --test` 输出（4/4）。
- `phase12/official-page.png`：官方套餐页渲染截图。
- `phase12/unit-test-results/`：22 个 JVM 测试类 XML。
- `phase12/lint-results-debug.txt`：lint 文本报告。
- `phase12/phase12-connected-test-results.xml`：86 项仪器测试原始 XML。
- `phase12/phase12-connected.log`、`phase12-device.txt`：设备测试日志与设备信息。
- `phase12/device-manual/`：官方首页、季付详情、采集器停止失败态、切回演示的截图与 UI dump。
- `phase12/final-status.txt`、`environment.txt`：构建、采集器、设备与验收摘要。
- 进度：`docs/PHASE12_PROGRESS.md`。

未验证：API 26–36、其他 ROM、通知渠道/省电 soak；FCM、release/CI 仍未实现。

阶段 12 新增并执行的测试：JVM `GlmCodingPlanRemoteDataSourceTest`（6）、`PriceChartModelTest.create_quarterlySnapshotsUsesMonthlyEquivalent`（1）；设备 `GlmCodingPlanSourceIntegrationTest`（1）、`HomeScreenTest.productCard_prefersLowestMonthlyEquivalentQuarterlyOffer`（1），其余为原有 84 项回归。

## 阶段 11 设备验收与交付（2026-10-30 22:34）

| 项目 | 结果 |
|---|---|
| 要求 9 命令 `clean assembleDebug testDebugUnitTest lintDebug` | `BUILD SUCCESSFUL`，50s，55 tasks |
| JVM 单元测试 | **127 / 127**，0 failed / 0 errors / 0 skipped（21 suites） |
| Lint | 0 errors，2 warnings |
| 设备手工验收 1–8 | **已执行**：2026-10-30 22:19–22:32，OPD2515 / API 37 |
| 设备自动化回归 | **84 / 84**，0 failed / 0 errors / 0 skipped，1m 6s（22:34） |

文件：

- `phase11/final-status.txt`：阶段 11 最终构建、设备自动化、手工验收与 ColorOS 限制摘要。
- `phase11/device-manual/`：步骤 1–8 的截图、UI dump、通知记录、构建日志、设备自动化日志与 XML。
- `phase11/phase11-connected-test-results.xml`：84 项仪器测试原始 XML。
- `phase11/unit-test-results/`：21 个 JVM 测试类 XML。
- `phase11/lint-results-debug.txt`：本次 lint 文本报告。
- 交付说明：`docs/DELIVERY.md`。
- 手工验收清单：`docs/PHASE11_DEVICE_ACCEPTANCE.md`。
- 进度：`docs/PHASE11_PROGRESS.md`。

未验证：API 26–36、其他 ROM、通知渠道关闭/省电 soak；阶段 12 之前未接入真实官方源（阶段 12 已接入 GLM，见上）；FCM；发布签名/CI。非法 URL、无浏览器、数据库读取失败等异常未手工制造，仅由自动化覆盖。

## 阶段 10 设备测试（2026-10-30 14:51）

| 项目 | 结果 |
|---|---|
| 要求 8 命令 `clean assembleDebug testDebugUnitTest lintDebug` | `BUILD SUCCESSFUL`，58s，55 tasks |
| JVM 单元测试 | **127 / 127**，0 failed / 0 errors / 0 skipped（21 suites） |
| 仪器测试 | **84 / 84**，0 failed / 0 errors / 0 skipped，Gradle 1m 13s |
| Lint | 0 errors，2 warnings |
| 设备 | OPD2515 / Android 17 / API 37 / serial <device-serial> |

阶段 10 新增并执行的测试：`OfficialSourceIntegrationTest`（2）、`LocalRadarRepositoryTest` 事务回滚与 active 边界（2）、`RadarDatabaseMigrationTest` 业务数据迁移（1）、`HomeScreenTest` 全部订阅/取消订阅（2），以及 5 个领域边界单元测试。设备测试从阶段 9 的 77 项增加到 84 项。

文件：

- `phase10-connected-test-results.xml`：84 项仪器测试原始 XML。
- `phase10-unit-test-results/`：21 个 JVM 测试类 XML。
- `phase10-lint-results-debug.txt`：本次 lint 文本报告。
- `phase10-device.txt`：设备型号、Android 版本、SDK、构建号。
- `phase10-verification.txt`：命令、计数与 ColorOS 安装向导处置记录。

未验证：API 26–36 与其他 ROM；通知渠道关闭、厂商省电、长期 soak；真实官方数据源。

## 阶段 9 设备测试（2026-10-30 13:16）

| 项目 | 结果 |
|---|---|
| JVM 单元测试 | 122 / 122，0 failed / 0 errors / 0 skipped（21 suites） |
| 仪器测试 | **77 / 77**，0 failed / 0 errors / 0 skipped，Gradle 1m 53s |
| Lint | 0 errors，2 warnings |
| 构建 | `testDebugUnitTest lintDebug assembleDebugAndroidTest` BUILD SUCCESSFUL |

阶段 9 新增并执行的设备测试：`SettingsNavigationTest`（设置入口/返回/系统返回/重建/新 Activity 启动）、`MainThreadOperationsTest`（StrictMode 下同步、点击、订阅、Room Flow、DataStore）、`HomeScreenTest`（状态卡、官方空态、DB 错误、通知提示）、`SettingsScreenTest`（数据源、自动同步、通知、重试）。

文件：

- `phase9-connected-test-results.xml`：77 项仪器测试原始 XML。
- `phase9-connected-tests.log`：本次 Gradle 设备测试完整输出。
- `phase9-unit-test-results/`：21 个 JVM 测试类 XML。
- `phase9-lint-results-debug.txt`：本次 lint 文本报告。
- `phase9-device.txt`：设备型号、Android 版本、SDK、构建号。

## 阶段 8 / P1-1 基线结果

| 项目 | 结果 |
|---|---|
| 干净构建 | BUILD SUCCESSFUL，1m 10s，81 tasks executed |
| 单元测试 | 97 / 97，0 failed / 0 errors / 0 skipped |
| 仪器测试 | 58 / 58，0 failed / 0 errors / 0 skipped，Gradle 3m 5s |
| Lint | 0 errors，2 warnings |

## 文件

- `unit-test-results/`：阶段 8 的 18 个单元测试类 JUnit XML。
- `connected-test-results.xml`：阶段 8 的 58 项仪器测试原始 XML。
- `deep-link-direct-instrument.log`：仅 `NotificationDeepLinkTest` 的 7 项直接 `am instrument` 输出。
- `first-sync-crash-before-fix.txt`：修复前首次同步崩溃的 crash buffer，含 `LocalRadarRepository.kt:34` 堆栈。
- `lint-results-debug.txt`：阶段 8 lint 文本报告。
- `build.log`：阶段 8 干净构建的完整 Gradle 输出。
- `connected-tests.log`：阶段 8 的 58 项仪器测试的完整 Gradle 输出。

## 阶段 8 新增测试

- `HomeViewModelTest.offerExpiresWhileScreenIsOpen_disappearsFromCurrentOffers`
- `DetailViewModelTest.offerExpiresWhileDetailIsOpen_movesFromCurrentToExpired`
- `TimeTickerTest.emitsImmediatelyAndThenAtTheConfiguredInterval`
- `NotificationDeepLinkTest.rotationAfterBackToHome_doesNotReopenConsumedDeepLink`
- `NotificationDeepLinkTest.rotationOnDetail_keepsDetailAndBackReturnsHomeOnce`
- `NotificationDeepLinkTest.rotationAfterHotDeepLink_keepsLatestProductAndBackReturnsHome`
- `NotificationDeepLinkTest.multipleHotDeepLinks_backReturnsHomeOnce`
- `LocalRadarRepositoryTest.offerWithMissingSource_isSkippedInsteadOfCrashing`
- `HistoricalLowestEvaluatorTest.withoutHistory_isNotHistoricalLowest`
- `HistoricalLowestEvaluatorTest.otherComparisonGroupsOnly_areNotHistoricalLowest`
- `HistoricalLowestEvaluatorTest.onlyFreeAndNegativeHistory_isNotPaidHistoricalLowest`
- `HistoricalLowestEvaluatorTest.onlyYearlyHistory_isNotMonthlyHistoricalLowest`
- `OfferSyncServiceIntegrationTest.firstSync_hasNoHistoricalLowestBadge`
- `OfferSyncServiceIntegrationTest.priceDecrease_marksOnlyItsComparisonGroupAsHistoricalLowest`
- `OfferSyncServiceIntegrationTest.newOffer_marksOnlyTheCheaperOfferAsHistoricalLowest`

## 阶段 9 新增测试

JVM：

- `DataStoreAppSettingsStoreTest`：默认值、持久化重载、未知枚举回退。
- `HomeViewModelTest`：官方模式隐藏演示、切换不重标来源、最后成功同步/失败原因、DB 读取失败重试、通知提示持久化、模式切换清理旧错误。
- `SettingsViewModelTest`：数据源元数据切换、离线同步提示、本地读取重试、偏好持久化。
- `SettingsAwareSyncServiceTest`：DEMO/OFFICIAL 路由与每次重新读模式。
- `RemotePayloadNormalizerTest`：抓取时间必填、`demo-` 前缀双向隔离、空白标题拒绝。
- `DetailViewModelTest` / `StatisticsViewModelTest`：读取失败后可重试恢复。

设备：

- `SettingsNavigationTest`：设置入口、返回、系统返回、`recreate()`、连续两次启动后的设置保持。
- `MainThreadOperationsTest`：StrictMode 下同步、点击、订阅、Room Flow、DataStore 操作无主线程磁盘 violation。
- `HomeScreenTest`：来源/最后成功同步/失败原因、官方空态、DB 错误重试、通知提示不重复。
- `SettingsScreenTest`：数据源切换警告、自动同步、同步状态、离线提示、本地读取重试、通知入口、返回。
- `LocalRadarRepositoryTest.applyingSync_cannotRelabelAnExistingSourceType`：来源类型不可变。

## 阶段 10 新增测试

JVM：

- `DiscountCalculatorTest.zeroCurrentPrice_isFullDiscount`
- `OfficialUrlValidatorTest.uppercaseHttpsScheme_isAccepted`
- `HistoricalLowestEvaluatorTest.candidateCurrencyMismatch_isNotHistoricalLowest`
- `PriceDisplayModelFactoryTest.offerPriceCurrencyMismatch_isRejected`
- `PriceDisplayModelFactoryTest.monthlyEquivalentCurrencyMismatch_isRejected`

设备：

- `HomeScreenTest.subscribeAllAction_reportsClick`
- `HomeScreenTest.subscribedProduct_showsUnsubscribeAndReportsChange`
- `LocalRadarRepositoryTest.activeOffers_includeStartBoundaryAndExcludeEndBoundary`
- `LocalRadarRepositoryTest.failedApplySync_rollsBackEveryWriteInTheTransaction`
- `RadarDatabaseMigrationTest.migrate1To2_preservesOffersSnapshotsAndSourceFetchMetadata`
- `OfficialSourceIntegrationTest.officialFakeSource_persistsTrustMetadataIntoRoom`
- `OfficialSourceIntegrationTest.demoAndOfficialSources_coexistWithoutChangingEachOtherTypes`

## 手工验证

1. 关闭 Wi-Fi 与移动数据，避免启动同步覆盖测试数据。
2. 将 Room 中 KIMI 优惠的 `endsAtEpochMillis` 改为当前时间 + 25 秒后启动 App。
3. 启动约 5 秒时首页显示 `KIMI ¥100.00/月`；约 40 秒后变为 `KIMI 暂无有效优惠`。
4. 打开 KIMI 详情：当前有效优惠区域显示“当前筛选条件下暂无有效优惠”，该优惠出现在“历史优惠”并显示截止时间。
5. 恢复网络并重启，演示同步把 KIMI 恢复为 `¥100.00/月`。

## P1-5 固定展示时区

1. 将设备系统时区切到 `America/Los_Angeles`。
2. 首页仍显示 `更新时间：2026-06-01 08:00 北京时间`，不是 `2026-05-31 17:00`。
3. 打开 KIMI 详情，`生效：2026-01-01 08:00 北京时间`、`更新时间：2026-06-01 08:00 北京时间`。
4. 截图：`timezone-home-los-angeles.png`、`timezone-detail-los-angeles.png`。
5. 验证后把设备时区恢复为 `Asia/Shanghai`。

## P1-4 历史最低空历史语义

1. 全新安装、首次同步完成后，首页三张卡片都不显示“历史最低”。
2. 通过“切换演示场景 → 价格下降”触发 KIMI 10000→8000 后，仅 KIMI 卡片显示“历史最低”，DEEPSEEK/GLM 不显示。
3. 单元测试覆盖空历史、仅其他比较分组、仅免费/负价、仅年付历史；集成测试覆盖首次同步、价格下降、同组新增更便宜优惠三种路径。

## 深链旋转与返回栈

1. `MainActivity` 只在 `savedInstanceState == null` 时消费启动 intent；配置变更由 NavController 的 saved state 恢复页面，不再重复打开旧详情。
2. `onNewIntent` 始终消费新的通知深链，但不调用 `setIntent`，避免 ActivityScenario 因 launch intent 被改写而忽略生命周期事件。
3. 导航使用 `popUpTo(home)` 且不 `saveState`；从通知进入不同产品后，一次返回即回首页，不会留下旧详情。
4. 自动化覆盖冷启动、同产品热启动、不同产品热启动、旋转、返回后再旋转、多次热启动一次返回，共 7 项；`deep-link-direct-instrument.log` 为直接运行结果。
5. 设备手工验证：冷启动深链进入 KIMI 详情，返回一次到首页；热启动切到 DEEPSEEK 详情，旋转后仍为 DEEPSEEK，返回一次到首页。

## 首次同步中间态崩溃修复

- 现象：全新安装后首次冷启动，`LocalRadarRepository.observeHomeProducts` 在 `combine` 的中间发射中拿到 offer 但还没拿到对应 source，`requireNotNull` 抛 `IllegalArgumentException`，进程崩溃。
- 修复：首页与详情页都改为 `mapNotNull`，缺少来源的中间态跳过，下一次数据库发射补齐；不再让 UI 层因瞬时不一致崩溃。
- 回归测试：`LocalRadarRepositoryTest.offerWithMissingSource_isSkippedInsteadOfCrashing`，同时验证首页与详情页都跳过孤儿 offer、保留正常 offer。
- 手工验证：卸载后重新安装、直接冷启动深链，首页/详情正常，无崩溃。

## 复现命令

```bash
export GRADLE_USER_HOME="$PWD/.gradle-home"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
export ANDROID_SERIAL=<device-serial>

./gradlew --offline --no-daemon \
  clean assembleDebug testDebugUnitTest lintDebug
./gradlew --offline --no-daemon connectedDebugAndroidTest
```
