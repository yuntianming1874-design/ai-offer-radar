# AI 优惠雷达：开发者交接文档

更新时间：2026-11-01
交接范围：阶段 0–12 的代码、自动化验收与交付文档；阶段 11 手工设备验收 1–8 已于 2026-10-30 22:19–22:32 在 OPD2515 执行并归档；阶段 12 已接入第一条真实 GLM Coding Plan 官方源，86 项仪器测试与真机官方模式验收已于 2026-10-31 00:10–00:13 完成；随后补充了 **endpoint 构建时配置 + GitHub Actions 定时采集**（公开仓库已推送，Pages 已启用，workflow 已真实运行成功）；P1 又加入了**部署后线上字节/新鲜度校验、Android Release build job、失败/2h 过期 Issue 告警**，并已在 GitHub Actions 远端首跑成功（[run 41182662449](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41182662449)）。P2 已完成**失败告警远端演练**（Issue #1 创建/去重/恢复关闭、失败 run 置红）、**unsigned Release workflow 远端构建**和**正式签名 Release 远端构建**（run 41221071911）；签名 keystore 仅在本机/Keychain，不入库。

## 一句话状态

项目是一个可构建、可安装、可测试的**本地优先 App**：Room、演示数据源、真实 GLM 官方源、同步、订阅、历史价格、官网跳转、点击统计、WorkManager、通知、深链、设置页和数据源边界都已打通；**没有后端，没有 FCM 配置**。真实官方源优先从 CI 定时发布的 HTTPS 静态 JSON 读取，Debug 也可连本机渲染采集器；官方快照超过 2 小时会被客户端拒绝。

`AppSettings.dataSourceMode` 默认 `DEMO`，也可以切到 `OFFICIAL`。官方模式使用 `GlmCodingPlanRemoteDataSource`（sourceId `glm-coding-plan-official`），endpoint 来自 `BuildConfig.GLM_COLLECTOR_URL`：Debug 默认本机 `http://127.0.0.1:8787/v1/glm-coding-plan`，Release 通过 `-PglmCollectorUrl=https://.../glm-coding-plan.json` 注入，未配置时明确失败关闭。采集失败或快照过期时显示真实失败原因并保留缓存，不把旧价说成实时价。不要把内置演示数据描述成实时优惠；首页“演示数据，非实时优惠”横幅是产品约束，不是临时占位。

## 当前验证基线

阶段 9/10/11/12 验证设备：OPD2515，Android 17 / API 37，adb 序列号 `<device-serial>`。2026-10-30 22:34 clean 后阶段 11 `connectedDebugAndroidTest` 通过：**84 / 84**（1m 6s）；阶段 11 手工验收 1–8 已于 22:19–22:32 执行，证据在 `docs/evidence/2026-10-30/phase11/device-manual/`。
2026-10-31 00:13 阶段 12 `connectedDebugAndroidTest` 通过：**86 / 86**，0 failed / 0 errors / 0 skipped（1m 5s）；官方模式真机同步、季付总价/月均、采集器失败缓存态与切回演示隔离均已验收，证据在 `docs/evidence/2026-10-30/phase12/device-manual/`。
2026-10-31 00:53 阶段 12 增强（endpoint + CI）复跑：clean 56s / 84 tasks；JVM **137 / 137（23 suites）**；Lint 0 errors / 2 warnings；`connectedDebugAndroidTest` **86 / 86**（1m 8s）；采集器 Node 测试 **10 / 10**；Release endpoint 两种构建均通过。
2026-10-31 15:48 P1 本地验证：Node **15 / 15**；线上 `verify-published.mjs` 通过；`testDebugUnitTest assembleRelease` 33s / 76 tasks；JVM **137 / 137**；helper APK 签名脚本通过；两个 workflow YAML 解析通过。
2026-10-31 16:02 P1 远端首跑：`collect-and-publish` 34s，部署后校验第 1 次通过（3 档 / 9 offers，SHA-256 `3357c7cd…`）；`build-android` 3m 4s（Gradle `BUILD SUCCESSFUL in 2m 29s`，BuildConfig 含线上 URL，helper APK `8783870e…`）；`close-recovered-issue` 成功；`glm-snapshot-health` 7s 成功。
2026-10-31 23:23–23:30 P2 远端：health `max_age_seconds=1` 创建 Issue #1（run 41214658712）；重复失败去重且 run 置红（run 41214743795）；默认 7200 恢复并关闭 Issue（run 41214800226）。`release.yml` unsigned 构建成功（run 41214833322，2m22s，versionCode 2 / 1.0.1-ci，APK SHA-256 `cf32651a…`，apksigner `DOES NOT VERIFY`）；`sign_release=true` 缺 Secrets 时 22s 失败关闭（run 41215119074）。
2026-11-01 00:43–00:46 P2 正式签名：四个 Secrets 配置后 `sign_release=true` 成功（run 41221071911，1m59s，versionCode 1 / 1.0.0）；v2 证书 SHA-256 `54f6ee32…`；artifact SHA-256 `b08b6208…`；已下载验签，设备断开前未再次安装。

