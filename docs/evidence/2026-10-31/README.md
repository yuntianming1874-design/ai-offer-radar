# 阶段 12 增强：endpoint 配置化 + CI 定时采集

验证时间：2026-10-31 00:53（Asia/Shanghai）
P2 远端验证：2026-10-31 23:23–23:30（Asia/Shanghai）
P2.1 签名 Release：2026-11-01 00:43–00:46（Asia/Shanghai）
P2.2 CI artifact 真机安装 + 真实过期快照：2026-11-01 01:33–01:40（Asia/Shanghai）
最终设备复跑：2026-10-31 00:52（86 / 86，1m 8s）
设备：OPD2515 / Android 17 / API 37 / serial `<device-serial>`
基线：`6f4229a`（tag `phase12-glm-source`）

## 结果

| 项目 | 命令 | 结果 |
|---|---|---|
| clean 构建 | `./gradlew --offline --no-daemon clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` | `BUILD SUCCESSFUL`，56s，84 tasks |
| JVM 单元测试 | 同上 | 137 / 137，23 suites，0 failed / 0 errors / 0 skipped |
| Lint | 同上 | 0 errors，2 warnings |
| 设备仪器测试 | `./gradlew --offline --no-daemon connectedDebugAndroidTest` | 86 / 86，0 failed / 0 errors / 0 skipped，1m 8s（00:52 最终复跑） |
| Node 采集器测试 | `node --test tools/glm-collector/test/*.test.mjs` | 15 / 15 |
| 真实采集 | `node tools/glm-collector/collector.mjs --once` | 3 tabs / 9 offers / 12s |
| payload 校验 | `node tools/glm-collector/validate-payload.mjs ... --max-age-seconds 1800` | 通过（3 档 / 9 offers） |
| Release（无 endpoint） | `./gradlew --offline --no-daemon assembleRelease` | `BUILD SUCCESSFUL`；`GLM_COLLECTOR_URL=""` |
| Release（注入 endpoint） | `./gradlew --offline --no-daemon assembleRelease -PglmCollectorUrl=https://example.com/glm-coding-plan.json` | `BUILD SUCCESSFUL`；BuildConfig 注入该 URL |
| Workflow 语法 | Ruby Psych 解析 `.github/workflows/glm-collector.yml` | 通过；1 job / 9 steps |
| 远程 CI | GitHub Actions / Pages | **通过**（2026-10-31 15:27，run 41180172626 重跑成功） |
| 线上 JSON | `curl https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json` | HTTP 200；3 products / 3 plans / 9 offers；`validate-payload.mjs` 通过 |

## 远程发布

- 仓库已改为 public（免费账号 Pages 不支持 private）；代码与证据已脱敏。
- Pages 以 `build_type=workflow` 启用；本次为首次成功发布。
- Workflow：<https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41180172626>
- 快照：<https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json>
- 发布后本地校验通过；后续每 30 分钟自动更新，失败时保留上一版快照，客户端 2 小时后拒绝。

## 关键实现

- endpoint 由 `BuildConfig.GLM_COLLECTOR_URL` 构建时注入；Debug 默认本机，
  Release 未注入时为空并失败关闭。
- 客户端拒绝超过 2 小时的快照，也拒绝晚于当前时间 5 分钟的时间戳。
- `.github/workflows/glm-collector.yml` 每 30 分钟采集、校验并发布到 GitHub Pages。
- 采集/校验失败时 job 失败，不发布半成品；客户端仍显示失败原因与缓存。

## 文件

