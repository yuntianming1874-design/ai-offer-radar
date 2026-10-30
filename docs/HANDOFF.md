# AI 优惠雷达：开发者交接文档

更新时间：2026-10-30  
交接范围：阶段 0–11 的代码、自动化验收与交付文档；阶段 11 手工设备验收 1–8 已于 2026-10-30 22:19–22:32 在 OPD2515 执行并归档。

## 一句话状态

项目是一个可构建、可安装、可测试的**本地优先 App**：Room、演示数据源、同步、订阅、历史价格、官网跳转、点击统计、WorkManager、通知、深链、设置页和数据源边界都已打通；**没有真实官方数据源，没有后端，没有 FCM 配置**。

`AppSettings.dataSourceMode` 默认 `DEMO`，也可以切到 `OFFICIAL`。官方模式目前固定注入 `UnconfiguredOfficialRemoteDataSource`，只会显示“官方数据（适配器未配置）”，不会产生实时优惠。不要把内置演示数据描述成实时优惠；首页“演示数据，非实时优惠”横幅是产品约束，不是临时占位。

## 当前验证基线

阶段 9/10/11 验证设备：OPD2515，Android 17 / API 37，adb 序列号 `<device-serial>`。  
2026-10-30 22:34 clean 后全量 `connectedDebugAndroidTest` 通过：**84 / 84**，0 failed / 0 errors / 0 skipped（1m 6s）。阶段 11 手工验收 1–8 已于 22:19–22:32 执行，证据在 `docs/evidence/2026-10-30/phase11/device-manual/`；阶段 10 之后没有生产代码变更。

| 项目 | 结果 |
|---|---|
| `clean assembleDebug testDebugUnitTest lintDebug` | `BUILD SUCCESSFUL`，50s，55 tasks（22:33） |
| JVM 单元测试 | 127 / 127，0 failed / 0 errors / 0 skipped（21 suites） |
| 设备仪器测试 | 84 / 84，0 failed / 0 errors / 0 skipped（22:34，1m 6s） |
| Lint | 0 errors，2 warnings（既有 `POST_NOTIFICATIONS` 标记、未使用 `radar_blue`） |
| 阶段 8/9/10/11 证据 | `docs/evidence/2026-10-30/` |
| 阶段 10 明细 | `docs/PHASE10_PROGRESS.md` |
| 阶段 9 明细 | `docs/PHASE9_PROGRESS.md` |
| 阶段 11 手工验收 | **已执行**（22:19–22:32）；`docs/PHASE11_DEVICE_ACCEPTANCE.md` |

原始 XML、构建日志、lint 报告、跨时区截图和深链直接运行日志都在 `docs/evidence/2026-10-30/`。阶段 9 的 77 项设备测试 XML 归档为 `phase9-connected-test-results.xml`；阶段 10 的 84 项归档为 `phase10-connected-test-results.xml`，JVM 21 个 XML 在 `phase10-unit-test-results/`；阶段 11 的 84 项与手工证据在 `phase11/`（`phase11-connected-test-results.xml`、`unit-test-results/`、`device-manual/`）。不要只引用旧文档里的“44 tests OK”或“全部完成”，以当前 XML 和复跑结果为准。


## 已完成的关键修复

### P0-1 官网 URL 数据链路

- `RemoteOfferDto` 增加 `officialUrl`。
- `RemotePayloadNormalizer` 不再固定写 `null`。
- 演示数据的 Kimi/DeepSeek/GLM 优惠带有官方入口 URL（`https://kimi.moonshot.cn/`、`https://www.deepseek.com/`、`https://chatglm.cn/`），仅用于跳转，不是价格来源。
- 端到端测试确认 URL 从远程 payload 进入 Room，再被 Repository 读出。

### P0-2 演示状态跨进程持久化

- `DemoRemoteDataSource` 通过 `DemoStateStore` 持久化场景、KIMI 演示价格、是否已新增优惠。
- 生产容器注入 `DataStoreDemoStateStore`；单元测试默认用 `InMemoryDemoStateStore`。
- 杀进程重启后不会退回基线并生成虚假的涨价/过期事件。

