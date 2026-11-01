# AI 优惠雷达

一个本地优先的 Android 演示应用，用于跟踪国内 AI 产品（Kimi、DeepSeek、GLM 等）的订阅价格、优惠和价格历史。

代码仓库：<https://github.com/yuntianming1874-design/ai-offer-radar>（public）。
CI 快照：<https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json>。

> **当前默认使用内置演示数据，不是实时优惠，也不代表任何官网当前价格。**
> 演示模式下首页固定展示“演示数据，非实时优惠”。演示价格、折扣和历史均为程序生成的确定性测试数据。
> 设置中的“官方数据”接入 GLM Coding Plan 官方套餐页：Debug 默认连本机渲染采集器，
> 也可用 `-PglmCollectorUrl=...` 指向 CI 定时发布的 HTTPS 快照；Release 未注入 endpoint 时
> 官方模式会明确显示未配置。采集器/CI 不可达或快照超过 2 小时会同步失败并保留缓存，
> 不会把旧数据说成实时数据。

## 当前状态

| 范围 | 状态 |
|---|---|
| 阶段 0–8（模型、Room、同步、首页、详情、官网跳转、WorkManager、通知） | 已完成 |
| 阶段 9（设置、数据源切换、完整异常状态） | 已完成；官方槽在阶段 12 替换为真实 GLM 适配器 |
| 阶段 10（自动化测试与验收覆盖） | 已完成：127 JVM / 84 设备测试通过（阶段 11 基线） |
| 阶段 11（设备端到端验收与交付） | 已完成：手工验收 1–8 已在 OPD2515 执行并归档（2026-10-30 22:19–22:32），127 JVM / 84 设备测试 |
| 阶段 12（GLM Coding Plan 官方源纵向切片） | 已完成：134 JVM / 22 suites；86 / 86 设备测试通过；官方模式真机验收已归档（2026-10-31 00:13） |
| 阶段 12 增强（endpoint 配置化 + CI 定时采集） | 已完成：137 JVM / 23 suites；86 / 86 设备；GitHub Actions + Pages 已启用，远程 workflow 成功发布 3 products / 9 offers |
| 阶段 12 增强 P1（CI 自证端到端） | 已完成远端首跑：部署后字节/新鲜度校验、Android Release build job、失败/2h 过期 Issue 告警；15 Node tests；run 41182662449 成功；P2 已补失败路径演练 |
| P2（失败告警演练 + Release 签名） | 失败告警演练已完成；unsigned 与正式签名 Release workflow 均已远端成功；CI 签名 artifact 已真机安装并验签；真实过期快照被 2 小时守卫拦截并手动恢复；签名 key 仅在本机/Keychain |
| P2.3（外部 heartbeat） | 已激活：每 15 分钟检查线上快照，超过 75 分钟通过 `repository_dispatch` 触发采集；GitHub `schedule` 降为兜底；8 项 heartbeat 测试、真实 dispatch（run 41330761449）与 macOS launchd 安装/运行均通过 |

当前是可构建、可安装、可测试的开发版本，不是生产版本。

最近一次验证（2026-11-02 01:21，P2.3 外部 heartbeat 真实 dispatch 与 launchd；设备基线为 01:40 P2 签名安装）：

- `clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`：`BUILD SUCCESSFUL`，56s，84 tasks
- JVM 单元测试：**137 / 137**（23 suites）
- Lint：**0 errors，2 warnings**（既有 API 标记与未使用颜色）
- 采集器/发布校验/heartbeat 测试：**23 / 23**（4 个 payload + 6 个校验/CLI + 5 个发布端点校验 + 8 个 heartbeat）；本机采集器实测 3 products / 3 plans / 9 offers
- P1 本地验证：`testDebugUnitTest assembleRelease` 33s / 76 tasks；线上 `verify-published.mjs` 通过；helper APK 签名脚本本地模拟通过；两个 workflow YAML 解析通过
- P1 远端首跑（[run 41182662449](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41182662449)）：`collect-and-publish` 34s，部署后校验第 1 次通过（3 档 / 9 offers，SHA-256 `3357c7cd…`）；`build-android` 3m 4s，Gradle `BUILD SUCCESSFUL in 2m 29s`，BuildConfig 含线上 URL，helper APK SHA-256 `8783870e…`；`close-recovered-issue` 成功；`notify-failure` 健康时跳过；[health run 41182665177](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41182665177) 7s 成功
- 设备仪器测试：**86 / 86**（OPD2515 / Android 17 / API 37，1m 8s）
  - 阶段 11 历史设备基线：84 / 84（2026-10-30 22:34）