- `unit-test-results/`：23 个 JVM XML
- `connected-test-results.xml`：86 项设备测试
- `lint-results-debug.txt` / `.html`
- `collector-payload.json`、`payload-validation.txt`
- `node-tests.txt`、`workflow-check.txt`
- `release-buildconfig-empty.java` / `release-buildconfig-injected.java`
- `remote-deployment.txt`：公开仓库 Pages 首次成功发布、run URL、HTTP 200 与线上 JSON 校验
- `p1-node-tests.txt`：P1 后 Node 15 / 15
- `p1-live-verification.txt`：线上 JSON 发布校验（3 档 / 9 offers、SHA-256）
- `p1-workflow-check.txt`：两个 workflow 的 job 结构
- `p1-remote-run.txt`：P1 远端首跑 run URL、job 耗时、发布哈希、BuildConfig 与 helper APK SHA-256
- `p2-alert-drill.txt`：失败阈值演练、Issue #1 创建/去重/恢复关闭、失败 run 置红
- `p2-release-workflow.txt`：unsigned Release 远端构建、APK 哈希/版本、缺密钥失败关闭
- `p2-signed-release.txt`：正式签名 Release 远端 run、artifact SHA-256、v2 证书指纹、版本/endpoint 与本地构建对比
- `p2-ci-install-and-real-stale.txt`：CI artifact 真机安装哈希、真实过期快照失败文案、手动刷新恢复、schedule 缺失观察

## P1 CI 自证（远端首跑通过，2026-10-31 16:02）

| 项目 | 证据 | 结果 |
|---|---|---|
| `collect-and-publish` | [run 41182662449](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41182662449) | 成功，34s；部署后 `verify-published` 第 1 次通过，3 档 / 9 offers，SHA-256 `3357c7cd…` |
| `build-android` | 同一 run | 成功，3m 4s；Gradle `BUILD SUCCESSFUL in 2m 29s`；BuildConfig 含线上 URL；helper APK `8783870e…` |
| `close-recovered-issue` | 同一 run | 成功；无遗留失败 Issue |
| `notify-failure` | 同一 run | 跳过（健康运行） |
| 快照健康检查 | [run 41182665177](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41182665177) | `check-freshness` 成功，7s |

详细记录：`p1-remote-run.txt`。

## P1 CI 自证（本地验证，2026-10-31 15:48）

| 项目 | 命令/证据 | 结果 |
|---|---|---|
| 发布端点校验 | `node tools/glm-collector/verify-published.mjs <线上 JSON> --max-age-seconds 7200 --attempts 2` | 第 1 次通过；3 档 / 9 offers；SHA-256 `5e7aebdc…` |
| Node 全量测试 | `node --test tools/glm-collector/test/*.test.mjs` | **15 / 15** 通过 |
| Android Release job 等价构建 | `testDebugUnitTest assembleRelease -PglmCollectorUrl=<线上 URL>` | `BUILD SUCCESSFUL`，33s / 76 tasks；JVM 137 / 137；BuildConfig 含线上 URL |
| helper APK 签名 | 临时 keytool + apksigner（本地模拟 CI 步骤） | 签名成功 |
| Workflow 结构 | Ruby Psych 解析 | `glm-collector.yml`：`collect-and-publish` / `build-android` / `notify-failure` / `close-recovered-issue`；`glm-snapshot-health.yml`：`check-freshness` |

P1 本地证据文件：`p1-node-tests.txt`、`p1-live-verification.txt`、`p1-workflow-check.txt`。

## P2 失败告警演练与 Release 签名骨架（2026-10-31 23:23–23:30）

### 失败告警演练（Issue #1）

