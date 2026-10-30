# 阶段 10：自动化测试与验收覆盖

更新时间：2026-10-30  
范围：阶段 9 已完成后的测试审计与补齐；不新增业务功能。

## 结果摘要

| 项目 | 结果 |
|---|---|
| 要求 8 命令 `clean assembleDebug testDebugUnitTest lintDebug` | `BUILD SUCCESSFUL`，58s，55 tasks |
| JVM 单元测试 | **21 suites / 127 tests**，0 failed / 0 errors / 0 skipped |
| Lint | **0 errors / 2 warnings**（既有 `POST_NOTIFICATIONS` API 标记、未使用 `radar_blue`） |
| 设备仪器测试 `connectedDebugAndroidTest` | **84 / 84**，0 failed / 0 errors / 0 skipped，1m 13s |
| 验证设备 | OPD2515，Android 17 / API 37，serial <device-serial> |

设备测试是在上述 clean 构建之后重新执行的；不是复用旧 APK 的结果。

## 9 条要求的对应证据

### 1. 领域规则单元测试

已覆盖 `domain/` 下全部 6 个规则文件。本阶段补了 5 个此前缺失的错误/边界分支：

- `DiscountCalculatorTest.zeroCurrentPrice_isFullDiscount`：现价 0 → 100% 折扣，方向 `DECREASE`。
- `OfficialUrlValidatorTest.uppercaseHttpsScheme_isAccepted`：`HTTPS://EXAMPLE.COM/Path?A=B` 被接受并原样返回。
- `HistoricalLowestEvaluatorTest.candidateCurrencyMismatch_isNotHistoricalLowest`：比较键为 USD、候选价为 CNY 时不得判历史最低。
- `PriceDisplayModelFactoryTest.offerPriceCurrencyMismatch_isRejected`：优惠总价币种与 `ComparisonKey` 不一致时抛 `IllegalArgumentException`。
- `PriceDisplayModelFactoryTest.monthlyEquivalentCurrencyMismatch_isRejected`：月均折算币种与总价币种不一致时抛 `IllegalArgumentException`。

现有领域覆盖：折扣方向/负价/原价 0/币种、历史最低空历史/跨组/免费/试用/年付/负价、优惠生效截止左闭右开、URL scheme/host/userInfo、价格格式化与图表模型。

### 2. Room 查询、事务和迁移

`app/src/androidTest/java/com/aioffer/radar/data/LocalRadarRepositoryTest.kt`（14 项）：

- `activeOffers_includeStartBoundaryAndExcludeEndBoundary`：`startsAt == now` 有效、`endsAt == now` 过期、`startsAt == now + 1ms` 未开始；断言 active 集合精确为 `offer-1` 与 `starts-now`。
- `failedApplySync_rollsBackEveryWriteInTheTransaction`：用缺失 `planId` 的 offer 触发外键失败，断言产品/计划/优惠均未落库、原有商品和优惠数量不变、同步元数据未被更新。验证 `database.withTransaction` 的真实回滚，而不是只断言抛异常。

`app/src/androidTest/java/com/aioffer/radar/core/database/RadarDatabaseMigrationTest.kt`（2 项）：

- 既有 `migrate1To2_preservesSubscriptionsAndSyncMetadata`。
- 新增 `migrate1To2_preservesOffersSnapshotsAndSourceFetchMetadata`：迁移后 offer 的 `officialUrl`/金额/来源、快照金额、来源类型与抓取时间均保留，新列 `lastSuccessfulSyncAtEpochMillis` 为 null。

### 3. Fake 数据源到 Room 的集成

- 演示源：既有 `OfferSyncServiceIntegrationTest`（13 项）覆盖首次同步、重复同步、价格往返、通知去重、失败保留基线、进程重启。
- 官方源：新增 `app/src/androidTest/java/com/aioffer/radar/data/OfficialSourceIntegrationTest.kt`（2 项）：
  - `officialFakeSource_persistsTrustMetadataIntoRoom`：`FixedPayloadRemoteDataSource` 的 `OFFICIAL_API` payload 落库后，source 的 URL/抓取时间、`product.isDemo=false`、`offer.source.type=OFFICIAL_API`、`officialUrl` 均可从 Room 读回。
  - `demoAndOfficialSources_coexistWithoutChangingEachOtherTypes`：先 demo 再 official，断言两个 source 类型互不影响，首页查询中各自的 offer 来源类型正确。这是“演示/官方隔离”的真实数据库断言。

### 4. 首次同步、重复同步、通知去重

沿用并通过 `OfferSyncServiceIntegrationTest`（13 项）：

- 首次成功同步只建基线，0 通知事件。
- 重复同步不重复追加同价快照、不重复通知。
- 降价/新优惠只通知已订阅产品一次。
- 未订阅不通知；网络失败保留基线/最后成功同步；失败首同步不建基线。
- 进程重启后共享 `DemoStateStore`，不重复产生事件/快照。

### 5. 首页订阅与详情图表 UI

- `HomeScreenTest` 16 项，本阶段新增：
  - `subscribeAllAction_reportsClick`：顶部“全部订阅”回调。
  - `subscribedProduct_showsUnsubscribeAndReportsChange`：已订阅产品显示“取消订阅”，点击回调 `false`。