- Release endpoint：不传 property 时 `GLM_COLLECTOR_URL=""`（官方模式明确失败关闭）；传 `-PglmCollectorUrl=https://example.com/glm-coding-plan.json` 后 BuildConfig 与 APK 构建均通过
- CI 定时采集：`.github/workflows/glm-collector.yml` 已在 GitHub Actions 真实运行成功（[run 41180172626](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41180172626)）；Pages 快照 <https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json> 经 `validate-payload.mjs` 校验通过（3 products / 3 plans / 9 offers）
- CI 自证（P1）：部署后由 `verify-published.mjs` 重拉线上 JSON，HTTP/schema/2h 新鲜度通过且字节 SHA-256 必须等于本次 artifact；`build-android` 用同一 endpoint 跑 JVM 测试与 Release 构建；失败自动开 Issue，`glm-snapshot-health.yml` 每小时检查 2 小时新鲜度
- P2 远端演练：`max_age_seconds=1` 创建 Issue #1，重复失败去重且 run 置红，恢复后评论并关闭；`release.yml` 的 unsigned 构建成功（versionCode 2 / 1.0.1-ci，[run 41214833322](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41214833322)）；`sign_release=true` 缺 Secrets 时 22s 失败关闭（[run 41215119074](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41215119074)）
- P2 正式签名（[run 41221071911](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41221071911)）：1m59s 成功；versionCode 1 / 1.0.0；APK SHA-256 `b08b6208691f567cfec88ff4a555dd57319c13676509816fb94e88ab420aaf27`；v2 证书 SHA-256 `54f6ee32…`；artifact 已下载验签
- P2 签名 artifact 真机安装（2026-11-01 01:36）：设备内 APK SHA-256 与 artifact 完全一致；证书一致；`adb install -r` 成功
- P2 真实过期快照（2026-11-01 01:36）：快照 15:31:50Z 超过 2h，同步显示“采集数据已过期…已超过 120 分钟”，缓存保留、无崩溃；手动触发 [run 41225390770](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41225390770) 刷新后 01:40 同步成功
- 外部 heartbeat（2026-11-02 01:11–01:21）：推送 commit `ad78a3a` 后 `repository_dispatch` 真实运行成功（[run 41330211849](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41330211849)）；heartbeat 脚本按 `--threshold-minutes 1` 真实发送 dispatch（[run 41330761449](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41330761449)，成功）；macOS launchd `com.aioffer.radar.collector-heartbeat` 已安装并 loaded，首次运行读到快照 5m、判定 fresh；线上快照 `fetchedAt=17:18:29Z`、3 档 / 9 offers，`verify-published` 通过
- 阶段 12 官方模式真机验收：GLM Lite/Pro/Max × 月/季/年；Lite 年付首页 `¥991.20/年`、折合 `¥82.60`；Lite 季付详情总价 `¥283.20`、折合 `¥94.40`；采集器停止后失败原因与缓存保留；切回演示不混数据
- 阶段 12 明细：[docs/PHASE12_PROGRESS.md](docs/PHASE12_PROGRESS.md)
- 阶段 11 手工验收：**已执行**（22:19–22:32），证据见 [docs/PHASE11_DEVICE_ACCEPTANCE.md](docs/PHASE11_DEVICE_ACCEPTANCE.md) 与 `docs/evidence/2026-10-30/phase11/device-manual/`
- 阶段 11 明细：[docs/PHASE11_PROGRESS.md](docs/PHASE11_PROGRESS.md)
- 阶段 10 明细：[docs/PHASE10_PROGRESS.md](docs/PHASE10_PROGRESS.md)
- 阶段 9 明细：[docs/PHASE9_PROGRESS.md](docs/PHASE9_PROGRESS.md)
- 阶段 8 验收明细：[docs/PHASE8_PROGRESS.md](docs/PHASE8_PROGRESS.md)