### P0-3 设备验收可复核

- 重写 `docs/PHASE8_PROGRESS.md`，删除不可复核的“44 tests OK / 已修复 launchSingleTop”等说法。
- 增加通知权限拒绝、渠道关闭、冷启动/热启动/不同产品/旋转/返回栈测试。
- 证据归档到 `docs/evidence/2026-10-30/`。

### P0-4 首次同步中间态崩溃

- `LocalRadarRepository` 的首页与详情查询从 `requireNotNull(source)` 改为跳过来源尚未到达的瞬时 offer。
- 避免冷启动深链或首次同步时 `combine` 中间态崩溃。

### P1-1 过期优惠自动消失

- `core/time/TimeTicker.kt` 每 30 秒发射当前时间。
- `HomeViewModel` 与 `DetailViewModel` 由 ticker 重算 current/upcoming/expired，页面常驻时过期优惠也会从“当前有效”移到历史区。

### P1-2 深链旋转与返回栈

- `MainActivity` 只在 `savedInstanceState == null` 时消费启动 intent。
- `onNewIntent` 始终消费新的通知深链，但**不调用 `setIntent()`**。
- 导航使用 `popUpTo(home)` 且不 `saveState`，详情之间不叠加，返回一次回首页。
- 重要：不要在 `onNewIntent` 里加 `setIntent()`。ActivityScenario 用原始 launch intent 匹配 Activity，改写后生命周期事件会被忽略，仪器测试会挂起。这个坑已经踩过一次，证据在 `docs/PHASE8_PROGRESS.md` 的 P1-2 段。

### P1-4 历史最低空历史语义

- `HistoricalLowestEvaluator` 要求至少一条合格的历史快照；没有历史时不再显示“历史最低”。
- `LocalRadarRepository.priorHistory` 在评估前移除当前观测对应的“同金额最新快照”，避免首次同步所有产品都标历史最低，也避免新增更便宜优惠时把旧优惠误标。
- 边界：同组多优惠同价的极端组合未覆盖；当前用“同金额最新快照”近似。

### P1-5 固定展示时区

- `core/time/DisplayTime.kt` 固定 `Asia/Shanghai`，输出 `yyyy-MM-dd HH:mm 北京时间`。
- 数据库仍存 UTC；不要改回 `ZoneId.systemDefault()`。
- 跨时区手工证据在 `docs/evidence/2026-10-30/timezone-*.png`。

### P1 快照主键冲突（早于 P1-4）

- `Offer.toSnapshot` 的快照 ID 包含 `observedAt`。
- 不能删除这个字段：演示数据 `updatedAt` 固定，价格往返到同一金额时，缺少观测时间会导致新快照被主键去重丢弃，图表少一个点。

## 阶段 9：设置、异常状态与数据源边界

### 设置持久化

- `core/model/AppSettings.kt`：`DataSourceMode { DEMO, OFFICIAL }`、`NotificationPromptState { NOT_ASKED, PROMPTED }`、`AppSettings`。
- `core/datastore/AppSettingsStore.kt` 接口 + `DataStoreAppSettingsStore.kt` 实现，文件 `app_settings`。
- 三个键：`data_source_mode`、`auto_sync_enabled`、`notification_prompt_state`；未知值回退默认，IOException 回退空配置。
- `AppContainer` 在应用作用域收集 `autoSyncEnabled`，调用 `OfferSyncScheduler.setEnabled`；**不要再在 Application 里无条件 schedule**，否则用户关闭的自动同步会在进程重启后复活。

### 数据源边界