| 项目 | 结果 |
|---|---|
| `clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` | `BUILD SUCCESSFUL`，84 tasks，56s（2026-10-31 00:53，阶段 12 增强复跑） |
| JVM 单元测试 | 137 / 137，0 failed / 0 errors / 0 skipped（23 suites） |
| 设备仪器测试 | 阶段 12 **86 / 86 通过**（2026-10-31 00:52，1m 8s）；阶段 11 历史基线 84 / 84 |
| Lint | 0 errors，2 warnings（既有 `POST_NOTIFICATIONS` 标记、未使用 `radar_blue`） |
| 采集器/发布校验测试 | 15 / 15（4 payload + 6 校验/CLI + 5 发布端点校验） |
| P1 CI 自证 | 远端首跑通过：部署后 HTTP/2h/SHA-256、Android build job、恢复关闭 job、health workflow；P2 已补失败路径演练 |
| P2 失败告警演练 | 远端已实测：Issue #1 创建、1 小时去重、恢复关闭、失败 run 置红；手动 1s 阈值演练，不是真实 stale 事故 |
| P2 Release 签名 | `release.yml` + Gradle 条件 signingConfig + `RELEASE_SIGNING.md`；unsigned 与正式签名远端构建均成功（run 41221071911，v2 证书 `54f6ee32…`）；keystore 仅在本机/Keychain |
| Release endpoint | 无 property 时 `GLM_COLLECTOR_URL=""`；`-PglmCollectorUrl=https://example.com/glm-coding-plan.json` 时 BuildConfig 正确注入，`assembleRelease` 通过 |
| 阶段 8/9/10/11 证据 | `docs/evidence/2026-10-30/` |
| 阶段 12 证据 | `docs/evidence/2026-10-30/phase12/` |
| 阶段 12 增强证据 | `docs/evidence/2026-10-31/`（23 JVM XML、86 项设备 XML、Node/Release 验证） |
| 阶段 12 明细 | `docs/PHASE12_PROGRESS.md` |
| 阶段 11 手工验收 | **已执行**（22:19–22:32）；`docs/PHASE11_DEVICE_ACCEPTANCE.md` |