## 功能

### 首页

- 产品卡片：Logo（失败时本地占位图）、名称、当前优惠摘要、价格/计费周期、来源、更新时间、订阅状态。
- 单个订阅/取消订阅、一键全部订阅，状态持久化。
- 加载、空数据、错误、离线但有缓存、同步失败等状态。
- 首页常驻状态卡：当前数据来源、最后成功同步时间、离线/缓存过期提示、上次同步失败原因。
- 空数据、同步失败、数据库读取异常都提供重新同步/重试读取入口；官方模式提供“切回演示数据”。
- 演示模式固定横幅；场景切换入口只在演示模式显示。
- “🔥 历史最低”只在同一比较分组存在**更早的合格历史快照**且当前价不高于最低价时显示，并列最低也显示。

### 设置与数据源边界

- DataStore 持久化：数据源模式（演示/官方）、自动同步偏好、通知权限提示状态。
- 数据源切换只改变首页展示范围与同步 sourceId，不改写历史 `SourceInfo.type`、快照或同步元数据。
- 演示与官方 sourceId 独立；演示 source/product/plan/offer ID 必须带 `demo-` 前缀，官方数据不得使用该前缀。
- 官方模式接入 GLM Coding Plan 官方套餐页：Lite / Pro / Max × 月/季/年 共 9 个 offer；
  显示“官方数据”和最后成功同步时间，采集器不可达时显示失败原因，不伪造价格。
- 自动同步开关控制 WorkManager 周期任务；关闭后不会在下次启动被重新打开。
- 通知提示在用户选择“稍后/开启”后持久化，避免进程重建后反复打扰；系统权限状态仍单独展示。

### 真实官方源（GLM Coding Plan）

- 来源：<https://www.bigmodel.cn/glm-coding>，公开个人套餐页，无需登录。
- 页面是 Vue SPA，价格需要浏览器渲染；由 `tools/glm-collector` 用 headless Chrome 读取。
  - 本地开发：`collector.mjs --serve` + `adb reverse tcp:8787 tcp:8787`；
  - 远程/生产：外部 heartbeat 每 15 分钟检查快照，超过 75 分钟通过
    `repository_dispatch` 触发采集；GitHub Actions `schedule` 每 30 分钟作为兜底。
    采集、校验后发布到 GitHub Pages 静态 JSON，构建时用
    `-PglmCollectorUrl=...` 注入 endpoint。
- 输出 `OFFICIAL_WEB` 来源、来源 URL、抓取时间、原文摘要、官方入口 URL；每条 offer 带
  月/季/年计费周期、总价、原价和折合月均。
- 首页选折合月价最低的 offer 展示总价并单列月均；详情页分别展示三个周期的价格。
- 采集器失败返回 503，CI 校验失败不覆盖上一版；App 拒绝抓取时间超过 2 小时的快照，
  同步失败时保留缓存，不把旧价格描述成实时数据。
- 详见 [阶段 12](docs/PHASE12_PROGRESS.md)、
  [采集器 README](tools/glm-collector/README.md)、
  [CI 采集](docs/CI_GLM_COLLECTOR.md) 与 [外部 heartbeat](docs/HEARTBEAT.md)。

### 详情与价格历史

- 当前有效、尚未生效、历史优惠分组。
- 套餐和计费周期筛选。
- 同一 `ComparisonKey` 的历史价格折线图；横轴时间，纵轴价格，显示币种。
- 年付总价与月均折算分开显示；历史点不足时显示“证据不足，将从首次同步开始积累”，不伪造趋势。
- 图表有文本摘要、最高/最低值、起止时间、无障碍描述。
- 每条优惠展示标题、折扣、原价/现价、生效/截止、来源、更新时间和历史最低标识。