- 演示源：`DemoRemoteDataSource`，`sourceId = demo-local`。
- 官方槽：`UnconfiguredOfficialRemoteDataSource`，`sourceId = official-unconfigured`，`fetch()` 永远抛“官方数据源适配器尚未配置”。
- `SettingsAwareSyncService` 每次 `sync()` 读取当前 `dataSourceMode`，只调用对应 source 的 `OfferSyncService`；不要绕过它直接把 demo source 注入官方同步。
- `RemotePayloadNormalizer` 现在校验：
  - 官方源必须有 `sourceUrl`；
  - `fetchedAt > Instant.EPOCH`；
  - source/product/plan/offer ID 与模式双向匹配：DEMO 必须 `demo-` 前缀，官方不得使用该前缀；
  - offer 标题非空。
- `LocalRadarRepository.applySync` 在事务内检查同一 sourceId 的类型不可变，违反直接抛异常回滚；`OfferSyncService` 写库前也检查一次。
- 首页按当前模式过滤 `offer.source.type`，产品卡片来源仍取实际 `offer.source.type`；切换模式不会 UPDATE 历史 `SourceInfo` 或快照。

### 异常状态与恢复

- `HomeViewModel` 状态：`HomeContentState`、`HomeError.OFFLINE/SYNC/SUBSCRIPTION/DATABASE`。
- 首页常驻 `DataSourceStatusCard`：来源、最后成功同步、离线/过期、失败原因。
- 数据库 Flow 抛错时进入 `HomeError.DATABASE`，提供“重试读取”：
  - `productsError`/`metadataError` 分开收集；
  - `retrySignal` 重新订阅已终止的 Flow；
  - 不清空已有缓存。
- 官方模式空态/错误态提供“切回演示数据”；演示模式空态/错误态提供重新同步。
- 详情页和统计页也补了 `retry()` 与“重试读取”按钮；`.catch` 必须放在 `retrySignal.flatMapLatest` 内部，否则异常会终止整个 `uiState` 流，`retry()` 更新信号后没有人再收集（已用 `localReadFailure_canBeRetried` 回归）。
- 通知横幅只在 `NotificationPromptState.NOT_ASKED` 时显示；“稍后/开启”都会持久化 `PROMPTED`。系统权限仍以 `NotificationManagerCompat` 实时查询为准。
- 模式切换可能发生在 `SettingsViewModel`，因此 `HomeViewModel` 监听 `settings.dataSourceMode` 变化并清空旧的 `operationError`；否则官方同步失败后切回演示会残留 SYNC 错误。

### 主线程约束

- Room 的 suspend DAO、Flow 查询、`database.withTransaction`、DataStore 读写、WorkManager `CoroutineWorker` 都不在主线程。
- `OfferSyncService` 与 `NotifyingSyncService` 显式 `withContext(Dispatchers.IO)`：即使从 `HomeViewModel.refresh()`、设置页“立即同步”或 WorkManager 调用，fetch、标准化、变更检测、写库和通知派发也不占用调用线程。
- `DetailViewModel` 的 `recordOfficialClick` 走 `viewModelScope` + suspend DAO；不要改成同步 DAO 或 `runBlocking`。
- `MainThreadOperationsTest` 用 StrictMode 在 main 线程跑同步、点击、订阅、首页/统计 Flow、设置读写，过滤 `com.aioffer.radar` 的 violation 并断言为空。该测试已在 2026-10-30 的设备全量回归中通过。

### 验证入口

- 首页：设置按钮、数据来源、最后成功同步、失败原因、官方空态、DB 错误重试。
- 设置页：数据源单选、自动同步 Switch、立即同步、通知权限、可信度说明、返回。
- 导航：首页 → 设置 → 返回；系统返回；配置变更/重新启动后设置保持。
- 进程重建：`DataStoreAppSettingsStoreTest` 用新 store 实例重载；设备侧用连续两次 Activity 启动验证。真实 `killProcess` 不能在仪器测试内做（会杀死测试进程）。


## 阶段 10：自动化测试与验收覆盖

