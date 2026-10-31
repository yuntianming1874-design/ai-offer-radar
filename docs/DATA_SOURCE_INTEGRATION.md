# 官方数据源接入契约

更新时间：2026-10-30
适用范围：阶段 9 之后的所有真实数据源适配器；阶段 12 已接入第一条 GLM 官方来源。

## 一句话规则

**没有来源 URL、抓取时间、原文标题/摘要和可靠生效时间的记录，不能标记为“官方真实数据”。**

演示数据永远是 `DataSourceType.DEMO`。真实适配器只能产出 `OFFICIAL_API`、`OFFICIAL_RSS` 或
`OFFICIAL_WEB`，并且必须通过 `RemotePayloadNormalizer` 的校验。没有可信来源时，正确做法是
不同步或记录失败，而不是猜价格、猜截止时间或把演示快照重新贴标签。

## 当前真实源：GLM Coding Plan

阶段 12 接入的官方来源是智谱 GLM Coding Plan 的公开套餐页：

```text
https://www.bigmodel.cn/glm-coding
```

该页面是 Vue SPA，价格只在浏览器执行 JavaScript 后出现。当前实现不是让 Android
直接抓页面，而是由渲染采集器读取页面，再把同构 JSON 给 App：

```text
Android OFFICIAL 模式
  -> BuildConfig.GLM_COLLECTOR_URL
     - Debug: http://127.0.0.1:8787/v1/glm-coding-plan (adb reverse)
     - Release: https://<owner>.github.io/<repo>/glm-coding-plan.json
  -> tools/glm-collector（Node.js + headless Chrome，本机或 GitHub Actions）
  -> https://www.bigmodel.cn/glm-coding
```

- sourceId：`glm-coding-plan-official`
- sourceType：`OFFICIAL_WEB`
- sourceUrl：官方套餐页
- 数据：Lite / Pro / Max × 月/季/年 = 9 个 offer
- 季/年 `actualAmountMinor` 是总价；`monthlyEquivalentMinor` 是折合月价
- 采集失败返回 503，**不回退旧价**
- Debug 仅本机监听，设备通过 `adb reverse tcp:8787 tcp:8787` 访问
- Release/CI 使用 HTTPS 静态快照，endpoint 由 `-PglmCollectorUrl` 注入；未注入时失败关闭
- App 拒绝抓取时间超过 **2 小时**的快照，也拒绝晚于当前时间 5 分钟的时间戳

采集器运行、JSON 契约和已知限制见
[`tools/glm-collector/README.md`](../tools/glm-collector/README.md)；
CI 与 Pages 配置见 [`CI_GLM_COLLECTOR.md`](CI_GLM_COLLECTOR.md)。

### 这不是生产数据服务

Debug 路径依赖开发机、Chrome 和 USB/adb；CI/Pages 路径是定时静态快照，仍不是具备
SLA、鉴权、横向扩展的后端。在拿到官方开放 API 或书面授权前，不要把它部署成公共服务。
当前保留这个纵向切片，是为了证明“来源可追溯、价格可同步、历史可追加”的完整链路。

## 适配器必须提供什么

`RemoteDataSource.fetch()` 返回的 `RemoteSyncPayload.source` 至少包含：

| 字段 | 要求 |
|---|---|
| `id` | 稳定、全局唯一、不与演示 ID 冲突；不得以 `demo-` 开头 |
| `sourceType` | `OFFICIAL_API` / `OFFICIAL_RSS` / `OFFICIAL_WEB` |
| `sourceUrl` | 真实抓取页或接口 URL，非空；只允许可信来源 |
| `titleOrSummary` | 原文标题或摘要，非空，不能是程序生成的占位文案 |
| `fetchedAtEpochMillis` | 实际抓取时间，必须大于 0；不能用“当前时间”伪装旧数据 |

每个 `RemoteOfferDto` 还必须提供：

- 稳定的官方 offer ID，不能以 `demo-` 开头；
- 非空 `title`（原文标题/优惠名）；
- 可靠的 `startsAt` / `endsAt`（没有截止时间可留空，但不能编造）；
- 实际金额、原价、币种、计费周期、地区、税费口径；
- 能追溯的来源（`source` 或 `officialUrl`）；
- 真实 `updatedAt`。

抓取失败时抛异常，不要把上一次缓存包装成“新抓取”。

## 不可变约束

1. **source ID 与类型不可变**：
   - `LocalRadarRepository.applySync` 在同一事务内检查已有 source 类型；同一 sourceId 从
     `DEMO` 改成 `OFFICIAL_*` 会抛 `IllegalStateException`，整个事务回滚。
   - `OfferSyncService` 在写库前也会检查，抛 `RemotePayloadNormalizationException`。
   - 不能通过“切换模式”或“重新同步”重写历史来源类型。