### 官网订阅与点击统计

- 有效优惠卡片可“去订阅”。
- URL 从 Room 读取；生产校验只接受 `https`，演示构建保留 `http` 测试开关，但内置 URL 全部为 HTTPS。
- 优先 Custom Tab；不可用时回退到安全浏览器 Intent；无浏览器时提示，不崩溃。
- 成功交给外部处理后记录产品、优惠、点击时间；统计页按产品展示次数，并支持确认后清除。

### 本地同步与通知

- WorkManager 唯一周期任务：15 分钟、网络约束、指数退避。
- 首次成功同步只建立基线，不产生通知事件。
- 只对同步时已订阅的产品生成新优惠/涨价/降价/失效事件。
- 稳定事件 ID + 数据库唯一约束去重；通知发送后记录 `SENT`。
- Android 13+ 通知权限申请；拒绝后事件保留 `PENDING`，恢复权限后继续。
- 通知点击进入对应产品详情；冷启动、热启动、旋转、返回栈有设备测试。
- FCM 只保留 `PushNotificationService` 边界和空实现，不需要 Firebase 配置即可构建。

### 时间与金额

- 数据库存 UTC `Instant`；展示固定为 `Asia/Shanghai`，格式 `yyyy-MM-dd HH:mm 北京时间`。
- 金额用最小货币单位整数保存（分），避免浮点误差。
- 只在产品、套餐、地区、币种、计费周期、税费口径一致时比较价格。

## 快速开始

### 环境

- JDK 17
- Android SDK Platform 35、Build-Tools 35
- Android Studio 或命令行 Android SDK
- Gradle Wrapper 8.9（仓库自带，无需本机安装 Gradle）
- 最低 Android 8.0（API 26），目标 API 35

`local.properties` 需要指向本机 SDK（该文件不提交）：

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

### 构建与安装

```bash
./gradlew assembleDebug
./gradlew installDebug
```

或直接安装已构建 APK：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aioffer.radar/.MainActivity
```

### 构建 + 单元测试 + Lint

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

### 真实官方源（可选）

本地调试需要本机安装 Chrome，并在仓库根目录启动采集器：

```bash
node tools/glm-collector/collector.mjs --serve
adb reverse tcp:8787 tcp:8787
```

然后在 App 的“设置 → 数据源”选择“官方数据”，回到首页点“同步”。Debug 构建默认连
`127.0.0.1:8787`；数据契约与限制见
[tools/glm-collector/README.md](tools/glm-collector/README.md)。设备断开 USB 后
`adb reverse` 失效，官方同步会失败并显示错误，演示数据不受影响。

远程/CI 模式（外部 heartbeat 主触发 + GitHub Actions 兜底采集并发布到 Pages）：

```bash
./gradlew assembleRelease \
  -PglmCollectorUrl=https://<owner>.github.io/<repo>/glm-coding-plan.json