- 领域规则：6 个 domain 文件均有单元测试；本阶段补现价 0 的 100% 折扣、官方 URL 大写 HTTPS、历史最低候选币种不一致、价格展示两个 `require` 分支。
- Room：补充 active 查询起点包含/终点排除、`applySync` 事务回滚、迁移保留 offer/快照/source 业务字段；原有仓储查询与订阅/点击/通知测试保留。
- 集成：`OfferSyncServiceIntegrationTest` 已覆盖首次同步、重复同步、价格往返、通知去重、失败保留 baseline、进程重启不重复；新增 `OfficialSourceIntegrationTest` 覆盖固定官方 Fake 源写入 Room 的 trust 元数据，以及 demo/official 同库共存不互改来源类型。
- UI：首页补“全部订阅”和已订阅卡“取消订阅”；详情图表测试断言月均折算、历史点数量、最高/最低值，而不是只验证页面出现。
- 官网安全与点击统计：既有 `OfficialUrlValidatorTest`、`DetailViewModelTest`、`OfficialOfferLauncherTest`、统计测试保留；本阶段补大写 scheme 与详情页错误分支。
- 主线程：`MainThreadOperationsTest` 在 StrictMode 下覆盖同步、点击、订阅、首页/统计 Flow、设置读写。
- 执行命令与结果：
  - `clean assembleDebug testDebugUnitTest lintDebug`：`BUILD SUCCESSFUL`，58s，55 tasks；JVM 127/127；Lint 0 errors / 2 warnings。
  - `connectedDebugAndroidTest`：84/84，0 failed / 0 errors / 0 skipped，1m 13s（OPD2515 / API 37）。
- 设备安装注意：ColorOS 的 `com.oplus.appdetail` 安装向导会卡住 Gradle 安装，需要人工点“继续安装/完成”；重装后测试 Activity 可能弹“30 天内允许”。**不要禁用 `com.oplus.appdetail`**：禁用后会落到系统安装器，对无 launcher icon 的 test APK 无法继续。
- 仍属未验证：API 26–36、其他 ROM、通知渠道关闭、厂商省电、长期 soak、真实官方源。设备测试只证明 OPD2515 / API 37 这一台。
- 明细与证据：`docs/PHASE10_PROGRESS.md`、`docs/evidence/2026-10-30/phase10-verification.txt`。

## 阶段 11：设备端到端验收与交付

- 交付说明已完成：`docs/DELIVERY.md`，覆盖构建/安装/测试、清数据首装、6 个演示场景、通知验证、详情/图表/官网/统计、无网络/空数据/来源失败/Custom Tab/非法 URL、真实源接入边界、FCM 缺口、WorkManager 非实时、演示/真实隔离、功能/测试/未验证对照表。
- 设备手工验收 1–8 **已执行**（2026-10-30 22:19–22:32，OPD2515 / API 37）：首装标识、订阅/取消/全部订阅与重启持久化、6 个演示场景、一条降价通知、通知深链、图表/历史最低/币种/周期/时间、官网 Custom Tab 与统计 +1、官方空态、离线缓存重启、失败原因展示。证据在 `docs/evidence/2026-10-30/phase11/device-manual/`，逐项结果见 `docs/PHASE11_DEVICE_ACCEPTANCE.md`。
- 构建与自动化：`clean assembleDebug testDebugUnitTest lintDebug` → 50s / 55 tasks；JVM 127/127；Lint 0 errors / 2 warnings；`connectedDebugAndroidTest` → 84/84，0 failed / 0 errors / 0 skipped，1m 6s。证据在 `docs/evidence/2026-10-30/phase11/`。
- ColorOS 限制：adb `pm clear` / `pm revoke POST_NOTIFICATIONS` 被 shell 权限拒绝（`CLEAR_APP_USER_DATA` / `REVOKE_RUNTIME_PERMISSIONS`）。首装用“此前未安装 + 全新安装”完成；通知权限通过 App 内“设置 → 开启通知”授予；清数据用卸载重装或系统设置。
- 未手工执行、仅自动化覆盖：非法 URL、无浏览器、数据库读取失败、清除点击记录，以及切换套餐后的图表更新、少于两个历史点。不要把它们写成手工通过。
- 阶段 11 新增文档：`docs/DELIVERY.md`、`docs/PHASE11_PROGRESS.md`、`docs/PHASE11_DEVICE_ACCEPTANCE.md`。
- 未验证/未实现：API 26–36/其他 ROM、通知渠道关闭/省电 soak、真实官方源、FCM/服务端推送、release 签名/CI。

