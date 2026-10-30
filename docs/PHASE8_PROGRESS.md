# 阶段 8 开发进度

## 状态

阶段 8 代码完成，P0-3 设备验收已于 2026-10-29 完成，P1-1 过期优惠自动消失、P1-2 深链旋转/返回栈、P0-4 首次同步中间态崩溃、P1-4 历史最低空历史语义与 P1-5 固定展示时区于 2026-10-30 完成。此前“全部完成 / 44 tests OK / 已修复 launchSingleTop”的说法已作废，以本文和 `docs/evidence/` 为准。

## 验收结果

- 构建：`./gradlew clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`，`BUILD SUCCESSFUL`。
- 单元测试：97 / 97 通过，0 failed / 0 errors / 0 skipped。
- 仪器测试：58 / 58 通过，0 failed / 0 errors / 0 skipped，设备 OPD2515 / Android 17 / API 37。
- Lint：0 errors，2 warnings（`InlinedApi` 通知权限常量、未使用颜色 `radar_blue`）。
- 仪器测试 XML：`docs/evidence/2026-10-30/connected-test-results.xml`。
- 单元测试 XML：`docs/evidence/2026-10-30/unit-test-results/`。
- P1-1 / P1-2 / P0-4 / P1-4 / P1-5 证据：`docs/evidence/2026-10-30/`（含 58 项仪器 XML、7 项深链直接运行日志与跨时区截图）。
- 构建与测试日志：`docs/evidence/2026-10-30/build.log`、`connected-tests.log`、`first-sync-crash-before-fix.txt`。

## P0-3 覆盖项

- 冷启动深链：`NotificationDeepLinkTest.coldStart_opensRequestedProductAndBackReturnsHome`。
- 已有 Activity、同一产品热启动：`existingActivity_sameProduct_reopensDetailAndBackReturnsHome`。
- 已有 Activity、不同产品热启动：`existingActivity_differentProduct_showsNewProductAndBackReturnsHome`。
- 通知权限被拒绝时事件保持 PENDING，恢复后发送：`NotificationDispatcherTest.permissionDenied_keepsEventPendingUntilGranted`。
- 通知渠道 `IMPORTANCE_NONE` 被识别为不可投递：`AndroidNotificationSenderTest.channelWithImportanceNone_isRecognizedAsBlocked`。
- `MainActivity.onNewIntent` 不再调用 `setIntent`：应用不依赖 `getIntent()` 的二次读取，深链通过 Compose 状态消费；这样 ActivityScenario 能正确跟踪生命周期。

## P1-1 过期优惠自动消失

- `app/src/main/java/com/aioffer/radar/core/time/TimeTicker.kt`：每 30 秒发射一次当前时间，冷启动立即发射。
- `HomeViewModel` 以 ticker 驱动 `observeHomeProducts(now)`；`DetailViewModel` 将 ticker 与价格历史组合，重新划分 current/upcoming/expired。
- 首页无有效优惠时显示“暂无有效优惠”；详情页显示“当前筛选条件下暂无有效优惠”，优惠保留在“历史优惠”区域。
- 新增测试：`HomeViewModelTest.offerExpiresWhileScreenIsOpen_disappearsFromCurrentOffers`、`DetailViewModelTest.offerExpiresWhileDetailIsOpen_movesFromCurrentToExpired`、`TimeTickerTest.emitsImmediatelyAndThenAtTheConfiguredInterval`。
- 设备手工验证：离线把 KIMI 截止时间设为当前 +25 秒，首页从 `¥100.00/月` 变为“暂无有效优惠”，详情页进入历史区；恢复网络重启后演示数据恢复。详见 `docs/evidence/2026-10-30/`。

## P1-2 深链旋转与返回栈

- `MainActivity` 在 `savedInstanceState == null` 时才消费启动 intent；旋转/配置变更交给 NavController 的 saved state 恢复，不再重复打开旧详情。
- `onNewIntent` 始终消费新的通知深链；不调用 `setIntent`，因为 ActivityScenario 会用原始 launch intent 匹配 Activity，改写 intent 会让生命周期事件被忽略并导致测试挂起。
- `AiOfferRadarApp` 导航到详情时 `popUpTo(home)` 且不 `saveState`；详情之间不会叠加，返回一次即回首页。
- 新增 4 个旋转/返回栈用例；`NotificationDeepLinkTest` 共 7 项，直接 `am instrument` 结果为 7 / 7。
- 已知边界：若 Activity 带着 saved state 被系统以一条全新的 intent 重建，启动 intent 会被当作旧深链跳过；通知点击走 `onNewIntent` 或冷启动路径，不受影响。该边界未在设备上单独构造。

## P0-4 首次同步中间态崩溃

- 现象：全新安装后直接冷启动深链，`LocalRadarRepository.observeHomeProducts` 的 `combine` 先收到 offers、后收到 sources，`requireNotNull(sourcesById[offer.sourceId])` 抛 `IllegalArgumentException`，进程崩溃。
- 修复：`LocalRadarRepository.kt` 首页与详情页改用 `mapNotNull`，来源缺失的瞬时 offer 跳过，等待下一次 Room 发射补齐；不改变数据库事务与最终数据。
- 回归：`LocalRadarRepositoryTest.offerWithMissingSource_isSkippedInsteadOfCrashing`，同时覆盖首页与详情页。
- 设备手工：卸载重装后冷启动深链，正常进入 KIMI 详情；返回一次到首页。

