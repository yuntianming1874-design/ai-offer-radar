# 阶段 12：GLM Coding Plan 官方源纵向切片

更新时间：2026-10-31
状态：**阶段 12 已完成；阶段 12 增强（endpoint 配置化 + CI 定时采集）本地验收完成，远程 Pages 待启用**
范围：第一条真实官方数据源、`QUARTERLY` 计费周期、采集器、Android 适配器、endpoint/CI、测试与证据。

> 2026-10-31 00:53 增强：endpoint 已改为构建时注入（Debug 本机 / Release CI HTTPS），
> GitHub Actions 定时采集 workflow 已实现并本地校验；JVM 137/137、设备 86/86、Node 10/10。
> 远程仓库已创建并推送，Pages 尚未启用，workflow 未在真实远端运行。详见文末“阶段 12 增强”。

## 目标与结论

阶段 12 只接入**一条**真实来源：智谱 GLM Coding Plan 官方套餐页
<https://www.bigmodel.cn/glm-coding>。选择它是因为页面公开、无需登录即可显示
Lite / Pro / Max 三档个人套餐价格，并且有明确的官方 URL 可回链。

该页面是 Vue SPA，价格只在浏览器执行 JavaScript 后出现；匿名 API
`/api/biz/product/overview` 需要 Authorization，不能直接抓取。因此本切片采用：

```text
Android App (OFFICIAL 模式)
  -> http://127.0.0.1:8787/v1/glm-coding-plan   (仅本地)
  -> tools/glm-collector (Node.js + headless Chrome)
  -> https://www.bigmodel.cn/glm-coding (公开页面)
```

这是一条**开发机辅助的真实源**，不是生产后端。设备通过 `adb reverse` 访问
开发机上的采集器；采集器不可用时，App 进入同步失败态并保留缓存，不会回退旧价。

## 数据模型

- source：`glm-coding-plan-official`，类型 `OFFICIAL_WEB`，`sourceUrl` 为官方套餐页，
  `fetchedAt` 为本次采集时间。
- 3 个产品：`glm-coding-plan-official-lite` / `-pro` / `-max`。
- 3 个标准套餐（`STANDARD`），每个产品一个。
- 9 个 offer：每个产品 × `MONTHLY` / `QUARTERLY` / `YEARLY`。
- 季付/年付的 `actualAmountMinor` 是这一期的**总价**；
  `monthlyEquivalentMinor` 是页面标注的折合月价。首页选“折合月价最低”的 offer
  展示总价并单列折合月均；详情页显示每个周期的总价和月均。
- `taxIncluded=true`（页面展示价按含税处理；如官方口径变化需调整）。
- `startsAt = fetchedAt`，`endsAt = null`；不编造活动截止时间。
- 只有原价高于现价时 `offerType=PROMOTION`；月付 tab 无划线原价时按 `NONE`。

本次采集到的页面价格（2026-10-30 23:39 北京时间）：

| 档位 | 连续包月 | 连续包季 8 折 | 连续包年 7 折 |
|---|---:|---:|---:|
| Lite | ¥118/月 | ¥94.4/月，季总价 ¥283.20（原 ¥354） | ¥82.6/月，年总价 ¥991.20（原 ¥1416） |
| Pro | ¥538/月 | ¥430.4/月，季总价 ¥1291.20（原 ¥1614） | ¥376.6/月，年总价 ¥4519.20（原 ¥6456） |
| Max | ¥1078/月 | ¥862.4/月，季总价 ¥2587.20（原 ¥3234） | ¥754.6/月，年总价 ¥9055.20（原 ¥12936） |

这些数字是 2026-10-30 的页面快照，不是永久价格；每次同步都会带新的
`fetchedAt` 和原文摘要。

## 关键文件

### 采集器

- `tools/glm-collector/payload.mjs`：把渲染结果转换成 `RemoteSyncPayload` 同构 JSON；
  校验 3 个 tab、3 个档位、价格正数、现价不高于原价。
- `tools/glm-collector/collector.mjs`：Node ESM，无 npm 依赖；spawn 本机 Chrome，
  通过 CDP 点击 tab、读取 `.cp-card`；`--once` 或 `--serve`（默认
  `127.0.0.1:8787`，缓存 5 分钟，失败返回 503，不回退旧数据）。
- `tools/glm-collector/test/payload.test.mjs`：4 个 Node 测试。
- `tools/glm-collector/README.md`：运行、契约、边界和已知限制。

### Android

- `app/src/main/java/com/aioffer/radar/core/network/GlmCodingPlanRemoteDataSource.kt`：
  `HttpURLConnection` 读取采集器 JSON，校验 schema/source/fetchedAt/非空，再映射为
  `RemoteSyncPayload`；默认 endpoint
  `http://127.0.0.1:8787/v1/glm-coding-plan`。
- `app/src/main/java/com/aioffer/radar/core/model/BillingPeriod.kt`：新增
  `QUARTERLY(3)`。
