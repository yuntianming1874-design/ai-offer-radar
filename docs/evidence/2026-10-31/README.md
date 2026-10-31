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
| Node 采集器测试 | `node --test tools/glm-collector/test/*.test.mjs` | 10 / 10 |
| 真实采集 | `node tools/glm-collector/collector.mjs --once` | 3 tabs / 9 offers / 12s |
| payload 校验 | `node tools/glm-collector/validate-payload.mjs ... --max-age-seconds 1800` | 通过（3 档 / 9 offers） |
| Release（无 endpoint） | `./gradlew --offline --no-daemon assembleRelease` | `BUILD SUCCESSFUL`；`GLM_COLLECTOR_URL=""` |
| Release（注入 endpoint） | `./gradlew --offline --no-daemon assembleRelease -PglmCollectorUrl=https://example.com/glm-coding-plan.json` | `BUILD SUCCESSFUL`；BuildConfig 注入该 URL |
| Workflow 语法 | Ruby Psych 解析 `.github/workflows/glm-collector.yml` | 通过；1 job / 9 steps |
| 远程 CI | GitHub Actions / Pages | **未验证**（远程仓库与 Pages 尚未配置） |

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

## 未验证

- GitHub 远程仓库、Pages、定时 schedule 真实执行（无远程仓库）。
- Release 签名、应用发布 CI。
- API 26–36、其他 ROM、通知渠道/省电 soak。