## 代码地图

```text
app/src/main/java/com/aioffer/radar/
├── AppContainer.kt                手动 DI；修改数据源/时钟/通知时先看这里
├── MainActivity.kt                深链入口；注意上面 setIntent 的坑
├── AiOfferRadarApplication.kt     创建容器（周期任务由 AppContainer 依偏好调度）
├── core/
│   ├── database/                  Room schema v2、DAO、MIGRATION_1_2
│   ├── datastore/                 DataStoreDemoStateStore、AppSettingsStore/DataStoreAppSettingsStore
│   ├── network/                   RemoteDataSource、RemoteDtos、DemoRemoteDataSource、UnconfiguredOfficialRemoteDataSource
│   ├── notification/              渠道、AndroidNotificationSender、NotificationDispatcher
│   ├── push/                      PushNotificationService + NoOp
│   ├── sync/                      OfferSyncWorker + 调度（schedule/cancel/setEnabled）
│   ├── time/                      TimeTicker、DisplayTime
│   └── browser/                   Custom Tab / 浏览器回退
├── data/
│   ├── OfferSyncService.kt        Mutex、事务、基线、失败处理、来源类型二次校验
│   ├── SettingsAwareSyncService.kt 按 dataSourceMode 路由到 demo/official
│   ├── SyncChangeDetector.kt      快照、事件 ID、缺失优惠处理
│   ├── RemotePayloadNormalizer.kt DTO 校验/标准化/来源与 ID 隔离
│   └── LocalRadarRepository.kt    首页/详情/统计查询、来源类型不可变
├── domain/                        纯 Kotlin 规则
├── feature/                       Home / Settings / Detail / Statistics
└── navigation/                    Compose 路由（含 settings）与深链
```

### 关键不变量

1. **比较口径**：`ComparisonKey(productId, planId, region, currency, billingPeriod, taxIncluded)` 全等才能比较；免费/试用/学生/家庭/团队通过套餐类型和 `planId` 隔离。
2. **金额**：最小货币单位整数；折扣用 `DECIMAL128`；原价为 0、负价、币种不一致返回不可用。
3. **时间**：数据库 UTC；展示固定北京时间；有效性左闭右开 `startsAt <= now < endsAt`。
4. **历史**：`price_snapshots` 只追加；同步不得覆盖或删除旧快照；快照 ID 必须包含观测时间。
5. **基线**：首次成功同步只建基线，不生成通知事件；失败同步不能当基线。
6. **通知**：稳定事件 ID + DB 唯一约束；只对已订阅产品生成；`SENT` 的语义是“已提交给系统”，不是“用户已看到”。
7. **演示与真实**：演示数据必须保留 DEMO 标识；真实适配器必须带来源 URL、抓取时间、原文标题/摘要，否则标准化失败。不得把演示快照重新标成官方历史。
8. **数据源设置**：`dataSourceMode` 只控制展示范围与同步 sourceId；不得用切换模式 UPDATE `sources.type`、历史快照或旧同步元数据。同一 sourceId 类型不可变。
9. **ID 隔离**：演示 source/product/plan/offer ID 必须带 `demo-` 前缀；官方数据不得使用该前缀。
10. **失败语义**：失败同步保留 `lastSuccessfulSyncAt`，只更新 FAILED 与错误原因；UI 不得因此显示更晚的“最后成功同步”。

## 构建与测试

正常环境：

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

本工作区/沙箱曾遇到 `~/.gradle` 与 `~/.android` 不可写，因此创建了工作区内的：

