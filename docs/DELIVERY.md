# AI 优惠雷达：交付说明

更新时间：2026-11-01 23:55
适用版本：本地演示版 + GLM Coding Plan 官方源（`com.aioffer.radar`，版本号见 `app/build.gradle.kts`）
交付结论：可构建、可安装、可测试；已接入**一条**真实 GLM 官方源，支持 Debug 本机采集器与 Release/CI HTTPS 快照；外部 heartbeat 为主触发、GitHub schedule 为兜底；但**不是生产版本**，没有后端、账号、支付或 FCM；远程 CI/Pages 已启用并成功发布快照。

> 重要：内置数据全部是确定性演示数据。演示模式下首页固定显示“演示数据，非实时优惠”。官方模式的数据来自 `https://www.bigmodel.cn/glm-coding` 的公开套餐页，经 `tools/glm-collector` 渲染采集；Debug 走本机服务，Release 通过 `-PglmCollectorUrl` 指向 CI 定时发布的 HTTPS JSON。采集失败、endpoint 未配置或快照超过 2 小时时同步会失败并显示原因，不会把旧价描述为实时价。

## 1. 本阶段交付状态

| 项目 | 状态 |
|---|---|
| 干净构建 `clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` | 通过：`BUILD SUCCESSFUL`，84 tasks，56s（2026-10-31 00:53 增强复跑） |
| JVM 单元测试 | **137 / 137**，0 failed / 0 errors / 0 skipped（23 suites） |
| Lint | 0 errors，2 warnings（既有 `POST_NOTIFICATIONS` 标记、未使用 `radar_blue`） |
| 设备自动化测试 | **86 / 86**（2026-10-31 00:52，OPD2515 / API 37，1m 8s）；阶段 11 历史基线 84 / 84 |
| 采集器/发布校验/heartbeat 测试 | **23 / 23**（payload 4 + 校验/CLI 6 + 发布端点校验 5 + heartbeat 8） |
| P1 CI 自证（部署后哈希、Android job、Issue 告警） | **远端首跑通过**：[run 41182662449](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41182662449)；部署后校验、Android job、恢复关闭、health workflow 成功；P2 已补失败路径演练 |
| P2 失败告警 + Release workflow | 失败告警演练远端通过；unsigned 与正式签名 Release 均远端成功（签名 run 41221071911，v2 证书 `54f6ee32…`）；CI artifact 已真机安装；真实过期快照被守卫拦截并手动恢复；keystore 仅本机/Keychain |
| Release endpoint | 无 property 时为空（失败关闭）；`-PglmCollectorUrl=https://example.com/glm-coding-plan.json` 时构建成功并正确注入 |
| CI 定时采集 | **已在 GitHub Actions 真实运行成功**：run 41180172626；Pages 快照 3 products / 9 offers，JSON 校验通过 |
| 外部 heartbeat | 代码/单测/mock dispatch 通过；每 15 分钟检查、75 分钟阈值、`repository_dispatch` 触发；GitHub schedule 已降为兜底；launchd/远端 dispatch 待 token 激活 |
| 阶段 11 手工设备验收（步骤 1–8） | **已执行**：2026-10-30 22:19–22:32，OPD2515 / API 37；证据见 `docs/evidence/2026-10-30/phase11/device-manual/` |
| 阶段 12 GLM 官方源 | **已完成真机验收**：官方同步、3 产品 × 3 周期、季付总价/月均、失败缓存态、切回演示隔离；证据见 `docs/evidence/2026-10-30/phase12/device-manual/` |
| 交付说明 | 已完成（本文件） |

阶段 12 的 JVM 134/134、采集器 4/4、Lint 0 errors / 2 warnings 和 86 项设备测试均已通过；阶段 12 增强后的最终复跑为 137 JVM、Node 10/10、设备 86/86；P1 将 Node 测试增加到 15/15，P2.3 增加到 23/23，并补充部署后线上哈希校验、Android Release build job 与外部 heartbeat。官方模式真机采集/同步与失败/隔离场景已执行。阶段 11 的构建、手工验收与文档已完成；部分无法在正常安装中自然制造的异常（非法 URL、无浏览器、数据库读取失败、清除记录）由仪器/单元测试覆盖，不能写成手工通过；详见 [PHASE11_DEVICE_ACCEPTANCE.md](PHASE11_DEVICE_ACCEPTANCE.md) 第 12 节。