- `app/src/main/java/com/aioffer/radar/feature/home/HomeScreen.kt`：选择折合月价最低的
  offer；季/年付显示总价 + 折合月均；官方已配置空态文案。
- `app/src/main/java/com/aioffer/radar/feature/detail/DetailScreen.kt`：季付标签与折合月均。
- `app/src/main/java/com/aioffer/radar/feature/detail/PriceChartModel.kt`：季/年付图表使用
  折合月均，避免总价与月价混比。
- `app/src/main/java/com/aioffer/radar/feature/settings/SettingsScreen.kt`：显示官方源
  已配置说明与采集器启动提示。
- `app/src/main/AndroidManifest.xml` + `app/src/main/res/xml/network_security_config.xml`：
  仅允许 `127.0.0.1`、`localhost`、`10.0.2.2` 明文，其他域名继续禁止明文。
- `app/src/main/java/com/aioffer/radar/AppContainer.kt`：官方槽改为真实 GLM 适配器，
  `officialSourceId=glm-coding-plan-official`，`officialAdapterConfigured=true`。
- `app/src/main/java/com/aioffer/radar/core/network/UnconfiguredOfficialRemoteDataSource.kt`：
  已删除；官方模式不再有“未配置”占位。

### 测试

- `app/src/test/java/com/aioffer/radar/core/network/GlmCodingPlanRemoteDataSourceTest.kt`：
  6 个 JVM 测试（映射、HTTP 失败、source 不匹配、缺 sourceUrl、schema 版本、非法 JSON）。
- `app/src/androidTest/java/com/aioffer/radar/data/GlmCodingPlanSourceIntegrationTest.kt`：
  真实采集 JSON asset → `GlmCodingPlanRemoteDataSource` → `OfferSyncService` → Room；
  断言 `OFFICIAL_WEB`、9 个 offer、Lite 季付总价/月均/原价、3 个产品各 3 个周期。
- `app/src/androidTest/assets/glm-coding-plan-payload.json`：来自真实采集器的 payload。
- `app/src/test/java/com/aioffer/radar/feature/detail/PriceChartModelTest.kt`：
  `QUARTERLY` 使用月均。
- `app/src/androidTest/java/com/aioffer/radar/feature/home/HomeScreenTest.kt`：
  首页选择折合月价最低的季付 offer。
- `app/src/androidTest/java/com/aioffer/radar/navigation/SettingsNavigationTest.kt`：
  官方模式显示已配置说明，返回首页显示“数据来源：官方数据”。

## 验证结果（2026-10-31 00:14）

```bash
GRADLE_USER_HOME=$PWD/.gradle-home \
ANDROID_USER_HOME=$PWD/.android-home \
GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
./gradlew --offline --no-daemon \
  clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

- `BUILD SUCCESSFUL`，54s，81 tasks。
- JVM：**134 / 134**，0 failed / 0 errors / 0 skipped，22 suites。
- Lint：**0 errors / 2 warnings**（既有 API 标记与未使用颜色）。
- 采集器：`node --test` **4 / 4**；`--serve` 实测返回 3 products / 3 plans / 9 offers。
- `connectedDebugAndroidTest`：**86 / 86**，0 failed / 0 errors / 0 skipped，1m 5s
  （2026-10-31 00:13，OPD2515 / Android 17 / API 37 / serial <device-serial>）。
- 首次设备回归时 7 个 `NotificationDeepLinkTest` 超时；根因是设备 Wi-Fi 与移动数据均关闭，
  打开网络后重跑全绿。不是代码缺陷。

证据：

- `docs/evidence/2026-10-30/phase12/collector-payload.json`
- `docs/evidence/2026-10-30/phase12/collector-health.json`
- `docs/evidence/2026-10-30/phase12/collector-serve.log`
- `docs/evidence/2026-10-30/phase12/collector-tests.txt`
- `docs/evidence/2026-10-30/phase12/official-page.png`
- `docs/evidence/2026-10-30/phase12/unit-test-results/`（22 XML）
- `docs/evidence/2026-10-30/phase12/lint-results-debug.txt`
- `docs/evidence/2026-10-30/phase12/final-status.txt`
- `docs/evidence/2026-10-30/phase12/environment.txt`
- `docs/evidence/2026-10-30/phase12/phase12-connected-test-results.xml`
- `docs/evidence/2026-10-30/phase12/phase12-connected.log`
- `docs/evidence/2026-10-30/phase12/phase12-device.txt`
- `docs/evidence/2026-10-30/phase12/device-manual/`（官方首页/季付详情/采集器失败/切回演示的截图与 UI dump）

## 真机验收结果（2026-10-31 00:10–00:13）

1. 启动采集器 `node tools/glm-collector/collector.mjs --serve`。
2. `adb reverse tcp:8787 tcp:8787`。
3. 安装 debug APK，设置切“官方数据”，首页同步成功；状态卡显示“数据来源：官方数据”、
   “最后成功同步：2026-10-31 00:10 北京时间”。
4. 首页 3 个产品，每个产品 3 个周期；因年付月均最低，卡片显示年付：
   - Lite `¥991.20/年`，折合月均 `¥82.60`；
   - Pro `¥4519.20/年`，折合月均 `¥376.60`；
   - Max `¥9055.20/年`，折合月均 `¥754.60`。
5. Lite 详情切“季付”：总价 `CNY 283.20`、原价 `CNY 354.00`、折合月均 `CNY 94.40`、
   优惠 20%；来源“官方网站”；价格历史显示“证据不足，将从首次同步开始积累”。
6. 停止采集器后同步：首页保留 3 个官方产品缓存，状态卡显示
   “失败原因：无法连接 GLM 官方采集器…”，最后成功同步仍是 00:10，不崩溃、不伪装实时。
7. 切回演示：DEEPSEEK/KIMI 等演示卡恢复，来源“演示数据”；GLM 官方产品完全隐藏，
   证明模式切换不混数据。
8. `connectedDebugAndroidTest`：86 / 86 通过。

## 阶段 12 增强：endpoint 配置化 + CI 定时采集（2026-10-31 00:53）

目标：把官方源从“开发机本机服务”升级为可定时发布的远程 JSON 管道，同时保留 Debug 本地开发路径。

- endpoint：
  - `BuildConfig.GLM_COLLECTOR_URL` 构建时注入；
  - Debug 缺省 `http://127.0.0.1:8787/v1/glm-coding-plan`；
  - Release 未传 `-PglmCollectorUrl` 时为空，官方模式明确“未配置”，失败关闭；
  - 示例：`./gradlew assembleRelease -PglmCollectorUrl=https://<owner>.github.io/<repo>/glm-coding-plan.json`。