原始 XML、构建日志、lint 报告、采集器日志和跨时区截图都在 `docs/evidence/2026-10-30/`。阶段 9 的 77 项设备测试 XML 归档为 `phase9-connected-test-results.xml`；阶段 10 的 84 项归档为 `phase10-connected-test-results.xml`，JVM 21 个 XML 在 `phase10-unit-test-results/`；阶段 11 的 84 项与手工证据在 `phase11/`；阶段 12 的 134 项 JVM XML、86 项设备 XML、采集器输出和 lint 在 `phase12/`。不要只引用旧文档里的“44 tests OK”或“全部完成”，以当前 XML 和复跑结果为准。



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
- 官方槽（阶段 12 起）：`GlmCodingPlanRemoteDataSource`，`sourceId = glm-coding-plan-official`，`sourceType = OFFICIAL_WEB`；endpoint 来自 `BuildConfig.GLM_COLLECTOR_URL`（Debug 默认本机 `http://127.0.0.1:8787/v1/glm-coding-plan`，Release 通过 `-PglmCollectorUrl=...` 注入 CI/Pages HTTPS JSON），并校验 schemaVersion、来源、抓取时间与 2 小时新鲜度。`UnconfiguredOfficialRemoteDataSource` 已删除。
- `SettingsAwareSyncService` 每次 `sync()` 读取当前 `dataSourceMode`，只调用对应 source 的 `OfferSyncService`；不要绕过它直接把 demo source 注入官方同步。
- `RemotePayloadNormalizer` 现在校验：
  - 官方源必须有 `sourceUrl`；
  - `fetchedAt > Instant.EPOCH`；
  - source/product/plan/offer ID 与模式双向匹配：DEMO 必须 `demo-` 前缀，官方不得使用该前缀；
  - offer 标题非空。
- `LocalRadarRepository.applySync` 在事务内检查同一 sourceId 的类型不可变，违反直接抛异常回滚；`OfferSyncService` 写库前也检查一次。
- 首页按当前模式过滤 `offer.source.type`，产品卡片来源仍取实际 `offer.source.type`；切换模式不会 UPDATE 历史 `SourceInfo` 或快照。
- 季付/年付必须先折合月均再比较；首页在多个 active offer 中选月均最低的一项显示。

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
- 当时未验证/未实现：API 26–36/其他 ROM、通知渠道关闭/省电 soak、真实官方源、FCM/服务端推送、release 签名/CI。真实官方源已在阶段 12 接入 GLM 并完成真机验收。

## 阶段 12：GLM Coding Plan 官方源（纵向切片）

- 目标：只接一个产品、一个公开稳定来源，先把“采集 → 标准化 → Room → 首页/详情 → 来源与抓取时间”整条链路跑通。
- 采集器：`tools/glm-collector/`
  - 官方套餐页 `https://www.bigmodel.cn/glm-coding` 是 SPA，价格需 JS 渲染；匿名 `/api/biz/product/overview` 返回 1001 需要 Authorization。
  - `collector.mjs` 用 Node 24 内置 WebSocket + Chrome headless CDP，点击“连续包月/包季/包年”三个 tab，读取 `.cp-card`，输出与 `RemoteSyncPayload` 同构的 JSON；`--once` 或 `--serve`（127.0.0.1:8787，5 分钟缓存，失败 503 不回退旧价）。
  - `payload.mjs` 做金额解析和契约校验；4 个 payload 测试 + 6 个 payload/CLI 校验测试通过。
  - 详情与启动方式见 `tools/glm-collector/README.md`；CI 发布见 `docs/CI_GLM_COLLECTOR.md`。
- Android：
  - `BillingPeriod` 新增 `QUARTERLY(3)`；首页显示季度总价 + 折合月均，详情季/年都显示月均；图表对季/年使用月均。
  - `GlmCodingPlanRemoteDataSource` 用 `HttpURLConnection` + kotlinx-serialization JSON 读取 endpoint，校验 schemaVersion/source/URL/fetchedAt 和 2 小时新鲜度；错误消息区分 endpoint 未配置、采集器不可达、HTTP 非 2xx、过期、字段缺失。
  - `network_security_config.xml` 只允许 `127.0.0.1`、`localhost`、`10.0.2.2` 明文；Manifest 已引用。远程 endpoint 必须是 HTTPS。
  - `AppContainer` 官方槽使用 `BuildConfig.GLM_COLLECTOR_URL`；`officialAdapterConfigured = endpoint.isNotBlank()`；`UnconfiguredOfficialRemoteDataSource` 已删除。
  - 采集器失败或快照过期时官方模式保留已有官方缓存并显示失败原因；不会回退到旧价当实时价。演示数据仍完全隔离。