```

首次配置、Pages 权限、失败语义与新鲜度规则见
[docs/CI_GLM_COLLECTOR.md](docs/CI_GLM_COLLECTOR.md)。Release 未传 property 时
endpoint 为空，官方模式会显示“未配置 GLM 官方采集地址”，不会请求错误地址。

### 设备测试

连接真机或启动模拟器，确认 `adb devices` 能看到设备：

```bash
./gradlew connectedDebugAndroidTest
```

多设备时：

```bash
ANDROID_SERIAL=<serial> ./gradlew connectedDebugAndroidTest
```

部分 ColorOS/OPPO 设备（如本次 OPD2515）在 Gradle 安装 debug APK 时会弹“继续安装/完成”确认；不点会让 `connectedDebugAndroidTest` 卡住。重装后首次启动测试 Activity 还可能弹“30 天内允许”。不要禁用 `com.oplus.appdetail` 安装向导：禁用后会退到系统安装器，对无 launcher icon 的 test APK 无法继续。

本机 ColorOS 还限制 adb `pm clear` 与 `pm revoke POST_NOTIFICATIONS`（shell 缺少 `CLEAR_APP_USER_DATA` / `REVOKE_RUNTIME_PERMISSIONS`）。需要清数据时用卸载重装或系统设置；通知权限通过 App 内“设置 → 开启通知”授予。

## 演示模式

首页“切换演示场景”提供 6 个确定性场景：

| 场景 | 行为 |
|---|---|
| 首次同步 | 恢复基线：KIMI ¥100、DEEPSEEK ¥200、GLM ¥300（均为每月演示价） |
| 新增优惠 | 为 KIMI 增加 ¥70/月的新优惠 |
| 价格下降 | KIMI 从 ¥100 降到 ¥80 |
| 价格上涨 | KIMI 从当前价涨到 ¥110 |
| 重复同步 | 返回当前状态，不制造变化 |
| 网络失败 | 抛出确定性网络异常，不改动已有数据 |

场景和 KIMI 的演示价格通过 DataStore 持久化，杀进程/重启后仍保持。

注意：

- “首次同步”会重置演示价格；如果此时已订阅 KIMI，可能被识别为一次价格变化并产生通知。
- 演示数据源的 `sourceUrl` 为空；`officialUrl` 指向 Kimi、DeepSeek、智谱的官方首页/入口页，仅用于演示跳转，不是价格来源。
- 演示历史从首次同步开始积累，不会回填任何“官网历史价”。

验证通知：

1. 在首页订阅 KIMI。
2. 授权通知（Android 13+）。
3. 选择“价格下降”。
4. 通知应只出现一次；点击进入 KIMI 详情。
5. 未订阅的 DEEPSEEK/GLM 不应收到通知。

WorkManager 不保证实时执行；演示时可用首页“同步”按钮立即触发。

## 数据可信度规则

- 内置数据全部标记为 `DataSourceType.DEMO`，首页固定显示演示横幅。
- 官方 GLM 数据标记为 `OFFICIAL_WEB`，来源为官方套餐页，带来源 URL、抓取时间、原文摘要；
  采集器失败时不展示旧价为新数据。
- 不编造官网当前价格、历史价格或优惠截止时间。
- 真实来源适配器必须保存：来源 URL、抓取时间、原文标题/摘要、生效/截止时间；任一缺失时标准化失败，不能标记为官方真实数据。
- 同一个 sourceId 的来源类型不可变；演示/官方业务 ID 双向隔离，禁止把演示快照重新标记成官方历史。
- 没有可靠历史来源时，历史只从 App 首次同步开始积累。
- 同步只追加新价格快照，不覆盖或删除旧快照。
- 学生、家庭、团队、免费、免费试用不能与标准付费套餐混在一起判历史最低。
- 季付/年付先按折合月均比较；详情页同时显示总价和月均，不能拿季/年总价直接和月付比。
- 过期优惠保留在历史区，不继续作为当前有效优惠，也不显示“去订阅”。
- 离线或同步失败时只展示缓存，并明确标注“数据可能已过期”，不能说成实时数据。

详细接入契约见 [docs/DATA_SOURCE_INTEGRATION.md](docs/DATA_SOURCE_INTEGRATION.md)。

## 架构

单 `app` Gradle 模块，按包分层，保留未来拆模块的可能。

```text
app/src/main/java/com/aioffer/radar/
├── AiOfferRadarApplication.kt     Application：创建容器、注册周期任务
├── AppContainer.kt                手动依赖注入：Room、演示/GLM 数据源、Repository、同步、通知
├── MainActivity.kt                singleTop + 通知深链消费
├── core/
│   ├── browser/                   Custom Tab / 浏览器回退
│   ├── database/                  Room Entity、DAO、数据库、迁移
│   ├── datastore/                 演示状态 + AppSettings 持久化
│   ├── model/                     产品、套餐、优惠、价格、同步、设置、通知模型
│   ├── network/                   RemoteDataSource、DTO、演示源、GLM 采集器适配器、网络状态
│   ├── notification/              通知渠道、发送、去重分发
│   ├── push/                      FCM 抽象 + 空实现
│   ├── sync/                      WorkManager Worker 与调度
│   └── time/                      ticker、固定展示时区
├── data/                          同步服务、变更检测、Repository、DTO 标准化
├── domain/                        折扣、历史最低、有效性、URL 安全校验
├── feature/
│   ├── home/                      首页、订阅、演示场景、数据来源/同步状态
│   ├── settings/                  数据源、同步偏好、通知权限状态
│   ├── detail/                    详情、筛选、折线图
│   └── statistics/                点击统计
├── navigation/                    Compose 导航与深链路由
└── ui/theme/                      Material 3 主题