| 场景 | Run | 结果 |
|---|---|---|
| 阈值 1s，首次失败 | [41214658712](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41214658712) | 校验失败，创建 [Issue #1](https://github.com/yuntianming1874-design/ai-offer-radar/issues/1)；该 run 在“失败置红”修复前，run 仍为绿色 |
| 阈值 1s，重复失败 | [41214743795](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41214743795) | 复用 Issue，0 条新评论，updatedAt 不变；最终 step 使 run 失败（红） |
| 默认 7200，恢复 | [41214800226](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41214800226) | 成功；Issue 收到恢复评论并关闭（CLOSED，1 条评论） |

结论：Issue 创建、1 小时去重、恢复关闭、失败 run 置红均已实测；这是手动阈值演练，不是真实 stale 事故。

### Release workflow

| 场景 | Run | 结果 |
|---|---|---|
| `sign_release=false` | [41214833322](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41214833322) | 成功 2m22s；artifact `ai-offer-radar-release`；APK `ai-offer-radar-1.0.1-ci-unsigned.apk` 8.0MB，SHA-256 `cf32651a…`；versionCode 2 / versionName 1.0.1-ci；apksigner `DOES NOT VERIFY`（符合 unsigned） |
| `sign_release=true`，未配置 Secrets | [41215119074](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41215119074) | 22s 失败；逐项报四个 `RELEASE_*` Secret 未配置；未静默产出 unsigned 包 |

正式签名路径已配置四个 Secrets（keystore 由维护者本机保管）；本仓库不保存密钥。

### 正式签名 Release（2026-11-01 00:43–00:46）

| 项目 | Run / 证据 | 结果 |
|---|---|---|
| `sign_release=true` | [41221071911](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41221071911) | 成功 1m59s；`testDebugUnitTest assembleRelease` 通过（1m20s / 77 tasks）；artifact `ai-offer-radar-release` |
| 签名 APK | 下载 artifact | `ai-offer-radar-1.0.0-signed.apk`，8,585,258 bytes；SHA-256 `b08b6208691f567cfec88ff4a555dd57319c13676509816fb94e88ab420aaf27` |
| 签名验证 | `apksigner verify --print-certs` | v2 通过；证书 SHA-256 `54f6ee32cb7a7d28ed89e5ea131f1e134904b4eeb97dad7b02f238260d19979e`；RSA 4096 |
| 版本/endpoint | `aapt2 dump badging` + dex strings | versionCode 1 / versionName 1.0.0；BuildConfig 含线上 endpoint |
| 真机安装 | CI artifact 安装到 PMX110 / API 37 | 安装成功；设备内 APK SHA-256 与 artifact 完全一致；官方模式同步成功 |

结论：正式签名 CI 路径已闭环。CI 与本地 APK 哈希不同但证书一致；不要用哈希不同判断签名不一致。

### CI artifact 真机安装 + 真实过期事故（2026-11-01 01:33–01:40）

| 项目 | 结果 |
|---|---|
| CI artifact 安装 | `adb install -r` 成功；设备内 APK SHA-256 `b08b6208…` 与 artifact 完全一致；证书 `54f6ee32…`；versionCode 1 / 1.0.0 |
| 真实过期快照 | 快照 `fetchedAtEpochMillis=1793460710610`（15:31:50Z）；01:36 同步显示“采集数据已过期…已超过 120 分钟”；缓存仍展示、无崩溃、未冒充实时 |
| 手动恢复 | [run 41225390770](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41225390770) 36s 成功；新快照 `fetchedAtEpochMillis=1793468246705`（17:37:26Z）；01:40 同步成功并清除失败原因 |

### 定时采集观察

- 最后自动 `schedule` 收集：2026-10-31T13:31:47Z（run 41205967508）。
- health 自动 `schedule`：仅 2026-10-31T14:32:08Z（run 41210572896）。
- 16:01 / 16:31 / 17:01 / 17:31Z 收集 schedule 与 15:32 / 16:32 / 17:32Z health schedule 均未出现；workflow 状态为 active，手动 dispatch 正常。
- 17:36Z 手动触发收集 run 41225390770 恢复快照。
- 结论：GitHub `schedule` 目前不能提供 30 分钟/小时级 SLA；2 小时客户端新鲜度守卫正确拦截了过期快照，但生产运行需要外部 cron/heartbeat。不要把 GitHub schedule 写成保证。

详细记录：`p2-alert-drill.txt`、`p2-release-workflow.txt`、`p2-ci-install-and-real-stale.txt`。

## 未验证

- **schedule 可靠性未解决**：真实过期事故已发生并被 UI 正确拦截；GitHub schedule 只观察到各一次自动运行，不能作为 SLA。
- **CI artifact 已真机安装**：安装哈希与 artifact 完全一致；API 26–36/其他 ROM 仍未验证。
- API 26–36、其他 ROM、通知渠道/省电 soak。