```bash
export GRADLE_USER_HOME="$PWD/.gradle-home"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
./gradlew --offline --no-daemon clean assembleDebug testDebugUnitTest lintDebug
./gradlew --offline --no-daemon connectedDebugAndroidTest
```

`.gradle-home/`、`.android-home/`、`.kotlin/` 已加入 `.gitignore`；普通开发机不需要这些变量。不要把这些目录提交进仓库。

设备测试注意：

- 需要真机/模拟器，API 26+；当前验证设备为 API 37。
- ColorOS 安装 debug APK 可能弹“继续安装/完成”，不点会让 Gradle 安装卡住；重装后测试 Activity 可能弹“30 天内允许”。不要禁用 `com.oplus.appdetail`，禁用后会落到系统安装器，无法安装无 launcher icon 的 test APK。
- 多设备时设置 `ANDROID_SERIAL`。
- 仪器测试会卸载/重装 app，跑完后测试数据不会保留。

## 目录与文件

- 根 README：[README.md](../README.md)
- 交付说明：[DELIVERY.md](DELIVERY.md)
- 阶段 11 验收清单：[PHASE11_DEVICE_ACCEPTANCE.md](PHASE11_DEVICE_ACCEPTANCE.md)
- 阶段 11 进度：[PHASE11_PROGRESS.md](PHASE11_PROGRESS.md)
- 阶段 10 验收：[PHASE10_PROGRESS.md](PHASE10_PROGRESS.md)
- 阶段 9 验收：[PHASE9_PROGRESS.md](PHASE9_PROGRESS.md)
- 官方数据源接入契约：[DATA_SOURCE_INTEGRATION.md](DATA_SOURCE_INTEGRATION.md)
- 阶段 8 验收：[PHASE8_PROGRESS.md](PHASE8_PROGRESS.md)
- 阶段 6 验收：[PHASE6_PROGRESS.md](PHASE6_PROGRESS.md)
- 最新证据：[evidence/2026-10-30/README.md](evidence/2026-10-30/README.md)
- Room schema：`app/schemas/`
- 测试数据与 fake：`app/src/androidTest/java/com/aioffer/radar/core/testing/`、`app/src/test/...`
- 没有根级 `gradle/libs.versions.toml` 之外的隐藏依赖；依赖版本集中在 `gradle/libs.versions.toml`。

## 已知限制

1. **没有真实官方数据源**：`OFFICIAL` 模式固定使用 `UnconfiguredOfficialRemoteDataSource`，不会产生实时数据；切换过去只会看到未配置空态或同步失败态。
2. **设备矩阵有限**：当前只在 OPD2515 / API 37 上通过 84 / 84；阶段 11 手工 1–8 已在同一台设备执行。API 26–36、其他 ROM、通知渠道关闭、厂商省电策略和长期 soak 仍属未验证。
3. **没有后端/账号/支付**；“去订阅”只是打开官网入口，不完成购买。
4. **FCM 未接入**：`NoOpPushNotificationService` 只是边界。
5. **WorkManager 非实时**：15 分钟周期 + 系统调度；设备休眠、省电策略会延迟。演示时用首页“同步”按钮。
6. **通知权限/渠道边界**：拒绝或渠道关闭时事件保留 PENDING；恢复后补发；没有长期 soak。
7. **深链罕见路径**：已有 saved state 且系统用全新 intent 重建 Activity 时，启动 intent 会被跳过；常规冷启动/热启动/旋转已覆盖。
8. **历史最低近似**：同组多优惠同价时用“同金额最新快照”近似当前观测；演示数据不覆盖该组合。
9. **发布能力缺失**：没有 release 签名、CI、Play 配置、混淆策略；`release` 未开启 minify。
10. **Lint 仍有 2 个 warning**：`POST_NOTIFICATIONS` 常量 API 标记、未使用颜色 `radar_blue`。
11. **版本管理**：已初始化 git 仓库（`main`），阶段 11 验收基线为标签 `phase11-accepted`；远程仓库尚未配置。后续改动请提交到新 commit/分支，不要依赖本地未提交状态。
12. **文档历史**：`PHASE6_PROGRESS.md` 和旧版 `PHASE8_PROGRESS.md` 曾出现不可复核的完成声明；以根 README、阶段 8/9/10/11 文档和 evidence XML 为准。阶段 11 已执行的手工项与“仅自动化覆盖/未验证”项必须在文档中分开，不得混写成全部通过。