tools/glm-collector/               本机/CI GLM 官方套餐页渲染采集器（Node + Chrome CDP）
.github/workflows/glm-collector.yml 定时采集、校验、发布并自证线上字节
.github/workflows/glm-snapshot-health.yml 每小时检查线上快照是否超过 2 小时
app/src/test/                       JVM 单元测试
app/src/androidTest/                Room、同步、通知、Compose、深链设备测试
app/schemas/                        Room schema 与迁移测试资产
docs/                               进度、证据、交接文档
```

数据流：

```text
AppSettingsStore (DataStore)
  -> SettingsAwareSyncService
  -> DemoRemoteDataSource / GlmCodingPlanRemoteDataSource
  -> RemotePayloadNormalizer (source URL/fetchedAt/原文/ID 前缀隔离)
  -> OfferSyncService (Mutex, Dispatchers.IO, Room transaction, source 类型不可变)
  -> products/plans/offers/sources/price_snapshots/sync_metadata
  -> LocalRadarRepository
  -> ViewModel (StateFlow, 按数据源模式过滤)
  -> Compose UI (来源/最后成功同步/失败原因/过期提示)

android: OFFICIAL 模式
  -> BuildConfig.GLM_COLLECTOR_URL
     - Debug 默认: http://127.0.0.1:8787/v1/glm-coding-plan (adb reverse)
     - CI/Release: https://<owner>.github.io/<repo>/glm-coding-plan.json
  -> tools/glm-collector (Node + headless Chrome)
  -> https://www.bigmodel.cn/glm-coding
  -> 外部 heartbeat/仓库 workflow 采集 + validate-payload.mjs + Pages 发布
  -> verify-published.mjs 重拉线上字节（HTTP/schema/2h/SHA-256）
  -> build-android 用同一 endpoint 跑 JVM + Release；失败/过期开 Issue

