# GLM Coding Plan 采集器

这是第一条真实官方数据源的**本机/CI 渲染采集器**。智谱官方套餐页
<https://www.bigmodel.cn/glm-coding> 是 Vue SPA，价格只在浏览器执行 JavaScript
后出现，匿名 HTTP 接口不返回套餐价格。采集器用 headless Chrome 打开公开页面，
读取 Lite / Pro / Max 三个档位在“连续包月 / 连续包季 / 连续包年”三个 tab 下的
价格，输出与 App 的 `RemoteSyncPayload` 同构的 JSON。

## 数据边界

- 只访问**官方公开套餐页**，不登录、不绕过任何权限、不读取账号信息。
- 只采集个人套餐；团队套餐不在本切片范围内。
- 采集器只监听 `127.0.0.1`，不要绑定 `0.0.0.0`，也不要暴露到公网。
- 每条价格都带 `sourceUrl`、`fetchedAtEpochMillis`、原文摘要、`officialUrl`。
- 页面上没有折扣时，`originalAmountMinor == actualAmountMinor`，`offerType=NONE`；
  有划线价时 `offerType=PROMOTION`。季付/年付的 `actualAmountMinor` 是这一期的
  **总价**，`monthlyEquivalentMinor` 是页面标注的折合月价。
- 采集失败返回 503，**不会回退到旧价格**；App 会显示失败原因，而不是把旧数据
  描述成实时数据。

## 环境要求

- Node.js **24+**（使用内置全局 `WebSocket`，无 npm 依赖）
- 本机安装 Chrome 或 Chromium
- 设备通过 USB 连接，`adb` 可用

## 运行

```bash
# 只采集一次，JSON 打印到 stdout
node tools/glm-collector/collector.mjs --once

# 启动本地 HTTP 服务（默认 127.0.0.1:8787）
node tools/glm-collector/collector.mjs --serve

# 自定义端口 / Chrome 路径
node tools/glm-collector/collector.mjs --serve --port 8788 \
  --chrome "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
```

服务接口：

- `GET /v1/glm-coding-plan`：返回采集 JSON；缓存 5 分钟
- `GET /health`：返回 `ok`、最近成功/失败信息、缓存抓取时间

让 Android 设备访问本机采集器：

```bash
adb reverse tcp:8787 tcp:8787
```

然后在 App 的“设置 → 数据源”选择“官方数据”，回到首页手动同步。
如果使用模拟器，采集器地址可用 `http://10.0.2.2:8787/v1/glm-coding-plan`；
默认地址在 Debug 构建中是 `http://127.0.0.1:8787/v1/glm-coding-plan`。

## 远程模式：CI 定时采集 + 静态 JSON

生产/验收不需要开发机一直开着。GitHub Actions 的 `schedule` 只作为兜底；
主触发是外部 heartbeat（`heartbeat.mjs`，每 15 分钟检查快照，超过 75 分钟时
通过 `repository_dispatch` 触发）。`.github/workflows/glm-collector.yml` 会：

1. 在 Ubuntu runner 上安装 Node 24 和 Chrome；
2. 先跑采集器测试；
3. 用 headless Chrome 采集官方套餐页；
4. 用 `validate-payload.mjs` 校验 3 档 × 3 周期、来源、金额和新鲜度；
5. 把 JSON 作为 GitHub Pages artifact 发布；
6. 部署后用 `verify-published.mjs` 重拉公网 JSON，要求字节 SHA-256 等于本次
   artifact、HTTP 200、schema 合法且在 2 小时内；
7. `build-android` 用同一个 endpoint 跑 JVM 测试和 Release 构建；
8. 失败/2h 过期自动开 GitHub Issue；`glm-snapshot-health.yml` 每小时独立检查新鲜度。

外部 heartbeat：

```bash
# 快照新鲜时不需要 token；过期时会查询最近 run，因此需要 token
node tools/glm-collector/heartbeat.mjs --dry-run

# macOS 安装 launchd（token 先存 Keychain）
bash tools/glm-collector/install-heartbeat.macos.sh install

# 任意外部 cron：把 GITHUB_TOKEN 注入环境，每 10–15 分钟运行
GITHUB_TOKEN=... node tools/glm-collector/heartbeat.mjs
```

token 权限、Keychain、launchd 状态/卸载和托管 cron 说明见
[docs/HEARTBEAT.md](../../docs/HEARTBEAT.md)。

2026-11-02 01:11–01:21 已真实验证：`repository_dispatch` run 41330211849 成功；
`heartbeat.mjs --threshold-minutes 1` 读取 Keychain token 后真实触发
run 41330761449 成功；launchd `com.aioffer.radar.collector-heartbeat` 已安装并
loaded，首次运行判定 fresh。

首次使用前：

1. 把仓库推到 GitHub（免费账号的 Pages 需仓库为 public）；
2. 在仓库 **Settings → Pages → Build and deployment** 选择 **GitHub Actions**；
3. 手动运行一次 `GLM Coding Plan collector` workflow（`workflow_dispatch`）。

当前已部署的 endpoint：

```text
https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json
```

Android 构建时注入：

```bash
./gradlew assembleDebug \
  -PglmCollectorUrl=https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json

./gradlew assembleRelease \
  -PglmCollectorUrl=https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json
```