2. **演示/官方业务 ID 不相交**：
   - `DEMO` 的 source/product/plan/offer ID 必须带 `demo-` 前缀。
   - 官方数据不得使用 `demo-` 前缀。
   - 违反任一条，标准化直接失败。
3. **快照只追加**：价格历史一旦写入不覆盖、不删除；切换数据源不会隐藏或改写历史快照，
   只是首页按当前模式过滤展示。
4. **展示按实际来源标记**：产品卡片显示 `offer.source.type`，不是全局模式；因此即使 UI 有
   bug 把两种数据放在一起，来源徽标也不会被模式“染色”。

## 接入步骤

1. 新建 `OfficialXxxRemoteDataSource : RemoteDataSource`，实现 `sourceId` 与 `fetch()`。
2. 在 `AppContainer` 中用真实适配器替换官方槽；设置 `officialSourceId = adapter.sourceId`、
   `officialAdapterConfigured = true`。
3. 保证适配器填充上表中的所有来源字段；缺字段时让 `RemotePayloadNormalizer` 抛错，
   不要降级成“看起来像官方”。
4. 先写失败测试：缺 URL、缺抓取时间、抓取失败、来源类型冲突、演示 ID 混入。
5. 再写成功测试：真实 payload → 标准化 → 事务 → 首页/详情/历史。
6. 在设置页和文档中更新“适配器已配置”的说明；不能提前宣称支持实时数据。

## 数据可信度与 UI 语义

- 首页 `DataSourceStatusCard` 永远显示当前模式的数据来源、最后成功同步时间和失败原因。
- 离线或同步失败但有缓存时，文案固定包含“数据可能已过期”，不会说“实时”。
- 演示模式始终显示“演示数据，非实时优惠”横幅。
- 官方模式显示“官方数据”和采集器/同步状态；endpoint 未配置、Debug 采集器未启动、
  `adb reverse` 断开、CI 快照过期或返回错误时，显示真实失败原因和“切回演示数据”入口，
  不回退旧价。
- `lastSuccessfulSyncAt` 只在成功同步时前进；失败同步保留上一次成功时间，并单独展示失败原因。
- 首页选折合月价最低的 offer 展示总价；详情页按周期分别展示总价和月均。季/年付不能直接
  和月付总价比较。

## 社媒与博主线索

微博、小红书 robots 对通用爬虫 `Disallow: /`，不做自动抓取。B 站等平台即使 robots 允许，
内容也多为视频/动态，价格需要 OCR/ASR，且可能包含登录后或过期信息。博主内容是**未验证
线索**，不是官方价格。

建议分层：

- **L1 官方价格**：官方套餐页/官方文档/授权 API → 可进入价格库，可触发通知。
- **L2 官方社媒**：官方账号公告 → 进入线索池，必须回链 L1 核验后才能入库。
- **L3 博主/社区**：只作为发现入口，展示“未验证线索”，不进入价格库、不触发价格通知。

任何 L2/L3 线索都必须保留原始链接、抓取时间、原文摘要和“未验证”标识；不能覆盖 L1 的
`OFFICIAL_*` 来源类型。

## 验收红线

出现以下任一情况，真实源接入视为不合格：

- 首页把演示数据称为官方数据或实时数据；
- 切换数据源后旧快照的来源类型改变；
- 官方模式同步了演示 payload，或演示模式写入了官方 sourceId；
- 无网络、空数据、数据库读取失败时出现崩溃且没有重试入口；
- 同步失败后仍显示一个更晚的“最后成功同步时间”；
- 采集器失败后回退旧价格，却把旧价格说成本次/实时价格；
- 开发者文档声称已接入真实数据源，但实际没有可复核的 sourceUrl / fetchedAt / 原文信息。

## 相关文件

- `app/src/main/java/com/aioffer/radar/core/model/AppSettings.kt`
- `app/src/main/java/com/aioffer/radar/core/datastore/AppSettingsStore.kt`
- `app/src/main/java/com/aioffer/radar/core/network/GlmCodingPlanRemoteDataSource.kt`
- `app/src/main/java/com/aioffer/radar/data/SettingsAwareSyncService.kt`
- `app/src/main/java/com/aioffer/radar/data/RemotePayloadNormalizer.kt`
- `app/src/main/java/com/aioffer/radar/data/LocalRadarRepository.kt`
- `app/src/main/java/com/aioffer/radar/data/OfferSyncService.kt`
- `app/src/main/java/com/aioffer/radar/feature/home/HomeViewModel.kt`
- `app/src/main/java/com/aioffer/radar/feature/settings/SettingsViewModel.kt`
- `tools/glm-collector/`
