# CI 定时采集与远程 endpoint

本文说明 GLM Coding Plan 官方源如何从“开发机本地采集器”升级为
“CI 定时采集 + HTTPS 静态 JSON”，以及 CI 如何自证发布物可用。

## 数据流

```text
GitHub Actions（每 30 分钟）
  └─ headless Chrome 访问 https://www.bigmodel.cn/glm-coding
       └─ 解析 3 档 × 3 周期 = 9 个 offer
            └─ validate-payload.mjs 校验来源、金额、完整性、新鲜度
                 └─ 计算 payload SHA-256
                      └─ GitHub Pages: /glm-coding-plan.json
                           └─ verify-published.mjs 拉取线上字节
                                ├─ HTTP 200 + JSON/schema/2h 新鲜度
                                ├─ SHA-256 必须等于本次 CI 产物
                                └─ 通过后才算部署成功
                                     └─ Android build job 用该 endpoint 构建
                                          └─ App 官方模式 GET endpoint
                                               └─ OfferSyncService → Room
```

本地开发仍可运行 `collector.mjs --serve`，Debug 构建默认指向
`http://127.0.0.1:8787/v1/glm-coding-plan`，通过 `adb reverse` 访问。

## 工作流

### `glm-collector.yml`

文件：`.github/workflows/glm-collector.yml`

- 触发：`schedule` 每 30 分钟 + `workflow_dispatch`
- 权限：`contents: read`、`pages: write`、`id-token: write`；告警 job 额外
  `issues: write`
- job：

| job | 作用 |
|---|---|
| `collect-and-publish` | Node 24 → Chrome → 采集器/校验器测试 → 真实采集 → payload 校验 → 计算 SHA-256 → Pages 发布 → 拉取线上 URL 做 HTTP/新鲜度/字节哈希校验 |
| `build-android` | 用上一步的线上 endpoint 跑 `testDebugUnitTest assembleRelease`；核对 BuildConfig；用一次性 CI key 签 helper APK；手动触发时上传 artifact |
| `notify-failure` | 失败时自动开/更新 Issue `GLM collector CI failed`；同一 Issue 1 小时内不重复评论 |
| `close-recovered-issue` | 下一次成功运行自动评论并关闭该 Issue |

发布失败时不会覆盖上一次成功的 JSON。注意：Pages 的“部署成功”不等于
“线上已经是新字节”，所以 `collect-and-publish` 会在 deploy 之后再次拉取
公网 URL，只有 HTTP 200、schema 合法、未超过 2 小时且 SHA-256 与本次产物
完全一致，job 才变绿。

### `glm-snapshot-health.yml`

文件：`.github/workflows/glm-snapshot-health.yml`

- 每小时第 15 分钟 + 手动触发。
- 独立于采集 workflow：如果 GitHub scheduled run 停止、采集 job 长期失败或
  Pages 一直返回旧文件，健康检查会以 2 小时为阈值失败。
- 失败时开/更新 Issue `GLM snapshot is stale (>2h)`；恢复后自动关闭。
- 这样即使“最后一次成功超过 2 小时”而没有新的失败 run，也会被发现。

## 为什么需要线上字节哈希

只检查 HTTP 200 会漏掉：

- Pages 仍在上一次部署（CDN/部署传播延迟）；
- 自定义 404 页面返回 200；
- 错误分支写入了不完整 JSON；
- 采集成功但部署 artifact 不是刚校验的那一份。

因此 workflow 记录 `sha256sum public/glm-coding-plan.json`，部署后由
`verify-published.mjs` 重新计算线上响应体哈希，不一致就重试；默认
12 次 × 5 秒，仍不一致则 job 失败并触发 Issue。

## 首次配置

1. 将仓库推送到 GitHub（免费账号的 Pages 仅对公开仓库开放；本项目已改为 public）。
2. **Settings → Pages → Build and deployment → Source** 选择 **GitHub Actions**。
3. 手动执行一次 workflow，确认 `Deploy to GitHub Pages` 与
   `Verify the deployed bytes and freshness` 都成功。
4. endpoint 为：

   ```text
   https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json
   ```

5. 构建 App 时注入：

   ```bash
   ./gradlew assembleRelease \
     -PglmCollectorUrl=https://<owner>.github.io/<repo>/glm-coding-plan.json
   ```

   Debug 构建可继续使用本机默认地址；也可以用同一个 property 指向远端。

## 为什么 Release 默认空 endpoint

`app/build.gradle.kts` 在未传 `-PglmCollectorUrl` 时：

- Debug：默认 `http://127.0.0.1:8787/v1/glm-coding-plan`
- Release：空字符串

