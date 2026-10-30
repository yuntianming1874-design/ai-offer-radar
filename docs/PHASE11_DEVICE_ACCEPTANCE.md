# 阶段 11 设备手工验收清单

更新时间：2026-10-30 22:35  
状态：**已执行**（步骤 1–8 手工完成；步骤 9 干净构建/单测/Lint 完成，设备自动化见下；步骤 10 文档与证据归档完成）。

设备：OPD2515 / Android 17 / API 37 / build `OPD2515_17.0.0.103(CN01)` / serial `<device-serial>`。  
证据目录：`docs/evidence/2026-10-30/phase11/device-manual/`。

## 0. 准备与 ColorOS 限制

设备连接：

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
export ANDROID_SERIAL=<device-serial>
adb devices -l
```

本次连接曾遇到默认 5037 端口 adb server 状态异常：`adb devices` 为空，但 `system_profiler` 能看到 OPPO Pad Mini。改用 5038 端口能看到设备；随后执行：

```bash
adb -P 5037 kill-server
adb -P 5038 kill-server
adb start-server
```

设备恢复为 `device`。后续统一使用默认 5037。

安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**ColorOS 限制（实测）**：

```text
adb shell pm clear --user 0 com.aioffer.radar
SecurityException: ... does not have permission android.permission.CLEAR_APP_USER_DATA