- 既有首页 UI：来源/更新时间/价格/历史最低/设置入口/来源状态卡/官方空态/DB 错误/通知提示/离线缓存。
- 详情图表：`DetailScreenTest` 与 `PriceChartModelTest` 断言“月均折算”、两个历史点、最高 20/最低 10 等具体值，不只断言不崩溃。

### 6. 官网跳转安全和点击统计

- `OfficialUrlValidatorTest` 5 项：仅 HTTPS（生产）、大写 HTTPS、畸形 host、非法 scheme、userInfo。
- `OfficialOfferLauncherTest` 4 项、`OfficialUrlEndToEndTest`、`DetailViewModelTest`：URL 从 Room 读出、Custom Tab/浏览器回退、无浏览器/打开失败提示。
- 点击统计：`LocalRadarRepositoryTest` 统计与清空、`StatisticsScreenTest` 3 项、`StatisticsViewModelTest` 3 项；点击记录走 suspend DAO，主线程 StrictMode 测试覆盖。

### 7. 验证真实结果，而不只是“没有抛异常”

本阶段新增测试均断言具体数据或状态：

- 事务回滚断言库内产品/计划/优惠数量与 metadata 值，不仅 `assertThrows`。
- 迁移断言迁移后具体列值。
- 官方集成断言 source 类型、URL、抓取时间、`isDemo`、offer 来源类型。
- 领域测试断言具体 fraction/方向/返回值/异常类型。
- UI 测试断言具体文案、节点是否存在、回调参数。

### 8. 完整构建命令

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug
```

实际带沙箱环境变量执行：

```bash
GRADLE_USER_HOME=$PWD/.gradle-home \
ANDROID_USER_HOME=$PWD/.android-home \
GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
./gradlew --offline --no-daemon clean assembleDebug testDebugUnitTest lintDebug
```

结果：`BUILD SUCCESSFUL in 58s`，55 tasks；JVM 127 / 127；Lint 0 errors / 2 warnings。

### 9. 设备测试

```bash
./gradlew connectedDebugAndroidTest
```

结果：`Starting 84 tests on OPD2515 - 17` → `Finished 84 tests`，0 failed / 0 skipped，`BUILD SUCCESSFUL in 1m 13s`。

设备测试类分布：

| 测试类 | 数量 |
|---|---:|
| HomeScreenTest | 16 |
| LocalRadarRepositoryTest | 14 |
| OfferSyncServiceIntegrationTest | 13 |
| NotificationDeepLinkTest | 7 |
| SettingsScreenTest | 7 |
| DetailScreenTest | 4 |
| SettingsNavigationTest | 4 |
| OfficialOfferLauncherTest | 4 |
| NotificationDispatcherTest | 3 |
| StatisticsScreenTest | 3 |
| RadarDatabaseMigrationTest | 2 |
| OfficialSourceIntegrationTest | 2 |
| AndroidNotificationSenderTest | 1 |
| OfferSyncWorkerTest | 1 |
| MainThreadOperationsTest | 1 |
| OfficialUrlEndToEndTest | 1 |
| SubscriptionPersistenceTest | 1 |

## 新增/修改的测试文件

新增：

- `app/src/androidTest/java/com/aioffer/radar/data/OfficialSourceIntegrationTest.kt`

修改：

- `app/src/test/java/com/aioffer/radar/domain/DiscountCalculatorTest.kt`
- `app/src/test/java/com/aioffer/radar/domain/OfficialUrlValidatorTest.kt`
- `app/src/test/java/com/aioffer/radar/domain/HistoricalLowestEvaluatorTest.kt`
- `app/src/test/java/com/aioffer/radar/domain/PriceDisplayModelFactoryTest.kt`
- `app/src/androidTest/java/com/aioffer/radar/data/LocalRadarRepositoryTest.kt`
- `app/src/androidTest/java/com/aioffer/radar/core/database/RadarDatabaseMigrationTest.kt`
- `app/src/androidTest/java/com/aioffer/radar/feature/home/HomeScreenTest.kt`

## 证据

目录：`docs/evidence/2026-10-30/`

- `phase10-connected-test-results.xml`：84 项仪器测试原始 XML。
- `phase10-unit-test-results/`：21 个 JVM 测试类 XML。
- `phase10-lint-results-debug.txt`：本次 Lint 文本报告。
- `phase10-device.txt`：设备型号/Android/SDK/构建号。
- `phase10-verification.txt`：命令、计数与安装向导处置记录。

## 未验证项

- 设备矩阵仅 OPD2515 / Android 17 / API 37；API 26–36、模拟器、其他厂商 ROM 未执行。
- ColorOS 安装 debug APK 的“继续安装/完成”确认、以及重装后的“30 天内允许”启动确认需要人工或 watcher 处理；未做成 CI 自动化。
- 通知渠道关闭、长期省电/后台限制、长期 soak 未覆盖。
- 真实官方数据源仍不存在；官方集成测试使用固定 Fake payload。
- 未做 release 构建、签名、混淆和 CI 验证。