### 1.1 阶段 12 增量：GLM Coding Plan 官方源

- 官方源：`https://www.bigmodel.cn/glm-coding`，`DataSourceType.OFFICIAL_WEB`，sourceId `glm-coding-plan-official`。
- 采集器：`tools/glm-collector/`，Node 24 + 本机 Chrome headless CDP，读取 Lite / Pro / Max 的连续包月、包季、包年价格；输出 3 products、3 plans、9 offers。
- 价格语义：季/年 offer 的 `actualAmountMinor` 是这一期总价，`monthlyEquivalentMinor` 是页面折合月价；首页选月均最低的 offer 显示，详情同时显示总价和月均。
- Android：阶段 12 原实现通过 `http://127.0.0.1:8787/v1/glm-coding-plan` 拉取；当前 endpoint 已改为构建时注入（Debug 默认本机，Release 用 CI HTTPS），`network_security_config` 只允许本机/模拟器回环地址明文。
- 失败语义：采集器不可达、HTTP 非 2xx、schema 不匹配时同步失败，状态卡显示原因；官方缓存不会回退旧价冒充实时。演示数据完全隔离。
- 验证结果：JVM 134/134；采集器 payload 测试 4/4；Lint 0 errors / 2 warnings；设备 **86 / 86**。
- 官方模式真机：首页 GLM Lite/Pro/Max 选年付最低月均（Lite ¥991.20/年、月均 ¥82.60）；Lite 季付详情总价 ¥283.20、原价 ¥354.00、月均 ¥94.40；采集器停止后缓存保留并显示失败原因；切回演示后官方产品完全隐藏。
- 未验证：API 26–36、其他 ROM、通知渠道关闭/省电 soak。

### 1.2 阶段 12 增强：endpoint 配置化 + CI 定时采集