adb shell pm revoke com.aioffer.radar android.permission.POST_NOTIFICATIONS
SecurityException: ... does not have permission android.permission.REVOKE_RUNTIME_PERMISSIONS
```

本机 shell 无法用 adb 清除应用数据或撤销通知权限。本次通过“包此前未安装 + 全新安装”满足首装语义；需要再次清数据时使用卸载重装：

```bash
adb uninstall com.aioffer.radar
adb install app/build/outputs/apk/debug/app-debug.apk
```

通知权限通过 App 内“设置 → 开启通知”授予，未使用 `pm grant`。

安装过程中可能出现：

- `com.oplus.appdetail/...InstallGuideActivity`：点“继续安装/完成”；
- `com.oplus.securitypermission/...AppStartConfirmDialogActivity`：点“30天内允许”。

不要禁用 `com.oplus.appdetail`：禁用后会落到系统安装器，无 launcher icon 的 test APK 无法继续。

## 1. 清数据首装与演示标识

- [x] 全新安装后首次启动不崩溃，自动完成一次演示同步（最后成功同步 22:19 北京时间）。
- [x] 首页标题“AI 优惠雷达”。
- [x] 固定横幅“演示数据，非实时优惠”。
- [x] 状态卡显示“数据来源：演示数据”“最后成功同步：2026-10-30 22:19 北京时间”。
- [x] 展示 KIMI / DEEPSEEK / GLM 三张演示卡。
- [x] 页面不存在“实时优惠”“官方实时数据”等表述。
- [x] 通知未授权时出现通知提示横幅；点“稍后”后 force-stop 重启不再出现（`PROMPTED` 持久化）。

证据：`01-first-launch.png`、`01-first-launch.xml`、`01-dismissed-notification.png`、`01-restart-after-prompt.png`、`01-restart-after-prompt.xml`。

## 2. 订阅与持久化

1. 点 KIMI 卡片“订阅”，按钮变为“取消订阅”。
2. `adb shell am force-stop com.aioffer.radar`，重新启动。
3. 确认 KIMI 仍显示“取消订阅”。
4. 点顶部“全部订阅”，确认三张卡都显示“取消订阅”。
5. 取消 DEEPSEEK、GLM，只保留 KIMI，确认只影响对应产品。
6. 再次重启，确认 KIMI 仍订阅。

- [x] 单个订阅立即生效。
- [x] 杀进程重启后订阅保持。
- [x] 全部订阅生效。
- [x] 取消订阅只影响该产品。
- [x] 重启后保持。

过程补充：首次点击时 helper 默认命中了 DEEPSEEK 的“订阅”，随后改为订阅 KIMI 并取消 DEEPSEEK；这属于操作脚本误点，不影响结论。证据：

- `02-two-subscriptions.png`、`02-two-subscriptions.xml`
- `02-restart-persisted.png`
- `02-subscribe-all.png`
- `02-unsubscribe.png`、`02-unsubscribe.xml`
- `02-kimi-only.png`

## 3. 六个演示场景

确保数据源为“演示数据”。首页“切换演示场景”下拉逐项执行；每次选择后等待同步结束（不再显示“同步中”）再记录。

实际执行顺序：首次同步 → 新增优惠 → 价格上涨 → 价格下降 → 重复同步 → 网络失败。  
**重要更正**：新增优惠之后，KIMI 存在 ¥70 的新优惠，首页卡片始终显示最低价 ¥70；价格上涨/下降改变的是 welcome offer，必须进详情核对。原清单把“首页当前价”写成 ¥110/¥80，与累计场景行为不符。

| 场景 | 操作 | 实际结果 | 证据 |
|---|---|---|---|
| 首次同步 | 选“首次同步” | KIMI ¥100/月、DEEPSEEK ¥200、GLM ¥300；无历史最低徽标 | `03-initial.png`、`03-initial.xml` |
| 新增优惠 | 选“新增优惠” | KIMI 出现 ¥70/月新优惠；卡片显示 ¥70 + “历史最低” | `03-new-offer.png/.xml`、`03-new-offer-top.png` |
| 价格上涨 | 选“价格上涨” | 详情中 welcome offer ¥100 → ¥110；新优惠 ¥70 仍在，首页仍显示 ¥70 | `03-price-up-top.png`、`03-price-up-detail.png/.xml` |
| 价格下降 | 选“价格下降” | 详情中 welcome offer ¥110 → ¥80；新优惠 ¥70 仍在，首页仍显示 ¥70 | `03-price-down-top.png`、`03-price-down-detail.png/.xml` |
| 重复同步 | 选“重复同步” | 最后成功同步更新到 22:24；价格历史仍 4 个点（22:19–22:23，最高 110、最低 70），无新增 | `03-repeat.png`、`03-repeat-detail.png/.xml` |
| 网络失败 | 选“网络失败” | 最后成功同步保持 22:24；状态卡“失败原因：Deterministic demo network failure”；横幅“同步失败，正在展示缓存数据，数据可能已过期”；缓存产品仍在 | `03-network-failure.png/.xml`、`03-network-failure-top.png/.xml` |

- [x] 首次同步恢复基线。
- [x] 新增优惠出现 ¥70。
- [x] 价格上涨后详情 welcome 为 ¥110。
- [x] 价格下降后详情 welcome 为 ¥80。
- [x] 重复同步不新增快照。
- [x] 网络失败保留缓存并显示失败原因。
- [x] 网络失败后选“首次同步”能恢复。

说明：若单独从基线执行涨价/降价，首页会显示 ¥110/¥80；累计执行时新优惠 ¥70 更便宜，首页显示最低价是正确行为。

## 4. 通知只一次、只给已订阅

前置：

1. 仅订阅 KIMI，取消 DEEPSEEK/GLM。
2. 通知权限已开启（本机通过 App 内“设置 → 开启通知”；`pm grant` 未使用）。
3. 通知渠道 `price_changes` 未被关闭。

步骤：

1. 选“价格下降”，等待同步完成。
2. 下拉通知栏：只有一条本应用通知，标题 KIMI，正文“价格下降，点击查看详情”。
3. 选“重复同步”再同步；本应用通知记录为 `NONE`，没有第二条。
4. DEEPSEEK/GLM 未订阅，本次没有它们的通知。

原始记录：

```text
0|com.aioffer.radar|1001|PRICE_DECREASE:demo-local:demo-kimi-welcome:demo-kimi:demo-kimi-standard:CN:CNY:MONTHLY:true:1780272000000:10000:8000|10416
android.title=String (KIMI)
android.text=String (价格下降，点击查看详情)
```

- [x] 已订阅 KIMI 收到一条降价通知。
- [x] 未订阅的 DEEPSEEK/GLM 没有通知。
- [x] 重复同步不重复通知。
- [x] 通知文案正确。

证据：`04-notification-shade.png`、`04-notification-shade.xml`、`04-notification-record.txt`、`04-notifications-after-repeat.txt`、`04-notification-list-after-repeat.txt`、`04-notification-permission.png`。

## 5. 点击通知进入详情

1. 在通知栏点击 KIMI 降价通知。
2. 进入 KIMI 详情，套餐 Demo Standard、月付、CNY、现价 ¥80、价格历史。
3. 返回一次回到首页。

- [x] 点击通知进入 KIMI 详情。
- [x] 详情内容与通知产品一致。
- [x] 返回一次回首页，不出现空白/重复首页。
- [x] 通知深链由 `PendingIntent` 携带产品 ID，不依赖 App 已在前台。

证据：`05-notification-deeplink.png`、`05-notification-deeplink.xml`。

## 6. 详情页图表、历史最低、币种、周期、时间

在 KIMI 详情核对：

- [x] 折线图出现。
- [x] 图例/摘要包含历史点数、最高价、最低价、起止时间：6 个点，最高 CNY 110.00，最低 CNY 70.00，22:19–22:28 北京时间。
- [x] 历史最低徽标与历史数据一致：过期新优惠 ¥70 在“历史优惠”中带“历史最低”；当前 welcome ¥80 不是最低，不显示徽标。
- [x] 币种为 CNY，展示为 ¥。
- [x] 计费周期为月付。
- [x] 当前优惠显示生效时间与“长期有效”。
- [ ] “切换其他套餐后图表随选择更新”：未单独手工执行；由 `DetailViewModelTest` / `DetailScreenTest` 覆盖。
- [ ] “少于两个历史点显示证据不足”：未手工构造；由 `PriceChartModelTest` 覆盖。

证据：`06-detail-chart.png`、`06-detail-chart.xml`；历史最低徽标另见 `03-new-offer.png`、`03-price-down-detail.png`。

## 7. 去订阅 URL 与统计增量

1. 打开“点击统计”，初始为“暂无官网跳转记录”。
2. 进入 KIMI 详情，点“去订阅”。
3. ColorOS 弹“AI 优惠雷达想要打开 Via”，点“30天内允许”。
4. 实际打开 Custom Tab：`Intent dat=https://kimi.moonshot.cn/ cmp=mark.via/.CustomTab`。
5. 返回 App，再打开“点击统计”，KIMI 显示“跳转次数：1”。
6. force-stop 重启后统计仍为 1。