OfferSyncWorker -> SettingsAwareSyncService -> OfferSyncService -> NotificationDispatcher
```

核心不变量：

- `ComparisonKey(productId, planId, region, currency, billingPeriod, taxIncluded)` 全等才可比较。
- `price_snapshots` 只追加；快照 ID 包含观测时间，避免往返价格被主键去重吞掉。
- 首次成功同步只建基线；失败同步不会被视为基线。
- 通知事件 ID 稳定，数据库唯一约束兜底。
- 数据源类型不可变：同一 sourceId 不能从 DEMO 改成 OFFICIAL；演示/官方 ID 用 `demo-` 前缀双向隔离。
- 数据源切换只改变展示范围，不重写历史快照或来源类型。
- 季付/年付必须先用折合月均比较；月付、季付、年付不能直接比总价。
- 官方 GLM 源固定为 `glm-coding-plan-official` / `OFFICIAL_WEB`；采集器失败不回退旧价。
- `MainActivity.onNewIntent` 不调用 `setIntent()`：ActivityScenario 依赖原始 launch intent 匹配生命周期，改写会导致仪器测试挂起。
- 展示时区固定为 `Asia/Shanghai`；数据库仍存 UTC。

## 测试

单元测试覆盖：

- 折扣计算、现价 0 的 100% 折扣、原价为零/负价/币种不一致
- 历史最低、并列最低、空历史、跨套餐/地区/币种/周期/税费，候选价币种与比较键不一致
- 免费试用不污染付费历史
- 生效/截止/过期边界；active 查询起点边界包含、终点边界排除
- 首次同步不通知；新优惠/涨价/降价只通知一次；未订阅不通知
- 固定时区；演示状态持久化；快照往返
- 数据源设置默认值/未知值回退/新实例重载
- 演示/官方来源字段校验、`demo-` 前缀双向隔离、抓取时间必填；大写 HTTPS scheme
- 官方模式隐藏演示数据、来源类型不可变、旧同步错误随模式切换清理
- 最后成功同步/失败原因、数据库读取失败重试、通知提示持久化
- 价格展示的两个 `require` 分支（优惠价/月均折算币种不一致）
- GLM 采集器 JSON 映射（9 offers、季付总价/月均）、采集器不可达、source 不匹配、缺 URL、schema 版本、非法 JSON
- endpoint 未配置时失败关闭、采集快照超过 2 小时拒绝、抓取时间晚于当前时间拒绝、Debug BuildConfig endpoint 非空
- 季付图表使用折合月均

设备测试覆盖：

- 订阅/取消/全部订阅持久化；首页“全部订阅”和已订阅卡“取消订阅”回调
- 历史快照不被覆盖；首次同步、重复同步、价格往返、通知去重
- 点击统计与清除
- Room 查询、迁移；active 起点/终点边界；事务失败回滚；迁移保留 offer/快照/source 业务字段
- 演示数据源、固定官方 Fake 数据源、真实 GLM 采集 payload 的完整同步；demo/official 同库共存且来源类型互不影响
- WorkManager、通知权限、渠道关闭、通知去重
- 首页/详情/统计 Compose UI
- 首页数据来源/最后成功同步/失败原因/官方空态/DB 错误重试
- 设置页数据源、自动同步、通知、返回；官方已配置说明；配置变更与重新启动后的设置保持
- 主线程 StrictMode 下同步、点击记录、Room Flow、DataStore 操作
- 通知深链冷启动、热启动、旋转、返回栈
- 官网 URL 安全回退
- 首页季付 display + 月均最低选择
- 发布端点校验：HTTP 重试、过期拒绝、线上字节 SHA-256 与构建产物不一致拒绝、CLI 本地 HTTP 服务

测试证据在 [docs/PHASE12_PROGRESS.md](docs/PHASE12_PROGRESS.md)、[docs/PHASE11_PROGRESS.md](docs/PHASE11_PROGRESS.md)、[docs/PHASE10_PROGRESS.md](docs/PHASE10_PROGRESS.md)、[docs/PHASE9_PROGRESS.md](docs/PHASE9_PROGRESS.md) 与 [docs/evidence/2026-10-30/README.md](docs/evidence/2026-10-30/README.md)。

## 当前限制

- GLM 官方源优先使用 CI 定时发布的 HTTPS 快照；本地开发仍可依赖本机采集器
  （Node 24 + Chrome + `adb reverse`）。GitHub Pages 已启用；外部 heartbeat 每 15 分钟
  检查、超过 75 分钟触发采集，GitHub `schedule` 每 30 分钟只是兜底；远程快照超过
  2 小时或采集失败时 App 会明确失败并保留缓存。
- Release 未传 `-PglmCollectorUrl` 时官方 endpoint 为空，官方模式显示未配置；
  这是有意失败关闭，不会请求错误地址。
- 官方页面是 SPA，选择器改版会导致采集失败；只覆盖公开个人套餐，不覆盖团队套餐、登录优惠、
  优惠券或首月立减。App 拒绝抓取时间超过 2 小时的快照。
- 设备测试目前只在 OPD2515 / API 37 上通过 86 / 86（阶段 12）；阶段 11 基线为 84 / 84。
  API 26–36、通知渠道关闭、厂商省电策略尚未做长期 soak。
- 没有后端、账号、支付、真实订阅或价格校验。
- FCM 未接入；`NoOpPushNotificationService` 只证明边界存在。
- WorkManager 周期任务不保证实时；设备休眠/厂商策略会延迟。
- 通知 `SENT` 表示已提交给系统，不保证用户实际看到；渠道被关闭时事件保持 `PENDING`。
- Release 正式签名 CI 已闭环（`.github/workflows/release.yml` + 条件 signingConfig）：四个 Secrets 已配置，签名 APK 在 [run 41221071911](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41221071911) 构建并验签；keystore 仅在本机/Keychain，不入库。`build-android` 的 helper APK 仍用 CI 临时 key，不是正式发布包；Play 发布配置、混淆策略仍未实现。
- P1 的部署后哈希校验、Android job 与健康路径已在远端通过；P2 已实测失败 Issue 的创建/去重/恢复关闭与失败 run 置红。2026-11-01 01:36 已发生真实过期快照并被 2 小时守卫拦截，手动刷新后恢复；GitHub `schedule` 只观察到各一次自动运行，不能作为 SLA。外部 heartbeat 已真实激活（run 41330211849 / 41330761449），但本机 launchd 依赖 Mac 开机/联网，生产应放到常在线 VM/容器；GitHub `schedule` 仍只观察到少量自动运行，只能当兜底。
- 深链在“已有 saved state 且系统用全新 intent 重建 Activity”的罕见路径未覆盖。
- 同一比较分组存在多个优惠时，“当前观测”用同金额最新快照近似；演示数据没有这种组合。
- Lint 仍有 2 个 warning：`POST_NOTIFICATIONS` 常量 API 标记、未使用颜色 `radar_blue`。
- 版本管理：已初始化 git 仓库（`main`）；阶段 11 验收基线为标签 `phase11-accepted`，阶段 12 已完成并提交到 `main`（标签 `phase12-glm-source`）；阶段 12 增强（endpoint + CI）已提交到 `main`（标签 `phase12-endpoint-ci`），并推送到公开仓库 `https://github.com/yuntianming1874-design/ai-offer-radar`。