- 验证结果：
  - clean 构建 54s / 81 tasks；JVM 134/134（22 suites）；Lint 0 errors / 2 warnings；采集器 payload 4/4。
  - `connectedDebugAndroidTest` **86 / 86**（00:13，1m 5s）；首次运行因设备 Wi-Fi/移动数据关闭导致 7 个深链测试超时，打开网络后重跑全绿。
  - 官方模式真机：同步成功，首页 GLM Lite/Pro/Max 选年付最低月均；Lite 年付 ¥991.20/年、月均 ¥82.60；详情季付总价 ¥283.20、原价 ¥354.00、月均 ¥94.40。
  - 停止采集器后同步失败，缓存保留、失败原因展示、最后成功同步不变；切回演示后官方产品完全隐藏。
- 证据：`docs/evidence/2026-10-30/phase12/`（含 `phase12-connected-test-results.xml`、22 个 JVM XML、`device-manual/` 截图与 UI dump）；明细见 `docs/PHASE12_PROGRESS.md`。
- 已知限制：个人套餐公开页；团队套餐/登录优惠/优惠券不含；选择器改版会失败；月付 tab 无划线原价；`taxIncluded` 按页面含税处理。

### 阶段 12 增强：endpoint 配置化 + CI 定时采集

- `app/build.gradle.kts` 增加 `buildConfigField("String", "GLM_COLLECTOR_URL", ...)`：
  - Debug 未传 `-PglmCollectorUrl` 时默认 `http://127.0.0.1:8787/v1/glm-coding-plan`；
  - Release 未传时为空，官方模式明确显示“未配置 GLM 官方采集地址”，不请求错误地址；
  - 远程/CI 构建示例：`./gradlew assembleRelease -PglmCollectorUrl=https://<owner>.github.io/<repo>/glm-coding-plan.json`。
- `.github/workflows/glm-collector.yml`：每 30 分钟 + 手动触发；Node 24 + Chrome；先跑采集器/校验器测试，再真实采集，`validate-payload.mjs` 校验 3×3=9、来源、金额、新鲜度，最后发布到 GitHub Pages。
- `tools/glm-collector/validate.mjs` / `validate-payload.mjs`：严格校验；CI 失败时不覆盖上一版；App 端 2 小时新鲜度兜底。
- 远程仓库已创建并推送；GitHub Pages 已启用；`GLM Coding Plan collector` 已在 GitHub Actions 真实运行成功（run 41180172626，重跑成功），发布 `glm-coding-plan.json` 并通过 `validate-payload.mjs` 校验；本地可复现命令见 `docs/CI_GLM_COLLECTOR.md`。
- 复跑结果（2026-10-31 00:53）：clean 构建 56s / 84 tasks；JVM **137 / 137（23 suites）**；Lint 0 errors / 2 warnings；`connectedDebugAndroidTest` **86 / 86**（1m 8s）；采集器测试 **10 / 10**；Release 空 endpoint 与注入 endpoint 均构建成功并核对 BuildConfig。
- 新增测试：`GlmCodingPlanRemoteDataSourceTest` 的空 endpoint/过期 payload，`BuildConfigTest`；采集器 `validate.test.mjs` 5 个校验 + 1 个 CLI 失败关闭测试。

### 阶段 12 增强 P1：CI 自证端到端

- `tools/glm-collector/verify-published.mjs`：部署后重拉公网 JSON；HTTP 非 2xx、非法 JSON、schema/过期/未来时间失败、线上字节 SHA-256 与 CI 产物不一致都会重试（默认 12 次 × 5 秒），最终失败。
- `tools/glm-collector/test/verify-published.test.mjs`：5 个测试（哈希匹配、404 重试、过期、哈希不一致、CLI 本地 HTTP 服务）；采集器 Node 测试总数 **15 / 15**。
- `.github/workflows/glm-collector.yml`：
  - `collect-and-publish` 记录 payload SHA-256，Pages deploy 后由 `verify-published.mjs` 校验，成功后才算发布完成；
  - 新增 `build-android`：用上一步的线上 endpoint 跑 `testDebugUnitTest assembleRelease`，核对 BuildConfig，用一次性 CI key 签 helper APK（手动触发时上传 artifact）；
  - `notify-failure` / `close-recovered-issue`：失败开/更新 Issue `GLM collector CI failed`，恢复后关闭；同一 Issue 1 小时内不重复评论。