- [x] URL 为 `https://kimi.moonshot.cn/`。
- [x] 实际使用 Custom Tab（Via 浏览器）。
- [x] 返回后统计 +1。
- [x] 重启后统计保留。
- [ ] “清除记录”手工操作未执行；由 `StatisticsScreenTest` / `StatisticsViewModelTest` 覆盖确认与清空。

证据：`07-statistics-before.png/.xml`、`07-official-url.png`、`07-statistics-after.png/.xml`、`07-statistics-persisted.png`。

## 8. 异常状态

### 8.1 无网络但有缓存

```bash
adb shell cmd connectivity airplane-mode enable
```

实测：

- [x] 继续展示缓存产品。
- [x] 显示“当前无网络，正在展示缓存数据，数据可能已过期”。
- [x] 状态卡“当前离线，展示的是缓存数据，可能已过期”。
- [x] 不崩溃，不出现“实时”描述。
- [x] force-stop 重启后仍展示缓存，最后成功同步时间保持 22:28。

恢复：

```bash
adb shell cmd connectivity airplane-mode disable
```

恢复后手动“同步”，最后成功同步更新到 22:31。

证据：`08-offline-cached.png/.xml`、`08-offline-restart.png/.xml`、`08-back-online.png`。

### 8.2 空数据 / 官方未配置

设置 → 数据源 → 官方数据：

- [x] 状态卡“数据来源：官方数据（适配器未配置）”。
- [x] 空态“暂无官方数据”+“官方数据源适配器尚未配置，当前不会显示任何官方实时优惠…”。
- [x] 出现“切回演示数据”和“重新同步”。
- [x] 没有任何演示产品被显示为官方数据。
- [x] 点“切回演示数据”后三张演示卡恢复，来源仍为“演示数据”。

证据：`08-official-empty.png/.xml`、`08-switch-back-demo.png/.xml`。

### 8.3 来源失败

选“网络失败”并同步：

- [x] 已有产品继续显示。
- [x] 状态卡“失败原因：Deterministic demo network failure”。
- [x] 横幅“同步失败，正在展示缓存数据，数据可能已过期”。
- [x] 再次选择“首次同步”可恢复。