- Debug 未传 property 时默认连本机 `127.0.0.1:8787`。
- Release 未传 property 时 endpoint 为空，官方模式会明确显示“未配置 GLM 官方采集地址”，
  不会悄悄请求错误地址。
- 远程 endpoint 必须是 HTTPS；`network_security_config` 只对 localhost/10.0.2.2
  放行明文。
- App 会拒绝抓取时间超过 **2 小时**的 payload；外部 heartbeat 在 75 分钟时触发，
  GitHub schedule 每 30 分钟只是兜底；失败时不会覆盖上一次已发布的 JSON。超时后
  官方同步会失败并保留缓存，而不是把旧数据描述成实时。

可在仓库 **Actions → GLM Coding Plan collector** 查看每次采集结果；Pages
的 `index.html` 会显示本次抓取时间和来源链接。

## 校验

```bash
# CI 使用：校验 JSON 是否完整、来源可信、抓取时间在有效窗口内
node tools/glm-collector/validate-payload.mjs \
  public/glm-coding-plan.json --max-age-seconds 1800

# 校验线上 Pages 的字节、schema 与新鲜度（可加 --expected-sha256）
node tools/glm-collector/verify-published.mjs \
  https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json \
  --max-age-seconds 7200 --attempts 2

# 单独看校验器测试
node --test tools/glm-collector/test/validate.test.mjs
node --test tools/glm-collector/test/verify-published.test.mjs
```

## JSON 契约

顶层：

```json
{
  "schemaVersion": 1,
  "source": {},
  "products": [],
  "plans": [],
  "offers": []
}
```

`source` 必须包含：

- `id`: `glm-coding-plan-official`
- `sourceType`: `OFFICIAL_WEB`
- `sourceUrl`: `https://www.bigmodel.cn/glm-coding`
- `titleOrSummary`: 官方页面原文摘要
- `fetchedAtEpochMillis`: 本次采集时间

每个 offer 必须包含 `productId`、`planId`、`planType`、`offerType`、`title`、
`discountDescription`、`regionCode=CN`、`currencyCode=CNY`、`billingPeriod`
（`MONTHLY` / `QUARTERLY` / `YEARLY`）、`taxIncluded`、`originalAmountMinor`、
`actualAmountMinor`、`monthlyEquivalentMinor`、`startsAtEpochMillis`、
`endsAtEpochMillis`（可为 null）、`updatedAtEpochMillis`、`source`、`officialUrl`。
金额均为人民币分。

输出示例：

```json
{
  "schemaVersion": 1,
  "source": {
    "id": "glm-coding-plan-official",
    "sourceType": "OFFICIAL_WEB",
    "sourceUrl": "https://www.bigmodel.cn/glm-coding",
    "titleOrSummary": "GLM Coding Plan 官方套餐页；抓取文本：...",
    "fetchedAtEpochMillis": 1793374402239
  },
  "products": [
    {
      "id": "glm-coding-plan-official-lite",
      "name": "GLM Coding Plan Lite",
      "logoUrl": null,
      "isDemo": false
    }
  ],
  "offers": [
    {
      "id": "glm-coding-plan-official-lite-quarterly",
      "billingPeriod": "QUARTERLY",
      "originalAmountMinor": 35400,
      "actualAmountMinor": 28320,
      "monthlyEquivalentMinor": 9440,
      "...": "其余字段见 payload.mjs"
    }
  ]
}
```

## 测试

```bash
node --test tools/glm-collector/test/*.test.mjs
```

测试覆盖金额解析、3 档 × 3 周期 = 9 个 offer、季/年总价与月均、缺失 tab/档位、
当前价高于原价、来源/来源 ID 不匹配、重复 ID、过期/未来抓取时间、
`validate-payload.mjs` 的失败关闭行为，以及发布端点的 HTTP 重试、过期拒绝、
线上字节 SHA-256 不一致拒绝，以及 heartbeat 的新鲜/等待/dispatch/不可读/dry-run
判定。当前 **23 / 23** 通过（4 payload + 6 校验/CLI + 5 发布校验 + 8 heartbeat）。

## 已知限制

- 依赖官方页面的 CSS 选择器 `.cp-card` / `.switch-tab-item`；页面改版会导致采集失败，
  失败时返回清晰错误，而不是旧价格。
- 月付 tab 的页面不显示划线原价，采集器按“原价=现价”处理。
- `taxIncluded` 固定为 `true`（页面为含税展示价；如官方页面明确不含税，需要改代码）。
- 只覆盖个人套餐，不含团队套餐、优惠券、首月立减等登录后价格。
- 页面文案/价格随时可能变化；每次采集都以 `fetchedAtEpochMillis` 和原文摘要留痕。
- 本地运行需要 Chrome；CI 使用 `browser-actions/setup-chrome`。Android 设备无法直接渲染该 SPA，Debug 通过本机采集器或 `adb reverse` 获取 JSON。
- CI 的 Android job 用一次性 key 签 helper APK，只用于冒烟/下载，不是正式 release 签名。
- 发布校验依赖 GitHub Pages 公网可达；它验证的是“刚部署的字节”，不是长期可用性 SLA。
- 外部 heartbeat 的 macOS launchd 依赖本机开机、联网、用户登录；生产应把
  `heartbeat.mjs` 放到常在线 VM/容器或托管 cron。GitHub `schedule` 仍可能延迟，
  不能当 SLA。