Release 为空时，`AppContainer` 将 `officialAdapterConfigured` 设为 `false`，
官方模式显示“适配器未配置”，同步会以“未配置 GLM 官方采集地址”失败。
这样不会在发布包里悄悄请求一个错误地址，也不会把演示数据误标为官方数据。

## 新鲜度与失败语义

- CI 每 30 分钟采集；App 端最大允许 2 小时。
- `GlmCodingPlanRemoteDataSource` 校验 `fetchedAtEpochMillis`：
  - 超过 2 小时：拒绝，错误“采集数据已过期”；
  - 晚于当前时间 5 分钟：拒绝，错误“抓取时间异常”。
- 本地 `--serve` 失败返回 503，不回退旧缓存。
- CI 校验失败时 job 失败，Pages 保持上一次成功版本；App 会在 2 小时后拒绝它。
- 官方同步失败时首页显示失败原因与最后成功同步时间，缓存产品仍可查看。
- `build-android` 的 helper APK 只用于 CI/真机冒烟，不进入任何发布渠道；
  它不是 release 签名，不能当作正式安装包。

## 安全与合规

- 只采集官方公开套餐页，不登录、不绕过权限、不读取账号数据。
- 不采集团队套餐、优惠券、登录后价格。
- 只把个人套餐价格发布为静态 JSON；来源 URL、抓取时间、原文摘要随每条数据保存。
- 不自动抓取微博/小红书；B 站等社媒内容只能作为未验证线索，不能进入价格库。
- Pages 发布的是公开价格快照，不包含任何凭据；workflow 不写入仓库内容。
- `verify-published.mjs` 只做 GET，不写远端；Issue 告警只写 issue，不改代码。

## 本地验证

```bash
# 采集器/校验器/发布校验测试（15 项）
node --test tools/glm-collector/test/*.test.mjs

# 模拟 CI 发布物
mkdir -p /tmp/glm-public
node tools/glm-collector/collector.mjs --once > /tmp/glm-public/glm-coding-plan.json
node tools/glm-collector/validate-payload.mjs \
  /tmp/glm-public/glm-coding-plan.json --max-age-seconds 1800

# 直接验证线上 Pages（HTTP/schema/新鲜度/可选哈希）
node tools/glm-collector/verify-published.mjs \
  https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json \
  --max-age-seconds 7200 --attempts 2

# Android 单元测试（含 endpoint 空值/过期校验）
./gradlew testDebugUnitTest

# 构建时注入 endpoint
./gradlew assembleDebug -PglmCollectorUrl=https://example.com/glm-coding-plan.json
```

2026-10-31 15:48 本地结果：

- `node --test tools/glm-collector/test/*.test.mjs`：**15 / 15** 通过
  （4 payload + 6 校验/CLI + 5 发布校验）；
- 线上 endpoint `verify-published.mjs` 通过：3 档 / 9 个 offer，
  SHA-256 `5e7aebdc528862abdd2dfc6341f10cd1d7da3276b680efb6c17a1feff3a5ac03`；
- `testDebugUnitTest assembleRelease -PglmCollectorUrl=线上地址`：
  `BUILD SUCCESSFUL`，33s / 76 tasks；JVM **137 / 137（23 suites）**；
  Release BuildConfig 含线上 URL；
- CI helper 签名脚本本地模拟通过：`apksigner verify` 成功；
- 两个 workflow YAML 均通过 Ruby Psych 解析。

已执行的远端记录：

- 2026-10-31 15:26–15:27：run [41180172626](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41180172626)
  重跑成功，checkout、Node、Chrome、测试、采集、校验、Configure Pages、
  上传 artifact、Deploy Pages 全部通过；线上快照 HTTP 200，
  `validate-payload.mjs` 校验 3 products / 3 plans / 9 offers。
- P1（部署后哈希校验 + Android build job + Issue 告警）的远端首跑记录见
  `docs/evidence/2026-10-31/phase12-endpoint-ci/`。

## 已知限制

- GitHub 的 `schedule` 可能延迟；30 分钟是期望值，不是 SLA。
- GitHub Pages 需要仓库为 public 或付费计划；免费账号对 private 仓库会返回
  “Your current plan does not support GitHub Pages for this repository”。
  本项目已改为 public，代码与证据已脱敏。
- 官方页面 CSS 选择器变化会导致采集失败；workflow 会失败并触发 Issue，
  错误同时留在 Actions 日志。
- 只覆盖个人套餐；团队套餐、优惠券、登录价不在本切片内。
- 采集器需要 Chrome；CI 使用 `browser-actions/setup-chrome`，本地需要安装 Chrome。
- P1 的 Android job 产出的是 **CI 临时 key 签名的 helper APK**；正式签名、
  Play 发布、设备矩阵仍不在本切片。
- Issue 告警依赖 `issues: write` 和仓库 Issue 功能开启；工作流本身不会发邮件/Slack。