- endpoint：`BuildConfig.GLM_COLLECTOR_URL`；Debug 缺省本机 `http://127.0.0.1:8787/v1/glm-coding-plan`；Release 通过 `-PglmCollectorUrl=https://.../glm-coding-plan.json` 注入，未注入时官方模式明确“未配置”并失败关闭。
- 新鲜度：`GlmCodingPlanRemoteDataSource` 拒绝超过 2 小时的快照，也拒绝晚于当前时间 5 分钟的时间戳；endpoint 为空时不发起网络。
- CI：外部 heartbeat 每 15 分钟检查，超过 75 分钟通过 `repository_dispatch` 触发；`.github/workflows/glm-collector.yml` 保留 `schedule` 每 30 分钟兜底。Node 24 + Chrome 采集后 `validate-payload.mjs` 校验，发布到 GitHub Pages；校验失败不发布。
- P1 CI 自证：deploy 后 `verify-published.mjs` 重新拉取线上 JSON，要求 HTTP 200、schema/2h 新鲜度通过且字节 SHA-256 等于 CI 产物；`build-android` 用同一 endpoint 跑 JVM 测试与 Release 构建并签 helper APK；失败/2h 过期自动开 Issue，恢复后关闭。
- 本地验证：Node **23/23**；真实采集 3 档/9 offers 校验通过；JVM 137/137；Lint 0/2；设备 86/86；Release 两种 endpoint 构建均通过；线上 `verify-published` 通过；Android Release job 等价命令 33s / 76 tasks；三个 workflow 触发（repository_dispatch/schedule/workflow_dispatch）解析通过；heartbeat 线上 70m 判定 fresh、mock 90m 正确 dispatch。
- **远端首跑（2026-10-31 16:02）**：`collect-and-publish` 34s，部署后校验第 1 次通过（3 档 / 9 offers，SHA-256 `3357c7cd…`）；`build-android` 3m 4s（Gradle `BUILD SUCCESSFUL in 2m 29s`，BuildConfig 含线上 URL，helper APK `8783870e…`）；`close-recovered-issue` 成功；`glm-snapshot-health` 7s 成功。页面：<https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json>。
- P2 失败告警演练（远端，2026-10-31 23:23–23:25）：`max_age_seconds=1` 创建 [Issue #1](https://github.com/yuntianming1874-design/ai-offer-radar/issues/1)（run 41214658712）；重复失败去重且 run 置红（run 41214743795）；默认 7200 恢复并关闭 Issue（run 41214800226）。这是手动阈值演练，不是真实 stale 事故。
- P2 Release workflow（远端）：`sign_release=false` 构建成功（run 41214833322，2m22s，versionCode 2 / 1.0.1-ci，APK SHA-256 `cf32651a…`，unsigned）；`sign_release=true` 缺 Secrets 时 22s 失败关闭（run 41215119074），不会静默产出 unsigned 包。
- P2 正式签名（远端，2026-11-01 00:43–00:46）：四个 Secrets 配置后 `sign_release=true` 成功（[run 41221071911](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41221071911)，1m59s；Gradle 1m20s / 77 tasks）；`ai-offer-radar-1.0.0-signed.apk` SHA-256 `b08b6208…`，v2 证书 `54f6ee32…`，版本 1 / 1.0.0，endpoint 正确。
- P2 真机与真实 stale（2026-11-01 01:33–01:40）：CI artifact 安装成功，设备内 APK 哈希与 artifact 完全一致；01:36 快照超过 2h，同步显示“采集数据已过期…已超过 120 分钟”，缓存保留、无崩溃；手动触发 [run 41225390770](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41225390770) 刷新后 01:40 同步成功。
- 外部 heartbeat：`tools/glm-collector/heartbeat.mjs` 每 15 分钟检查 Pages 快照，超过 75 分钟时 `repository_dispatch`（event type `glm-heartbeat`）触发采集；有运行中的 run 时跳过；快照不可读时保守触发。`tools/glm-collector/install-heartbeat.macos.sh` 安装 launchd，token 从 Keychain 读取。GitHub `schedule` 只作为兜底。
- **未验证/未解决**：GitHub `schedule` 只观察到各一次自动运行，不能提供 30 分钟/小时级 SLA；外部 heartbeat 的远端 dispatch/launchd 尚未激活；本机 launchd 依赖 Mac 开机/联网；API 26–36/其他 ROM 未验证。
- 下一步：配置 token 并激活外部 heartbeat（launchd 或常在线 cron），再做 API 26–36 兼容矩阵；后续发布沿用同一 key、递增 versionCode。
- 详细步骤见 [CI_GLM_COLLECTOR.md](CI_GLM_COLLECTOR.md) 与 [RELEASE_SIGNING.md](RELEASE_SIGNING.md)。

## 2. 构建、运行和测试

### 2.1 环境

- JDK 17
- Android SDK Platform 35、Build-Tools 35
- Gradle Wrapper 8.9（仓库自带）
- 最低 Android 8.0（API 26），targetSdk 35
- 真机需开启 USB 调试；`adb devices -l` 能看到设备

`local.properties`（不提交）：

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

### 2.2 构建与安装

```bash
./gradlew assembleDebug
./gradlew installDebug
# 或
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aioffer.radar/.MainActivity
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

### 2.3 测试与静态检查

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

沙箱/受限环境（`~/.gradle`、`~/.android` 不可写）可用：

```bash
export GRADLE_USER_HOME="$PWD/.gradle-home"
export ANDROID_USER_HOME="$PWD/.android-home"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"
./gradlew --offline --no-daemon clean assembleDebug testDebugUnitTest lintDebug
./gradlew --offline --no-daemon connectedDebugAndroidTest
```

多设备：

```bash
ANDROID_SERIAL=<serial> ./gradlew connectedDebugAndroidTest
```

ColorOS/OPPO 安装注意：

- Gradle 安装 debug APK 时可能弹 `com.oplus.appdetail` 安装向导，需要点“继续安装/完成”，否则 `connectedDebugAndroidTest` 会卡在安装。
- 重装后测试 Activity 可能弹“30 天内允许”。
- **不要禁用 `com.oplus.appdetail`**：禁用后会落到系统安装器，对无 launcher icon 的 test APK 无法继续。

### 2.4 清除数据后首装检查

标准做法：

```bash
adb shell pm clear com.aioffer.radar
adb shell am start -n com.aioffer.radar/.MainActivity
```

**ColorOS 实测限制**：本机 shell 执行 `pm clear` 会抛 `SecurityException: ... does not have permission android.permission.CLEAR_APP_USER_DATA`，执行 `pm revoke ... POST_NOTIFICATIONS` 同样被拒绝。需要清数据时改用卸载重装，或系统设置中“清除数据”：

```bash
adb uninstall com.aioffer.radar
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aioffer.radar/.MainActivity
```

本次阶段 11 验收即通过“此前未安装 + 全新安装”完成首装；通知权限通过 App 内“设置 → 开启通知”授予。

预期：

- 首页标题“AI 优惠雷达”；
- 固定横幅“演示数据，非实时优惠”；
- 状态卡“数据来源：演示数据”“最后成功同步：…北京时间”；
- 首次启动会自动同步一次，展示 KIMI / DEEPSEEK / GLM 三张演示卡；
- 全程不出现“实时优惠”“官方实时数据”等表述。

首次启动若通知未授权，会显示通知提示横幅；点“稍后”会持久化 `PROMPTED`，之后不再反复弹出（系统权限仍可在设置中修改）。

## 3. 演示场景切换

首页“切换演示场景”只在 `DEMO` 模式显示。选择场景会设置下一场景并立即触发一次同步；如果当前正在同步，等本次结束后再试。也可以点顶部“同步”手动触发。

| 场景 | 预期行为 | 关键观察点 |
|---|---|---|
| 首次同步 | 恢复基线：KIMI ¥100、DEEPSEEK ¥200、GLM ¥300（每月） | 3 个产品；无历史最低徽标；首次成功只建基线 |
| 新增优惠 | 为 KIMI 增加 ¥70/月新优惠 | KIMI 出现更便宜的第二个有效优惠；已订阅时一条新优惠通知 |
| 价格下降 | KIMI 的 welcome offer 从当前价降到 ¥80 | 详情出现历史最低；已订阅时一条降价通知。若 ¥70 新优惠仍有效，首页卡片继续显示最低价 ¥70 |
| 价格上涨 | KIMI 的 welcome offer 从当前价涨到 ¥110 | 价格上涨通知；详情中 welcome 高于历史低点。若 ¥70 新优惠仍有效，首页卡片继续显示 ¥70 |
| 重复同步 | 不制造数据变化 | 不新增优惠/快照/通知 |
| 网络失败 | 抛出确定性网络异常，不改业务数据 | 保留缓存；状态卡显示失败原因；不清空 `lastSuccessfulSyncAt` |

注意：

- 场景会累积：先执行“新增优惠”后，KIMI 同时存在 welcome offer 和 ¥70 新优惠。随后涨价/降价只改 welcome offer，首页卡片显示的是最低价 ¥70；必须进详情才能看到 welcome 的 ¥110/¥80。若单独从基线执行，首页会显示 ¥110/¥80。
- “首次同步”会重置演示价格；如果此时已订阅 KIMI 且当前价不是 ¥100，可能被识别成一次价格变化并产生通知；这是预期行为，不是 bug。
- 场景和 KIMI 演示价格写入 DataStore，杀进程/重启后保持。
- 演示数据源 `sourceUrl` 为空；`officialUrl` 只是 Kimi/DeepSeek/GLM 的官网入口，不是价格来源。

## 4. 通知验证

前置：

1. Android 13+ 授权通知：
   ```bash
   adb shell pm grant com.aioffer.radar android.permission.POST_NOTIFICATIONS
   ```
   或在首页横幅点“开启通知”。ColorOS 本机 shell 可能拒绝 `pm grant/revoke`（`REVOKE_RUNTIME_PERMISSIONS` 权限不足），优先用 App 内“设置 → 开启通知”或系统设置。
2. 确认通知渠道 `price_changes` 未被关闭：
   ```bash
   adb shell dumpsys notification --noredact | grep -A8 'price_changes'
   ```
3. 在首页只订阅 KIMI（不要订阅 DEEPSEEK/GLM）。

步骤：

1. 选择“价格下降”并等待同步完成。
2. 下拉通知栏，应只有一条 KIMI 通知，文案“价格下降，点击查看详情”；DEEPSEEK/GLM 没有通知。
3. 选择“重复同步”，再次同步；不应出现第二条相同通知。
4. 点击通知，应进入 KIMI 详情页；返回一次回首页。
5. 取消订阅 KIMI，选择“价格下降”或“价格上涨”，再同步；不应再新增 KIMI 通知。

实现语义：

- 通知事件有稳定事件 ID，数据库唯一约束 + `wasDelivered` 去重；只对已订阅产品发送。
- 通知 `SENT` 表示已提交给系统，不表示用户已看到。
- 权限拒绝或渠道关闭时事件保持 `PENDING`，恢复后补发；不会为了“看起来成功”而标记已发送。
- 通知由本地同步触发（手动同步或 WorkManager），不是服务端推送。

## 5. 详情、图表与官网跳转

从首页“查看详情”进入 KIMI 详情，核对：

- 当前有效优惠：原价、现价、折扣、生效时间、截止时间（演示为“长期有效”）；
- 币种：`CNY`，展示为 `¥`；
- 计费周期：月付；年付数据会显示“折合月均”；
- 价格历史：至少两个历史点后出现折线图，摘要包含点数、最高、最低、起止时间；不足两个点时显示“证据不足，将从首次同步开始积累”；
- 历史最低徽标只在同一比较分组存在更早的合格历史快照且当前价不高于最低价时出现。

“去订阅”：

- 打开 `https://kimi.moonshot.cn/`（DeepSeek/GLM 同理）；
- 优先 Custom Tab，无 Custom Tab 时回退系统浏览器；
- 可用以下命令核对实际启动的 URL：
  ```bash
  adb shell dumpsys activity activities | grep -m1 'dat=https'
  ```
- 返回 App 后，首页“点击统计”中 KIMI 的跳转次数 +1；
- 统计记录存 Room，杀进程/重启后仍保留；
- 统计页可“清除记录”，需要二次确认。

## 6. 异常与边界验证

### 6.1 无网络但有缓存

```bash
adb shell cmd connectivity airplane-mode enable   # 或 svc wifi disable / svc data disable
```

回到首页点“同步”，预期：

- 继续展示缓存产品；
- 显示“当前无网络，正在展示缓存数据，数据可能已过期”；
- 状态卡显示“当前离线，展示的是缓存数据，可能已过期”；
- 不崩溃，不把缓存描述成实时数据。

恢复网络后关闭飞行模式。

### 6.2 空数据

设置 → 数据源 → 官方数据。分两种情况：

- 采集器**未启动**或未执行 `adb reverse`：同步失败；状态卡显示“数据来源：官方数据”和失败原因（如“无法连接 GLM 官方采集器”）；空态有“重新同步”和“切回演示数据”。
- 采集器已启动但返回空数组/没有 active offer：状态卡“数据来源：官方数据”，空态“暂无官方数据”，说明当前官方源没有可展示优惠；仍提供“重新同步”和“切回演示数据”。

两种情况都不能出现任何演示产品被伪装成官方数据。

### 6.3 来源失败

选择“网络失败”并同步。预期：

- 已有产品继续展示；
- 状态卡显示“上次同步失败：Deterministic demo network failure”；
- 横幅“同步失败，正在展示缓存数据，数据可能已过期”；
- 之后选择“重复同步”或“首次同步”可恢复。

官方模式的来源失败：停止采集器或断开 `adb reverse`，点“同步”。预期：

- 不崩溃；
- 状态卡显示“数据来源：官方数据”和采集器错误；
- 若之前成功同步过，保留官方缓存并提示可能过期；
- 不把旧价格说成实时价格。

### 6.4 数据库读取失败

日常安装没有故障注入入口。验证方式：

- 运行 `HomeScreenTest.databaseError_showsRecoverableRetryEntry` / `databaseErrorWithCachedContent_keepsRetryEntryVisible`；
- 异常时 UI 显示“本地数据读取失败，请重试”+原因+“重试读取”；
- 已有缓存时状态卡保留“重试读取”，不会因为错误把内容清空。

### 6.5 Custom Tab 不可用 / 非法 URL

真机通常至少有一个浏览器，无法自然制造“无浏览器”。使用仪器测试验证：

- `OfficialOfferLauncherTest.unavailableCustomTab_fallsBackToBrowserOnce`
- `OfficialOfferLauncherTest.noAvailableBrowser_returnsErrorWithoutThrowing`
- `DetailViewModelTest` 的非法 URL/打开失败分支

对应 UI 文案：

- 未找到浏览器：“未找到可用浏览器，无法打开官网”
- 非法地址：“官网订阅地址无效，无法打开”
- 其他打开失败：“官网打开失败，请稍后重试”

如需在测试机上手工验证“无浏览器”，可临时禁用浏览器包并在测试后恢复；不要在个人主力机上操作。

## 7. 真实数据源接入边界

### 7.1 当前 GLM 官方源

- 来源：`https://www.bigmodel.cn/glm-coding`，公开个人套餐页（Lite / Pro / Max；连续包月/包季/包年）。
- 采集：`tools/glm-collector` 用 headless Chrome 渲染页面，输出 `RemoteSyncPayload` 同构 JSON；本机服务默认监听 `127.0.0.1:8787`，5 分钟缓存，失败 503 不回退旧价；外部 heartbeat/GitHub schedule 采集并发布 HTTPS 静态 JSON。
- Android：`GlmCodingPlanRemoteDataSource` 从 `BuildConfig.GLM_COLLECTOR_URL` 读取 JSON；Debug 默认本机 + `adb reverse tcp:8787 tcp:8787`，Release 用 `-PglmCollectorUrl` 注入 Pages 地址。
- 来源字段：`sourceUrl`、`fetchedAtEpochMillis`、`titleOrSummary`（页面原文摘要）、`officialUrl`（每个 offer）全部必填。
- 金额：人民币分；季/年 `actualAmountMinor` 是总价，`monthlyEquivalentMinor` 是折合月价；`taxIncluded=true` 按页面含税展示处理。
- 边界：只覆盖公开个人套餐，不含团队套餐、登录优惠、优惠券、首月立减；选择器改版会采集失败。

### 7.2 接入下一条真实源必须满足

1. `RemoteSourceDto`：
   - `id` 使用真实 sourceId，不得使用 `demo-` 前缀；
   - `sourceType` 为 `OFFICIAL_API` / `OFFICIAL_RSS` / `OFFICIAL_WEB`；
   - `sourceUrl` 必填；
   - `fetchedAtEpochMillis > 0`；
   - `titleOrSummary` 必填，保存原文标题/摘要。
2. 产品/套餐/优惠 ID 不得使用 `demo-` 前缀。
3. 优惠必须能追溯到来源；没有来源 URL、抓取时间或原文信息的记录，标准化会失败，不得标记为官方真实数据。
4. 价格、币种、计费周期、地区、含税口径必须来自来源，不得猜测或回填。
5. 同一 `sourceId` 的类型不可变；已有 DEMO 来源不能被后续官方同步改写成 OFFICIAL。
6. 快照只追加，不覆盖/删除；UI 不得把旧缓存描述成实时数据。
7. 季/年/月不能直接比总价；有多个 active offer 时按折合月均比较。
8. 采集失败必须有明确错误并保留缓存，不能回退旧价并标记为实时。

新适配器应实现 `RemoteDataSource`，并在 `AppContainer` 注册独立 sourceId；先补标准化/失败测试，再接网络。
接入步骤与字段示例见 [DATA_SOURCE_INTEGRATION.md](DATA_SOURCE_INTEGRATION.md)。

## 8. 生产能力缺口

### FCM 尚未配置

- 没有 `google-services.json`、没有 Firebase SDK、没有设备 token 注册。
- `NoOpPushNotificationService` 只是边界；当前所有通知都是本地生成和本地派发。
- 没有后端触发、用户分群、送达回执或服务端去重。
- 接入 FCM 需要：Firebase 项目、google-services 配置、token 注册/刷新、后端推送、通知权限与渠道、深链、失败重试。

### WorkManager 非实时

- 周期任务为 15 分钟，约束为联网；系统会延迟、合并或跳过。
- 设备休眠、厂商省电、后台限制会进一步延迟。
- 它不能替代服务端 FCM；演示时用首页“同步”立即触发。
- 关闭“自动同步”会取消唯一周期任务；重启后按持久化偏好恢复，不会无条件复活。

### 其他缺口

- Debug 官方 GLM 源依赖开发机上的 Node/Chrome 采集器；CI/Pages 定时采集 pipeline 已启用并成功运行，但仍是对公开页面抓取的静态快照，不是可部署的生产后端；没有采集监控/告警。
- 没有后端、账号、支付、真实订阅或价格校验。
- 没有 Play 发布配置与混淆策略；正式签名 Release 已由 `release.yml` 产出并验签（run 41221071911），keystore 仅本机/Keychain。
- 设备矩阵只验证过 OPD2515 / API 37；阶段 12 的 86 项设备测试已在该设备通过，但 API 26–36/其他 ROM 仍属未验证。
- 通知渠道关闭、长期省电、长期 soak 未验证。
- 深链“已有 saved state + 全新 intent”罕见路径未覆盖。

## 9. 真实与演示数据隔离规则

- 演示数据：`sourceId = demo-local`，`DataSourceType.DEMO`；source/product/plan/offer ID 必须带 `demo-` 前缀。
- 官方数据：`sourceId = glm-coding-plan-official`（GLM），`DataSourceType.OFFICIAL_WEB`，不使用 `demo-` 前缀；必须带 `sourceUrl`、`fetchedAt`、`titleOrSummary`。
- 首页按当前 `DataSourceMode` 过滤 `offer.source.type`；切换模式只改变展示范围，不 UPDATE 历史 `SourceInfo`、快照或同步元数据。
- 数据源类型不可变；同一 sourceId 若从 DEMO 改成 OFFICIAL，同步直接失败并回滚。
- 演示记录永远不会被标记成官方历史；官方空态不会展示演示产品。
- 演示数据不是实时优惠；官方采集失败时也不会产生/回退“官方实时数据”。

## 10. 功能、测试与未验证项对照

| 功能/场景 | 自动化证据 | 状态 |
|---|---|---|
| 首页演示横幅、来源/更新时间（自动化） | `HomeScreenTest` | 通过（仪器） |
| 单个订阅/取消、全部订阅、重启持久化 | `SubscriptionPersistenceTest`、`HomeViewModelTest`、`HomeScreenTest` | 通过 |
| 首次同步、新增优惠、涨价、降价、重复同步、网络失败 | `OfferSyncServiceIntegrationTest`（13 项） | 通过 |
| 只通知已订阅产品一次 | `NotificationDispatcherTest`、`OfferSyncServiceIntegrationTest` | 通过 |
| 点击通知进入详情 | `NotificationDeepLinkTest`（7 项） | 通过 |
| 折线图、历史最低、币种、周期、优惠时间 | `DetailScreenTest`、`PriceChartModelTest`、`HomeViewModelTest` | 通过 |
| 季付总价/折合月均、按最低月均选择首页 offer | `PriceChartModelTest`、`HomeScreenTest` | 通过（JVM/仪器） |
| 官网 URL、Custom Tab 回退、点击统计 | `OfficialOfferLauncherTest`、`OfficialUrlEndToEndTest`、`StatisticsViewModelTest` | 通过 |
| 无网络、空数据、来源失败、DB 错误恢复 | `HomeScreenTest`、`HomeViewModelTest` | 通过 |
| 干净构建、JVM、Lint | 本文件第 1 节 | 通过 |
| 阶段 11 手工设备验收 1–8 | `docs/evidence/2026-10-30/phase11/device-manual/` | 通过（部分异常项由自动化覆盖，见清单第 12 节） |
| GLM 采集器 JSON 契约 | `tools/glm-collector/test/payload.test.mjs` | 通过（4 / 4） |
| GLM payload 校验器与 CLI | `tools/glm-collector/test/validate.test.mjs` | 通过（6 / 6） |
| endpoint 空值/过期/未来时间戳/ BuildConfig | `GlmCodingPlanRemoteDataSourceTest`、`BuildConfigTest` | 通过（JVM） |
| GLM 适配器 → Room 集成 | `GlmCodingPlanSourceIntegrationTest` | 通过（仪器） |
| 阶段 12 设备测试（86 项） | `connectedDebugAndroidTest` | 通过（86 / 86） |
| API 26–36、其他 ROM、通知渠道/省电 soak | 无 | **未验证** |
| GLM 官方源真机采集/同步 | `docs/evidence/2026-10-30/phase12/device-manual/` | 通过（手工 + 86 项自动化） |
| CI 定时采集/Pages | `.github/workflows/glm-collector.yml` | 通过：run 41180172626；快照 JSON 校验 3 / 9 |
| FCM / 服务端推送 | 未配置 | **未实现** |
| 正式签名 Release、应用 CI | `release.yml`；run 41221071911 | 通过（v2 证书 `54f6ee32…`）；CI artifact 已真机安装，哈希一致 |
| 采集触发可靠性 | 外部 heartbeat + GitHub schedule 兜底 | heartbeat 代码/单测/mock 通过；远端 dispatch/launchd **待激活**；GitHub schedule 仅各一次自动运行 |
| Play 发布、混淆策略 | 未配置 | **未实现** |

“通过”不等于生产验收：设备测试只证明 OPD2515 / API 37 且自动化覆盖到的路径；阶段 11 手工场景与阶段 12 官方模式真机场景已完成，设备矩阵、真实源生产化与长期稳定性仍需补齐。

## 11. 证据

- 阶段 12 构建、采集器、JVM 与设备证据：`docs/evidence/2026-10-30/phase12/`
  - `collector-payload.json`、`collector-health.json`、`collector-serve.log`
  - `collector-tests.txt`
  - `unit-test-results/`（22 XML）、`lint-results-debug.txt`
  - `phase12-connected-test-results.xml`（86 / 86）、`phase12-connected.log`、`phase12-device.txt`
  - `device-manual/`（官方首页/季付详情/采集器失败/切回演示截图与 UI dump）
  - `final-status.txt`、`environment.txt`
- 阶段 10 设备与构建证据：`docs/evidence/2026-10-30/phase10-*`
- 阶段 11 构建证据：`docs/evidence/2026-10-30/phase11/`
- 阶段 11 手工验收截图/UI dump/通知记录：`docs/evidence/2026-10-30/phase11/device-manual/`
- 阶段 11 手工验收清单：[PHASE11_DEVICE_ACCEPTANCE.md](PHASE11_DEVICE_ACCEPTANCE.md)
- 阶段 11 进度：[PHASE11_PROGRESS.md](PHASE11_PROGRESS.md)
- 阶段 12 进度：[PHASE12_PROGRESS.md](PHASE12_PROGRESS.md)
- GLM 采集器说明：[../tools/glm-collector/README.md](../tools/glm-collector/README.md)
- 交接文档：[HANDOFF.md](HANDOFF.md)
- 官方源接入契约：[DATA_SOURCE_INTEGRATION.md](DATA_SOURCE_INTEGRATION.md)