- `.github/workflows/glm-snapshot-health.yml`：每小时检查线上快照是否超过 2 小时；失败开/更新 `GLM snapshot is stale (>2h)`，恢复后关闭。
- 本地验证（2026-10-31 15:48）：Node **15 / 15**；线上 `verify-published` 通过；`testDebugUnitTest assembleRelease` 33s / 76 tasks；JVM 137 / 137；helper APK 签名脚本通过；两个 YAML 解析通过。
- 远端首跑（2026-10-31 16:02）：`collect-and-publish` 34s（部署后校验第 1 次通过，SHA-256 `3357c7cd…`）；`build-android` 3m 4s（`BUILD SUCCESSFUL in 2m 29s`，BuildConfig 含线上 URL，helper APK `8783870e…`）；`close-recovered-issue` 成功；`notify-failure` 健康时跳过；`glm-snapshot-health` 7s 成功。
- **失败建 Issue 路径已注入演练（P2）**：`max_age_seconds=1` 创建 Issue #1，重复失败去重并置红，默认 7200 恢复关闭；真实 stale 事故仍未发生。
- 限制：`build-android` 的 APK 是 CI 临时 key 签名的 helper，不是正式发布包；正式签名由 `release.yml` + `ai-offer-radar-release` artifact 产出。Issue 告警依赖 `issues: write` 与仓库 Issue 功能；工作流不会发邮件/Slack。

### 阶段 12 增强 P2：失败告警演练 + 正式签名 Release

- `.github/workflows/glm-snapshot-health.yml` 新增 `workflow_dispatch` 输入
  `max_age_seconds`，默认 `7200`；手动传 `1` 可在不等待两小时的情况下触发 stale
  路径。`MAX_AGE_SECONDS` 会进入校验命令和 Issue body；失败仍用固定标题
  `GLM snapshot is stale (>2h)` 去重，恢复后评论并关闭。
- `.github/workflows/release.yml`：手动触发，输入 `version_name`、`version_code`、
  `endpoint_url`、`sign_release`。`sign_release=true` 时解码
  `RELEASE_KEYSTORE_BASE64` 到 runner 临时目录，Set up JDK 17/Gradle，执行
  `testDebugUnitTest assembleRelease`，用 `apksigner verify` 校验，输出 SHA-256
  并上传 artifact；缺任一 secret 时在构建前失败。
- `app/build.gradle.kts`：从 `RELEASE_KEYSTORE_PATH`/`RELEASE_KEYSTORE_PASSWORD`/
  `RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` 读取签名信息；四项齐全才创建
  `signingConfigs.release` 并绑定 release buildType，否则保持 unsigned。支持
  `-PreleaseVersionCode` / `-PreleaseVersionName`。
- `docs/RELEASE_SIGNING.md`：keystore 生成、四个 GitHub Secrets、触发命令、本地
  签名、apksigner/安装、版本纪律和密钥丢失/泄露处理。
- 本地验证：临时 key 签名 `assembleRelease` 成功，`apksigner` 显示 v2 签名；
  无环境变量时 clean release 仍为 `app-release-unsigned.apk`（apksigner
  `DOES NOT VERIFY`）；临时 key 已删除，未入库。