## P1-4 历史最低空历史语义

- 问题：历史为空时 `all {}` 为真，首次同步会把所有付费优惠标成“🔥 历史最低”；当前快照自身也被当作历史证据。
- `HistoricalLowestEvaluator` 现在要求同一比较分组至少存在一条合格的历史快照；没有历史时返回 false，免费、试用、负价与非同组历史不计入。
- `LocalRadarRepository.priorHistory` 在评估前移除“当前观测”对应的快照：同组中金额等于候选价的最新快照。若同组还有其他优惠的快照，仍参与比较，避免新增更便宜优惠时把旧优惠误标为历史最低。
- 新增测试：
  - `HistoricalLowestEvaluatorTest.withoutHistory_isNotHistoricalLowest`
  - `HistoricalLowestEvaluatorTest.otherComparisonGroupsOnly_areNotHistoricalLowest`
  - `HistoricalLowestEvaluatorTest.onlyFreeAndNegativeHistory_isNotPaidHistoricalLowest`
  - `HistoricalLowestEvaluatorTest.onlyYearlyHistory_isNotMonthlyHistoricalLowest`
  - `OfferSyncServiceIntegrationTest.firstSync_hasNoHistoricalLowestBadge`
  - `OfferSyncServiceIntegrationTest.priceDecrease_marksOnlyItsComparisonGroupAsHistoricalLowest`
  - `OfferSyncServiceIntegrationTest.newOffer_marksOnlyTheCheaperOfferAsHistoricalLowest`
- 设备手工验证：全新安装首页无“历史最低”；切换“价格下降”后仅 KIMI 显示“历史最低”，KIMI 为 ¥80.00/月，DEEPSEEK/GLM 不显示。
- 已知边界：同组存在多个优惠时，以“同金额最新快照”近似当前观测；演示数据与现有产品均为每产品一个套餐优惠，未构造同组多优惠同价的极端组合。

## P1-5 固定展示时区

- 新增 `app/src/main/java/com/aioffer/radar/core/time/DisplayTime.kt`：固定 `Asia/Shanghai`，统一输出 `yyyy-MM-dd HH:mm 北京时间`。
- 删除 `HomeScreen` 与 `DetailScreen` 各自的 `ZoneId.systemDefault()` 格式化实现，首页更新时间、详情生效/截止/更新时间、图表文本摘要与无障碍描述全部走同一格式化器。
- 数据库仍保存 UTC `Instant`，只改变展示层；设备系统时区不再影响优惠时间。
- 新增单元测试：
  - `DisplayTimeTest.formatsInShanghaiRegardlessOfSystemDefault`（临时把 JVM 默认时区改为 `America/Los_Angeles`）
  - `DisplayTimeTest.crossingUtcDateBoundaryUsesShanghaiDate`
  - `DisplayTimeTest.formatsEpochAtFixedOffset`
- 新增 UI 断言：`HomeScreenTest.productCard_displaysDetailsAndHandlesSubscription` 校验首页显示 `更新时间：2026-06-01 08:00 北京时间`。
- 设备手工验证：把 OPD2515 系统时区切到 `America/Los_Angeles` 后，状态栏显示 10:16，首页仍显示 `更新时间：2026-06-01 08:00 北京时间`，详情显示 `生效：2026-01-01 08:00 北京时间`；截图存于 `docs/evidence/2026-10-30/timezone-home-los-angeles.png` 与 `timezone-detail-los-angeles.png`，随后设备时区已恢复 `Asia/Shanghai`。

## 实施约束

- 周期同步仅保证最终执行，不保证实时；设备休眠、电量和系统策略均可能延迟。
- 通知在同步事务提交后发送；`NotificationManager.notify()` 调用后记录 `SENT`，语义是“已提交给系统”，不是系统展示成功。
- 渠道 importance 为 `NONE` 时 `canNotify()` 返回 false，事件保持 PENDING；应用级通知关闭和 Android 13+ 权限拒绝同样阻止发送。
- 不添加 Firebase 依赖，FCM 仅保留可替换边界与空实现。
- 本轮在 ColorOS 上安装 APK 会弹系统“继续安装”确认；自动化验收需人工/脚本确认，这不是 App 代码问题。

## 未覆盖 / 后续

- 在系统设置中手工关闭通知渠道、手工撤销权限未逐项复现；由上述自动化用例覆盖判定逻辑。
- WorkManager 在真实后台/进程死亡后的周期执行未做长时间 soak 测试。
- 深链在“已有 saved state 且系统以全新 intent 重建 Activity”的罕见路径未覆盖；常规旋转、冷启动与热启动已覆盖。
- 首次同步中间态只在设备手工复现并修复；没有构造 Room 查询交错的确定性测试，回归测试直接注入孤儿 offer 验证容错。

## 文档索引

- 根说明与运行方式：`README.md`
- 开发者交接：`docs/HANDOFF.md`
- 验收证据：`docs/evidence/2026-10-30/README.md`
