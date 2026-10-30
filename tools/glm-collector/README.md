# GLM Coding Plan 采集器

这是第一条真实官方数据源的**本机渲染采集器**。智谱官方套餐页
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
如果使用模拟器，采集器地址可用 `http://10.0.2.2:8787/v1/glm-coding-plan`，
但当前 `BuildConfig` 未暴露配置项；默认走 `adb reverse`。

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
node --test tools/glm-collector/test/payload.test.mjs
```

测试覆盖金额解析、3 档 × 3 周期 = 9 个 offer、季/年总价与月均、缺失 tab/档位、
当前价高于原价等失败路径。

## 已知限制

- 依赖官方页面的 CSS 选择器 `.cp-card` / `.switch-tab-item`；页面改版会导致采集失败，
  失败时返回清晰错误，而不是旧价格。
- 月付 tab 的页面不显示划线原价，采集器按“原价=现价”处理。
- `taxIncluded` 固定为 `true`（页面为含税展示价；如官方页面明确不含税，需要改代码）。
- 只覆盖个人套餐，不含团队套餐、优惠券、首月立减等登录后价格。
- 页面文案/价格随时可能变化；每次采集都以 `fetchedAtEpochMillis` 和原文摘要留痕。
- 需要本机 Chrome；Android 设备无法直接渲染该 SPA，因此采集器必须运行在开发机。