- 客户端新鲜度：
  - `GlmCodingPlanRemoteDataSource` 校验 `fetchedAt`，超过 **2 小时**拒绝；
  - 晚于当前时间超过 5 分钟拒绝；
  - endpoint 为空时不发网络请求，直接给出可操作错误。
- CI：
  - `.github/workflows/glm-collector.yml`，每 30 分钟 + 手动触发；
  - Node 24 + Chrome headless 采集，`validate-payload.mjs` 校验 3×3=9、来源、金额、新鲜度；
  - 发布 `glm-coding-plan.json` + 最小 index 到 GitHub Pages；
  - 采集/校验失败时 job 失败，不发布半成品。
- 本地验证：
  - `tools/glm-collector/validate.mjs` / `validate-payload.mjs`，Node 测试 10/10；
  - 真实采集一次：3 tabs / 9 offers / 12s，payload 校验通过；
  - workflow YAML 经 Ruby Psych 解析通过，index 生成脚本本地模拟成功（3 products / 9 offers）；
  - clean 构建 56s / 84 tasks；JVM **137 / 137（23 suites）**；Lint 0 errors / 2 warnings；
  - 设备 `connectedDebugAndroidTest` **86 / 86**（1m 8s）；
  - Release 无 property / 注入 property 两种构建均成功，BuildConfig 分别为空 / 注入值。
- 未验证：
  - 远程仓库已创建并推送，GitHub Pages 未启用，workflow 未在 GitHub 真实运行；
  - Release 未签名，CI 采集依赖 public 页面选择器与 Chrome 可用性。
- 证据：`docs/evidence/2026-10-31/phase12-endpoint-ci/`；命令与 CI 配置见 `docs/CI_GLM_COLLECTOR.md`。

## 已知限制

- Debug 采集器依赖开发机 Chrome；CI/Release 路径已实现；远程仓库已推送，Pages 尚未启用。
- 官方页面改版、CSS 选择器变化、网络/Chrome 故障都会导致采集失败；失败时返回 503，
  App 显示错误，不显示旧价。
- 只抓个人套餐的公开价格，不抓团队套餐、登录后优惠、优惠券、首月立减。
- 月付 tab 没有划线原价，按原价=现价处理；`taxIncluded` 按含税理解。
- 官方套餐页没有匿名价格 API；在拿到正式授权/稳定接口前，不把采集器部署成公共服务。
- 微博/小红书 robots 对通用爬虫 `Disallow: /`，不做自动抓取；B 站/社媒只能做
  **未验证线索**，不能进入价格库或触发通知。详见 `docs/DATA_SOURCE_INTEGRATION.md`。
- API 26–36、其他 ROM、通知渠道/省电 soak、FCM、release/CI 仍未验证或未实现。

## 下一步

1. 在已推送的私有仓库启用 GitHub Pages，真实跑通定时采集，并用 `-PglmCollectorUrl` 注入 Release endpoint。
2. 为采集器增加 selector 漂移/抓取失败告警与“上次成功快照”健康检查；保持 provenance。
3. 再扩展第二个产品，仍按“单产品、单来源、可审计”推进。
4. 扩大设备矩阵：API 26–36、其他 ROM、通知渠道关闭、厂商省电与长期 soak。
