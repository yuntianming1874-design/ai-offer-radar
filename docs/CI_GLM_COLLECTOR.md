# CI 定时采集与远程 endpoint

本文说明 GLM Coding Plan 官方源如何从“开发机本地采集器”升级为
“CI 定时采集 + HTTPS 静态 JSON”，以及 Android 侧如何注入 endpoint。

## 数据流

```text
GitHub Actions（每 30 分钟）
  └─ headless Chrome 访问 https://www.bigmodel.cn/glm-coding
       └─ 解析 3 档 × 3 周期 = 9 个 offer
            └─ validate-payload.mjs 校验来源、金额、完整性、新鲜度
                 └─ GitHub Pages: /glm-coding-plan.json
                      └─ Android 官方模式 GET endpoint
                           └─ OfferSyncService → Room（sourceType=OFFICIAL_WEB）
```

本地开发仍可运行 `collector.mjs --serve`，Debug 构建默认指向
`http://127.0.0.1:8787/v1/glm-coding-plan`，通过 `adb reverse` 访问。

## 工作流

文件：`.github/workflows/glm-collector.yml`

- 触发：`schedule` 每 30 分钟 + `workflow_dispatch`
- 权限：`contents: read`、`pages: write`、`id-token: write`
- 步骤：Node 24 → Chrome → 采集器测试 → 真实采集 → payload 校验 → Pages 发布
- 发布失败时不会覆盖上一次成功的 JSON；App 在抓取时间超过 2 小时后拒绝该
  payload，官方同步失败但保留缓存，并显示失败原因。

## 首次配置

1. 将仓库推送到 GitHub 私有仓库。
2. **Settings → Pages → Build and deployment → Source** 选择 **GitHub Actions**。
3. 手动执行一次 workflow，确认 `Deploy to GitHub Pages` 成功。
4. endpoint 为：

   ```text
   https://<owner>.github.io/<repo>/glm-coding-plan.json
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

## 安全与合规

- 只采集官方公开套餐页，不登录、不绕过权限、不读取账号数据。
- 不采集团队套餐、优惠券、登录后价格。
- 只把个人套餐价格发布为静态 JSON；来源 URL、抓取时间、原文摘要随每条数据保存。
- 不自动抓取微博/小红书；B 站等社媒内容只能作为未验证线索，不能进入价格库。
- Pages 发布的是公开价格快照，不包含任何凭据；workflow 不写入仓库内容。

## 本地验证

```bash
# 采集器/校验器测试
node --test tools/glm-collector/test/*.test.mjs

# 模拟 CI 发布物
mkdir -p /tmp/glm-public
node tools/glm-collector/collector.mjs --once > /tmp/glm-public/glm-coding-plan.json
node tools/glm-collector/validate-payload.mjs \
  /tmp/glm-public/glm-coding-plan.json --max-age-seconds 1800

# Android 单元测试（含 endpoint 空值/过期校验）
./gradlew testDebugUnitTest

# 构建时注入 endpoint
./gradlew assembleDebug -PglmCollectorUrl=https://example.com/glm-coding-plan.json
```

2026-10-31 00:53 本地复跑结果：

- `node --test tools/glm-collector/test/*.test.mjs`：**10 / 10** 通过；
- 真实采集：3 tabs / 9 offers / 12s；payload 校验通过；
- JVM：**137 / 137（23 suites）**；`testDebugUnitTest` 通过；
- 设备：`connectedDebugAndroidTest` **86 / 86**（OPD2515 / API 37，1m 8s）；
- Release 无 property：`GLM_COLLECTOR_URL=""`；注入 property：BuildConfig 为注入 URL，两次 `assembleRelease` 均通过；
- workflow YAML 经 Ruby Psych 解析通过（1 job / 9 steps），index 生成脚本本地模拟成功；
- **未在 GitHub 远端执行**：远程仓库与 Pages 尚未配置。

## 已知限制

- GitHub 的 `schedule` 可能延迟；30 分钟是期望值，不是 SLA。
- GitHub Pages 对私有仓库的可见性取决于仓库/Pages 配置；App 能访问即可。
- 官方页面 CSS 选择器变化会导致采集失败；workflow 会失败并把错误留在 Actions 日志。
- 只覆盖个人套餐；团队套餐、优惠券、登录价不在本切片内。
- 采集器需要 Chrome；CI 使用 `browser-actions/setup-chrome`，本地需要安装 Chrome。
