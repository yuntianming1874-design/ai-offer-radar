# 官方数据源接入契约

更新时间：2026-10-30  
适用范围：阶段 9 之后所有真实数据源适配器。

## 一句话规则

**没有来源 URL、抓取时间、原文标题/摘要和可靠生效时间的记录，不能标记为“官方真实数据”。**

演示数据永远是 `DataSourceType.DEMO`。真实适配器只能产出 `OFFICIAL_API`、`OFFICIAL_RSS` 或 `OFFICIAL_WEB`，并且必须通过 `RemotePayloadNormalizer` 的校验。没有可信来源时，正确做法是不同步或记录失败，而不是猜价格、猜截止时间或把演示快照重新贴标签。

## 当前阶段 9 的边界

- `AppSettings.dataSourceMode` 只有两个值：`DEMO` 与 `OFFICIAL`。
- `DEMO` 使用内置 `DemoRemoteDataSource`，sourceId 固定为 `demo-local`，数据全部标记 `DEMO`。
- `OFFICIAL` 当前使用 `UnconfiguredOfficialRemoteDataSource`，sourceId 为 `official-unconfigured`。它不会返回任何数据，`fetch()` 直接抛出 `RemoteDataSourceException("官方数据源适配器尚未配置…")`。
- 因此当前切到“官方数据”只会看到空态或同步失败态，不会看到实时优惠。这是有意为之，不是缺陷。
- 数据源切换只改变**首页展示范围**和当前同步使用的 sourceId，不会改写已存 `SourceInfo` 的类型、`Offer` 的 source、历史快照或同步元数据。

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
   - `LocalRadarRepository.applySync` 在同一事务内检查已有 source 类型；同一 sourceId 从 `DEMO` 改成 `OFFICIAL_*` 会抛 `IllegalStateException`，整个事务回滚。
   - `OfferSyncService` 在写库前也会检查，抛 `RemotePayloadNormalizationException`。
   - 不能通过“切换模式”或“重新同步”重写历史来源类型。
2. **演示/官方业务 ID 不相交**：
   - `DEMO` 的 source/product/plan/offer ID 必须带 `demo-` 前缀。
   - 官方数据不得使用 `demo-` 前缀。
   - 违反任一条，标准化直接失败。
3. **快照只追加**：价格历史一旦写入不覆盖、不删除；切换数据源不会隐藏或改写历史快照，只是首页按当前模式过滤展示。
4. **展示按实际来源标记**：产品卡片显示 `offer.source.type`，不是全局模式；因此即使 UI 有 bug 把两种数据放在一起，来源徽标也不会被模式“染色”。

## 接入步骤

1. 新建 `OfficialXxxRemoteDataSource : RemoteDataSource`，实现 `sourceId` 与 `fetch()`。
2. 在 `AppContainer` 中用真实适配器替换 `UnconfiguredOfficialRemoteDataSource`。
3. 设置 `officialSourceId = adapter.sourceId`、`officialAdapterConfigured = true`。
4. 保证适配器填充上表中的所有来源字段；缺字段时让 `RemotePayloadNormalizer` 抛错，不要降级成“看起来像官方”。
5. 先写失败测试：缺 URL、缺抓取时间、抓取失败、来源类型冲突、演示 ID 混入。
6. 再写成功测试：真实 payload → 标准化 → 事务 → 首页/详情/历史。
7. 在设置页和文档中更新“适配器已配置”的说明；不能提前宣称支持实时数据。

## 数据可信度与 UI 语义

- 首页 `DataSourceStatusCard` 永远显示当前模式的数据来源、最后成功同步时间和失败原因。
- 离线或同步失败但有缓存时，文案固定包含“数据可能已过期”，不会说“实时”。
- 演示模式始终显示“演示数据，非实时优惠”横幅。
- 官方模式适配器未配置时显示“官方数据（适配器未配置）”和空态说明，并提供“切回演示数据”入口。
- `lastSuccessfulSyncAt` 只在成功同步时前进；失败同步保留上一次成功时间，并单独展示失败原因。

## 验收红线

出现以下任一情况，阶段 9 视为不合格：

- 首页把演示数据称为官方数据或实时数据；
- 切换数据源后旧快照的来源类型改变；
- 官方模式同步了演示 payload，或演示模式写入了官方 sourceId；
- 无网络、空数据、数据库读取失败时出现崩溃且没有重试入口；
- 同步失败后仍显示一个更晚的“最后成功同步时间”；
- 开发者文档声称已接入真实数据源，但实际仍是 `UnconfiguredOfficialRemoteDataSource`。

## 相关文件

- `app/src/main/java/com/aioffer/radar/core/model/AppSettings.kt`
- `app/src/main/java/com/aioffer/radar/core/datastore/AppSettingsStore.kt`
- `app/src/main/java/com/aioffer/radar/core/network/UnconfiguredOfficialRemoteDataSource.kt`
- `app/src/main/java/com/aioffer/radar/data/SettingsAwareSyncService.kt`
- `app/src/main/java/com/aioffer/radar/data/RemotePayloadNormalizer.kt`
- `app/src/main/java/com/aioffer/radar/data/LocalRadarRepository.kt`
- `app/src/main/java/com/aioffer/radar/data/OfferSyncService.kt`
- `app/src/main/java/com/aioffer/radar/feature/home/HomeViewModel.kt`
- `app/src/main/java/com/aioffer/radar/feature/settings/SettingsViewModel.kt`