## 下一步建议

1. 把外部 heartbeat 从本机 launchd 迁到常在线 VM/容器（避免 Mac 休眠/断网），再做 API 26–36 兼容矩阵。正式签名已跑通，后续发布沿用同一 key 并递增 versionCode。
2. 扩大设备矩阵：在 API 26–36、其他 ROM、通知渠道关闭、厂商省电与长期 soak 下复跑阶段 11/12 验收。
3. 发布前：正式签名、Play 配置、WorkManager soak、真实设备长期运行、通知渠道/权限全矩阵。
4. 交接给下一位开发者时先读 [docs/HANDOFF.md](docs/HANDOFF.md)。

## 开发文档

- [交付说明](docs/DELIVERY.md)
- [交接文档](docs/HANDOFF.md)
- [CI 采集与远程 endpoint](docs/CI_GLM_COLLECTOR.md)
- [外部 heartbeat（cron/launchd）](docs/HEARTBEAT.md)
- [Release 签名与 CI 发布包](docs/RELEASE_SIGNING.md)
- [阶段 12：GLM 官方源](docs/PHASE12_PROGRESS.md)
- [GLM 采集器](tools/glm-collector/README.md)
- [阶段 11 设备验收清单](docs/PHASE11_DEVICE_ACCEPTANCE.md)
- [阶段 11 进度](docs/PHASE11_PROGRESS.md)
- [阶段 10 进度与验收](docs/PHASE10_PROGRESS.md)
- [阶段 9 进度与验收](docs/PHASE9_PROGRESS.md)
- [官方数据源接入契约](docs/DATA_SOURCE_INTEGRATION.md)
- [阶段 8 进度与验收](docs/PHASE8_PROGRESS.md)
- [阶段 6 进度](docs/PHASE6_PROGRESS.md)
- [2026-10-30 验收证据](docs/evidence/2026-10-30/README.md)
- [2026-10-31 endpoint/CI 验收证据](docs/evidence/2026-10-31/README.md)