- 远端失败演练（2026-10-31 23:23–23:25）：
  - `max_age_seconds=1` 首次失败创建 [Issue #1](https://github.com/yuntianming1874-design/ai-offer-radar/issues/1)（run 41214658712；该 run 在“失败置红”修复前，run 仍绿，但 step outcome 为 failure）；
  - 再次 `max_age_seconds=1`：复用 Issue，0 条新评论，updatedAt 不变；最终 step 使 run 失败（run 41214743795）；
  - 默认 7200：成功，Issue 收到恢复评论并关闭（run 41214800226）。
- 远端 Release（2026-10-31 23:26–23:29）：
  - `sign_release=false`，version 1.0.1-ci / code 2：成功 2m22s；artifact `ai-offer-radar-release`；APK SHA-256 `cf32651adbfc989c840c489c00b0f48c18bee3e2a2049e254cf07f74faf39307`，apksigner `DOES NOT VERIFY`（符合 unsigned）；
  - `sign_release=true` 且未配置 Secrets：22s 失败，逐项报四个 `RELEASE_*` Secret 未配置（run 41215119074），不会静默产出 unsigned 包。
- 正式签名远端（2026-11-01 00:43–00:46）：四个 Secrets 配置后 `sign_release=true` 成功（run 41221071911，1m59s；Gradle 1m20s / 77 tasks）；artifact `ai-offer-radar-1.0.0-signed.apk`，8,585,258 bytes，SHA-256 `b08b6208691f567cfec88ff4a555dd57319c13676509816fb94e88ab420aaf27`；v2 证书 SHA-256 `54f6ee32…`；下载后 `apksigner verify` 通过。
- **剩余**：CI artifact 未再次真机安装（设备断开）；本机同证书签名包已在 PMX110 安装并完成官方同步。keystore 只在本机 `.release/` 与 Keychain，不入库；备份/丢失处理见 `RELEASE_SIGNING.md`。


## 代码地图

```text
app/src/main/java/com/aioffer/radar/
├── AppContainer.kt                手动 DI；修改数据源/时钟/通知时先看这里
├── MainActivity.kt                深链入口；注意上面 setIntent 的坑
├── AiOfferRadarApplication.kt     创建容器（周期任务由 AppContainer 依偏好调度）
├── core/
│   ├── database/                  Room schema v2、DAO、MIGRATION_1_2
│   ├── datastore/                 DataStoreDemoStateStore、AppSettingsStore/DataStoreAppSettingsStore
│   ├── network/                   RemoteDataSource、RemoteDtos、DemoRemoteDataSource、GlmCodingPlanRemoteDataSource
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

tools/glm-collector/               GLM 官方套餐页本机/CI 渲染采集器（Node + Chrome CDP）+ payload/发布校验
.github/workflows/glm-collector.yml 定时采集、校验、发布并自证线上字节
.github/workflows/glm-snapshot-health.yml 每小时检查线上快照 2 小时新鲜度
.github/workflows/release.yml         手动签名 Release APK（密钥来自 Secrets）
app/src/androidTest/assets/        真实采集 JSON fixture
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
11. **计费周期**：季付/年付必须先折合月均再比较；月付、季付、年付不能直接比总价。图表对季/年使用月均。
12. **官方真实性**：GLM 官方源必须保存 sourceUrl、fetchedAt、原文摘要和 officialUrl；采集失败不得回退旧价并标记为实时。来源类型不可变，演示快照永远不能重标成官方历史。

## 构建与测试

正常环境：

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
node --test tools/glm-collector/test/payload.test.mjs
```

真机官方模式验收：

```bash
node tools/glm-collector/collector.mjs --serve
adb reverse tcp:8787 tcp:8787
# App：设置 → 官方数据 → 首页同步
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
- 阶段 12 进度：[PHASE12_PROGRESS.md](PHASE12_PROGRESS.md)
- GLM 采集器说明：[../tools/glm-collector/README.md](../tools/glm-collector/README.md)
- 阶段 11 验收清单：[PHASE11_DEVICE_ACCEPTANCE.md](PHASE11_DEVICE_ACCEPTANCE.md)
- 阶段 11 进度：[PHASE11_PROGRESS.md](PHASE11_PROGRESS.md)
- 阶段 10 验收：[PHASE10_PROGRESS.md](PHASE10_PROGRESS.md)
- 阶段 9 验收：[PHASE9_PROGRESS.md](PHASE9_PROGRESS.md)
- 官方数据源接入契约：[DATA_SOURCE_INTEGRATION.md](DATA_SOURCE_INTEGRATION.md)
- 阶段 8 验收：[PHASE8_PROGRESS.md](PHASE8_PROGRESS.md)
- 阶段 6 验收：[PHASE6_PROGRESS.md](PHASE6_PROGRESS.md)
- 最新证据：[evidence/2026-10-31/README.md](evidence/2026-10-31/README.md)；阶段 9–12 历史证据：[evidence/2026-10-30/README.md](evidence/2026-10-30/README.md)
- Room schema：`app/schemas/`
- 测试数据与 fake：`app/src/androidTest/java/com/aioffer/radar/core/testing/`、`app/src/test/...`
- 依赖版本集中在 `gradle/libs.versions.toml`。

## 已知限制

1. **官方源依赖本机/CI 采集层**：Debug 的 GLM Coding Plan 走 `tools/glm-collector` + headless Chrome + `adb reverse`；Release/CI 路径已实现为 HTTPS Pages 快照（`https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json`），但仍是公开页面抓取的静态快照，不是生产数据服务。页面选择器改版会采集失败；不覆盖团队套餐、登录优惠、优惠券。
2. **设备矩阵有限**：阶段 12 在 OPD2515 / API 37 上通过 86 / 86，阶段 11 历史基线 84 / 84；API 26–36、其他 ROM、通知渠道关闭、厂商省电策略和长期 soak 仍属未验证。
3. **没有后端/账号/支付**；“去订阅”只是打开官网入口，不完成购买。
4. **FCM 未接入**：`NoOpPushNotificationService` 只是边界。
5. **WorkManager 非实时**：15 分钟周期 + 系统调度；设备休眠、省电策略会延迟。演示时用首页“同步”按钮。
6. **通知权限/渠道边界**：拒绝或渠道关闭时事件保留 PENDING；恢复后补发；没有长期 soak。
7. **深链罕见路径**：已有 saved state 且系统用全新 intent 重建 Activity 时，启动 intent 会被跳过；常规冷启动/热启动/旋转已覆盖。
8. **历史最低近似**：同组多优惠同价时用“同金额最新快照”近似当前观测；演示数据不覆盖该组合。
9. **发布能力仍未完整闭环**：正式签名 Release 已由 `release.yml` 产出并验签（run 41221071911），但 CI artifact 未再次真机安装；没有 Play 配置、混淆策略，`release` 未开启 minify。`build-android` 仍用一次性 CI key 签 helper APK，不是正式发布包。失败告警注入路径已实测，真实 stale 事故与 schedule 长期稳定性未观察。
10. **Lint 仍有 2 个 warning**：`POST_NOTIFICATIONS` 常量 API 标记、未使用颜色 `radar_blue`。
11. **版本管理**：已初始化 git 仓库（`main`），阶段 11 验收基线为标签 `phase11-accepted`，阶段 12 已完成并提交到本地 `main`（标签 `phase12-glm-source`）；远程仓库为 `https://github.com/yuntianming1874-design/ai-offer-radar`（public，已脱敏），`main` 与 `phase11-accepted`、`phase12-glm-source`、`phase12-endpoint-ci` 三个标签均已推送。阶段 12 增强（endpoint + CI）已提交到 `main`；后续请新建 commit/分支，不要覆盖既有基线标签。
12. **文档历史**：`PHASE6_PROGRESS.md` 和旧版 `PHASE8_PROGRESS.md` 曾出现不可复核的完成声明；以根 README、阶段 8–12 文档和 evidence XML 为准。阶段 11 手工项与“仅自动化覆盖/未验证”项必须分开，不得混写成全部通过；阶段 12 的 86 项设备测试已完成并归档，引用时以 `phase12-connected-test-results.xml` 为准；阶段 12 增强引用 `evidence/2026-10-31/README.md`。

## 建议的下一步

1. **完成发布签名闭环并做 API 矩阵**
   - 正式签名已由 run 41221071911 验签；后续发布沿用同一 key，递增 versionCode，并在真机安装 CI artifact 复核。
   - keystore 只在本机 `.release/` 与 Keychain，备份/丢失处理见 `RELEASE_SIGNING.md`；不要用 CI helper APK 冒充正式发布包。
   - 观察几次定时 `schedule` 是否按 30 分钟运行；GitHub 可能延迟，客户端 2 小时阈值会兜底。

2. **扩大设备矩阵与长期 soak**
   - 在 API 26–36、其他 ROM、通知渠道关闭、厂商省电策略和长期运行下复跑 `docs/PHASE11_DEVICE_ACCEPTANCE.md` 的 1–8，并复跑阶段 12 官方模式。
   - 归档截图、UI dump、`dumpsys notification`、构建/测试日志和失败原因；未执行项保持“未验证”。

3. **把采集器替换为生产数据源**
   - 优先官方开放 API/授权接口；保持 `GlmCodingPlanRemoteDataSource` 输出契约不变，只替换 endpoint/采集层。
   - 若继续用渲染采集，需要监控、告警、选择器回归和部署位置（不能依赖开发者本机）。

4. **发布前**
   - release 签名、CI、lint 清零、WorkManager soak、真实设备长期运行。
   - 复核通知 `SENT` 语义和渠道关闭行为。
   - 补深链“saved state + 新 intent”边界，或明确接受。

5. **阶段 12 验收已完成**
   - 86 / 86 设备测试（`phase12-connected-test-results.xml`）与官方模式真机验收已归档。
   - 后续改动请新建 commit/分支，不要覆盖 `phase11-accepted` 基线标签。

## 接手检查清单

- [x] `./gradlew clean assembleDebug testDebugUnitTest lintDebug` 通过（2026-10-31：137 JVM / 23 suites，0 failed）
- [x] `assembleDebugAndroidTest` 编译通过；阶段 12 `connectedDebugAndroidTest` 已执行 86 / 86
- [x] `connectedDebugAndroidTest` 通过（2026-10-31：86 / 86，OPD2515 / API 37，1m 8s）
- [x] `node --test tools/glm-collector/test/*.test.mjs` 通过（15 / 15）
- [x] P1 本地：线上 `verify-published` 通过；Android Release job 等价命令通过；两个 workflow YAML 解析
- [x] P1 远端：`collect-and-publish` / `build-android` / `glm-snapshot-health` 真实通过（2026-10-31 16:02）
- [x] P1 失败 Issue 注入演练（health `max_age_seconds=1`；Issue #1 创建/去重/恢复关闭，失败 run 置红）
- [x] P2 本地：临时 key 签名通过 apksigner；无 key 时 release 保持 unsigned；两个 workflow YAML 解析
- [x] P2 远端：`release.yml` unsigned 运行成功；缺 Secrets 时失败关闭
- [x] P2 正式签名：四个 Secrets 配置后构建并下载验签（run 41221071911）
- [ ] P2 正式签名：CI artifact 真机安装复核
- [x] Release 空 endpoint 构建通过，BuildConfig 为空；注入 `-PglmCollectorUrl` 后 BuildConfig 正确
- [x] GitHub Pages/定时 workflow 真实执行（2026-10-31 15:27，run 41180172626 成功；快照 3 products / 9 offers）
- [x] 首装首页有“演示数据，非实时优惠”，状态卡显示“数据来源：演示数据”
- [x] 设置页切到“官方数据”显示官方已配置说明与采集器提示；采集器未运行时同步失败并显示原因
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
- [x] 官方模式真机：采集器 → `adb reverse` → 同步 → 9 offers/季付月均 → 切回演示不混数据
- [x] 不要重新引入 `onNewIntent` 里的 `setIntent()`
- [x] 不要把快照 ID 里的 `observedAt` 删掉
- [x] 不要绕过 `SettingsAwareSyncService` 直接把 demo source 注入官方同步
- [x] 阶段 11 手工 1–8 已完成并归档（`docs/evidence/2026-10-30/phase11/device-manual/`）
- [x] 核对 `docs/DELIVERY.md` 的功能/测试/未验证对照表与实际执行结果一致
- [ ] 在 API 26–36、其他 ROM、通知渠道关闭与省电 soak 下复跑
- [ ] 不要在没有证据时更新验收文档