证据见第 3 节 `03-network-failure-*`。

### 8.4 Custom Tab / 非法 URL / 数据库错误

- [x] Custom Tab 实际可用：Via `mark.via/.CustomTab` 打开 `https://kimi.moonshot.cn/`。
- 非法 URL、无浏览器、数据库读取失败无法在正常安装中自然制造，使用自动化证据：
  - `OfficialOfferLauncherTest.unavailableCustomTab_fallsBackToBrowserOnce`
  - `OfficialOfferLauncherTest.noAvailableBrowser_returnsErrorWithoutThrowing`
  - `DetailViewModelTest` 非法 URL/打开失败
  - `HomeScreenTest.databaseError_showsRecoverableRetryEntry`
  - `HomeScreenTest.databaseErrorWithCachedContent_keepsRetryEntryVisible`
- [ ] 未在本机禁用浏览器做“无浏览器”手工验证（避免破坏系统环境）。

## 9. 干净构建与测试

```bash
export GRADLE_USER_HOME="$PWD/.gradle-home"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
./gradlew --offline --no-daemon clean assembleDebug testDebugUnitTest lintDebug
./gradlew --offline --no-daemon connectedDebugAndroidTest
```

- [x] clean 构建成功：BUILD SUCCESSFUL 50s，55 tasks。
- [x] JVM 127/127（21 suites，0 failed / 0 errors / 0 skipped）。
- [x] Lint 0 errors / 2 warnings（既有 `POST_NOTIFICATIONS` API 标记、未使用 `radar_blue`）。
- [x] 设备自动化：84 / 84 通过，0 failed / 0 errors / 0 skipped，1m 6s（22:34；XML `phase11-connected-test-results.xml` 与 `device-manual/09-connected-results.xml`）。
- [x] ColorOS 安装向导处理：继续安装/完成，测试 Activity 弹窗点“30 天内允许”。

日志：`09-build.log`、`09-connected.log`。

## 10. 归档与更新

- [x] 截图与 UI dump 已保存到 `device-manual/`。
- [x] 通知原始记录已保存。
- [x] 本文结果表已更新。
- [x] `docs/PHASE11_PROGRESS.md`、`docs/DELIVERY.md`、`docs/evidence/2026-10-30/README.md`、`README.md`、`docs/HANDOFF.md` 已更新。
- [x] 失败与限制均保留原始证据，没有把未执行项写成通过。

## 11. 执行结果

| 步骤 | 结果 | 证据 | 备注 |
|---|---|---|---|
| 1 首装 | 通过 | `01-first-launch.*`、`01-restart-after-prompt.*` | ColorOS 禁 adb 清数据，使用全新安装 |
| 2 订阅 | 通过 | `02-*` | 单订、重启、全部订阅、取消 |
| 3 六场景 | 通过 | `03-*` | 累计场景下首页显示最低价 ¥70 |
| 4 通知 | 通过 | `04-*` | 一条通知；重复同步不重复 |
| 5 深链 | 通过 | `05-*` | 点击通知进 KIMI 详情 |
| 6 详情 | 通过（2 项自动化覆盖） | `06-*`、`03-price-down-detail.*` | 图表 6 点、CNY、月付、时间 |
| 7 官网/统计 | 通过（清除记录自动化覆盖） | `07-*` | Via Custom Tab；KIMI +1 |
| 8 异常 | 通过（非法 URL/无浏览器/DB 错误自动化覆盖） | `08-*`、`03-network-failure-*` | 离线缓存重启不崩溃 |
| 9 构建测试 | 构建/单测/Lint/设备自动化通过 | `09-build.log`、`09-connected.log`、`phase11-connected-test-results.xml` | 127 JVM；84 设备；Lint 0/2 |
| 10 交付说明 | 通过 | `docs/DELIVERY.md` | — |

## 12. 未验证项

- API 26–36、其他 ROM、通知渠道关闭、省电/长期 soak。
- 真实官方数据源、FCM/服务端推送、release 签名/CI/Play。
- 阶段 11 手工未单独执行的细项：切换其他套餐后的图表更新、少于两个历史点、清除点击记录、无浏览器（均有自动化覆盖，但不能写成手工通过）。
- 深链“已有 saved state + 全新 intent”罕见路径。
