# 阶段 12 增强：endpoint 配置化 + CI 定时采集

验证时间：2026-10-31 00:53（Asia/Shanghai）
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
| Node 采集器测试 | `node --test tools/glm-collector/test/*.test.mjs` | 10 / 10（P1 后 15 / 15） |
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

## 未验证

- **失败告警注入未执行**：P1 远端健康运行已验证部署后哈希、Android job、恢复关闭 job 与 health workflow；`notify-failure` 的失败建 Issue 路径尚未通过故意失败演练。
- 定时 `schedule` 的下一次自动触发（此前为手动 workflow_dispatch；页面与发布链路已真实执行）。
- Release 签名、应用发布 CI。
- API 26–36、其他 ROM、通知渠道/省电 soak。