## 建议的下一步

1. **扩大设备矩阵与长期 soak**
   - 在 API 26–36、其他 ROM、通知渠道关闭、厂商省电策略和长期运行下复跑 `docs/PHASE11_DEVICE_ACCEPTANCE.md` 的 1–8。
   - 归档截图、UI dump、`dumpsys notification`、构建/测试日志和失败原因；未执行项保持“未验证”。
   - 自动化基线：2026-10-30 22:34 在 OPD2515 上 `connectedDebugAndroidTest` 84 / 84 通过；证据 `phase11/phase11-connected-test-results.xml`。后续 UI/同步/设置/导航改动仍需重跑，并优先覆盖 `SettingsNavigationTest`、`MainThreadOperationsTest`、`HomeScreenTest`、`SettingsScreenTest`。

2. **真实数据源里程碑**
   - 先做一个产品、一个公开稳定来源，不要一次接多家。
   - 按 `docs/DATA_SOURCE_INTEGRATION.md` 填 `sourceUrl`、`fetchedAt`、`titleOrSummary`、原文标题；价格/截止时间没有可靠来源就留空，不得猜。
   - 替换 `UnconfiguredOfficialRemoteDataSource`，同时把 `officialAdapterConfigured = true`、`officialSourceId` 改成真实 sourceId。
   - 先写标准化和失败测试，再写网络实现。

3. **发布前**
   - release 签名、CI、lint 清零、WorkManager soak、真实设备长期运行。
   - 复核通知 `SENT` 语义和渠道关闭行为。
   - 补深链“saved state + 新 intent”边界，或明确接受。

## 接手检查清单

- [x] `./gradlew clean assembleDebug testDebugUnitTest lintDebug` 通过（2026-10-30：127 JVM，0 failed）
- [x] `adb devices` 有设备后 `./gradlew connectedDebugAndroidTest` 通过（2026-10-30：84 / 84）
- [x] 首装首页有“演示数据，非实时优惠”，状态卡显示“数据来源：演示数据”
- [x] 设置页切到“官方数据”显示“适配器未配置”，不出现任何产品；返回首页仍是官方空态
- [x] 设置页切回“演示数据”，产品恢复且来源仍标“演示数据”
- [ ] 关闭自动同步后杀进程重启，周期任务没有复活
- [x] 断网时首页继续显示缓存并提示“数据可能已过期”，不崩溃
- [ ] 数据库读取失败（可用测试或故障注入）显示“重试读取”，恢复后内容回来
- [x] 订阅 KIMI → 价格下降 → 只收到一次通知
- [x] 点击通知进入 KIMI 详情，返回一次回首页
- [x] 详情“去订阅”打开官方入口，统计 +1
- [ ] 旋转详情页不重复打开旧详情，返回栈不叠加
- [x] 杀进程重启后订阅、演示价格、数据源设置仍在
- [x] 时间显示 `北京时间`，与设备系统时区无关
- [x] 不要重新引入 `onNewIntent` 里的 `setIntent()`
- [x] 不要把快照 ID 里的 `observedAt` 删掉
- [x] 不要绕过 `SettingsAwareSyncService` 直接把 demo source 注入官方同步
- [x] 阶段 11 手工 1–8 已完成并归档（`docs/evidence/2026-10-30/phase11/device-manual/`）
- [x] 核对 `docs/DELIVERY.md` 的功能/测试/未验证对照表与实际执行结果一致
- [ ] 在 API 26–36、其他 ROM、通知渠道关闭与省电 soak 下复跑
- [ ] 不要在没有证据时更新验收文档
