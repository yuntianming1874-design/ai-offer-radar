# 外部采集心跳（heartbeat）

## 为什么需要它

GitHub Actions 的 `schedule` 是 best-effort：它可能排队、延迟，甚至跳过。
2026-11-01 的线上快照曾停留在 `15:31:50Z`，超过 App 的 2 小时新鲜度阈值；
GitHub 只在 `13:31Z` 和 `14:32Z` 各跑过一次自动 schedule，无法提供 30 分钟级 SLA。

现在的分层是：

1. **外部 heartbeat（主）**：每 15 分钟检查线上快照；超过 75 分钟没有刷新时，
   通过 GitHub API 发送 `repository_dispatch`（event type `glm-heartbeat`）。
2. **GitHub Actions `schedule`（兜底）**：保留每 30 分钟一次；它不再是唯一触发源。
3. **健康检查 workflow（告警兜底）**：每小时检查快照是否超过 2 小时；失败开 Issue，
   恢复后关闭。
4. **Android 客户端（最后防线）**：拒绝超过 2 小时的快照，显示失败原因并保留缓存。

75 分钟阈值给采集、Pages 部署和一次重试留出约 45 分钟，避免等到 2 小时守卫
才恢复。heartbeat 本身每 15 分钟运行一次，最坏情况下约 90 分钟触发刷新。

当前状态（2026-11-02 01:21）：macOS launchd `com.aioffer.radar.collector-heartbeat`
已安装并 loaded；`repository_dispatch` 真实运行 run 41330211849 成功；
heartbeat 脚本从 Keychain 读取 token 后真实触发 run 41330761449 成功。局限：
它只在这台 Mac 上，休眠/断网时不会触发，生产应迁到常在线主机。
安全说明：当前 Keychain 中存的是 `gh` 设备流 token（scopes `repo`/`workflow`），
权限比 heartbeat 所需更宽；生产建议换只授权本仓库的 fine-grained PAT
（Contents read/write + Actions read），并用 `security add-generic-password -U`
覆盖。

## 数据流

```text
launchd / 任意外部 cron（每 15 分钟）
  -> GET Pages 快照，读 source.fetchedAtEpochMillis
  -> 年龄 < 75 分钟：退出
  -> 查询 glm-collector.yml 最近一次 run
       queued / in_progress / requested / waiting / pending：退出，避免重复触发
  -> POST /repos/<owner>/<repo>/dispatches
       {"event_type":"glm-heartbeat"}
  -> glm-collector.yml (repository_dispatch)
       -> collect -> validate -> Pages deploy -> verify published bytes
       -> build-android
       -> failure issue / recovery issue
```

`glm-collector.yml` 同时接受：

- `schedule`（兜底）
- `repository_dispatch`，类型 `glm-heartbeat`（外部 heartbeat 主路径）
- `workflow_dispatch`（人工）

`concurrency` 使用 `cancel-in-progress: false`，避免外部触发把正在部署的 run 取消。

## macOS：安装 launchd heartbeat

实现文件：

- `tools/glm-collector/heartbeat.mjs`：判定与 dispatch 逻辑，支持 `--dry-run`
- `tools/glm-collector/install-heartbeat.macos.sh`：安装/卸载/查看 launchd agent
- Keychain service：`ai-offer-radar-heartbeat`，account：`github-token`

### 1. 准备 token

建议使用只授权给本仓库的 fine-grained PAT：

- Repository access：仅 `yuntianming1874-design/ai-offer-radar`
- Contents：Read and write（发送 repository dispatch 需要）
- Actions：Read（查询最近一次 workflow run）
- 如果使用 classic token，需要 `repo`（私有仓库）或 `public_repo`（公开仓库）；
  仅公开仓库时 `public_repo` 即可。不要把 token 写进仓库、plist、shell 历史或 URL。

存入登录钥匙串：

```bash
security add-generic-password -U \
  -s ai-offer-radar-heartbeat \
  -a github-token \
  -w '<fine-grained-token>'
```

如果希望避免首次读取时的钥匙串授权弹窗，可在创建时显式信任
`/usr/bin/security`：

```bash
security add-generic-password -U \
  -T /usr/bin/security \
  -s ai-offer-radar-heartbeat \
  -a github-token \
  -w '<fine-grained-token>'
```

### 2. 安装

```bash
bash tools/glm-collector/install-heartbeat.macos.sh install
```

安装器会：

- 校验 Node.js 20+；
- 确认 Keychain 中已有 token（不打印 token）；
- 写入 `~/Library/LaunchAgents/com.aioffer.radar.collector-heartbeat.plist`；
- 用 `launchctl bootstrap` 加载并立即 kickstart；
- 日志写入 `~/Library/Logs/ai-offer-radar/heartbeat.log`；
- 默认每 900 秒（15 分钟）运行一次，阈值 75 分钟。

可用环境变量覆盖：

```bash
HEARTBEAT_INTERVAL_SECONDS=900 \
HEARTBEAT_THRESHOLD_MINUTES=75 \
  bash tools/glm-collector/install-heartbeat.macos.sh install
```

### 3. 查看 / 卸载

```bash
bash tools/glm-collector/install-heartbeat.macos.sh status
tail -n 20 "$HOME/Library/Logs/ai-offer-radar/heartbeat.log"
bash tools/glm-collector/install-heartbeat.macos.sh uninstall
```

## 任意外部 cron / 容器

`heartbeat.mjs` 不依赖 launchd；只要环境有 Node.js 20+ 和网络即可。把
`GITHUB_TOKEN` 注入环境，然后每 10–15 分钟运行：

```bash
GITHUB_TOKEN='<fine-grained-token>' \
  node tools/glm-collector/heartbeat.mjs
```

可选参数/环境变量：

| 参数 | 环境变量 | 默认 |
|---|---|---|
| `--snapshot-url` | `HEARTBEAT_SNAPSHOT_URL` | Pages 上的 `glm-coding-plan.json` |
| `--repo` | `HEARTBEAT_REPO` | `yuntianming1874-design/ai-offer-radar` |
| `--workflow` | `HEARTBEAT_WORKFLOW` | `glm-collector.yml` |
| `--threshold-minutes` | `HEARTBEAT_THRESHOLD_MINUTES` | `75` |
| `--api-base-url` | `HEARTBEAT_API_BASE_URL` | `https://api.github.com` |
| `--dry-run` | — | 只打印判定，不 dispatch |

`--dry-run` 在快照新鲜时不需要 token；快照过期时会查询最近 run，因此需要 token。

手动 dispatch（不使用脚本）等价于：

```bash
curl -fsS -X POST \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/yuntianming1874-design/ai-offer-radar/dispatches \
  -d '{"event_type":"glm-heartbeat"}'
```

成功返回 `204 No Content`。不要把 token 放进 URL 查询参数；日志/代理可能记录 URL。

## 验证

```bash
# 1. 判定逻辑与脚本单测
node --test tools/glm-collector/test/*.test.mjs

# 2. 看当前快照年龄（不需要 token）
node tools/glm-collector/heartbeat.mjs --dry-run

# 3. 强制演练：阈值 1 分钟，确认会 dispatch（需要 token）
GITHUB_TOKEN=... node tools/glm-collector/heartbeat.mjs \
  --dry-run --threshold-minutes 1

# 4. 查看外部触发的 run
gh run list --workflow glm-collector.yml --event repository_dispatch

# 5. 确认 launchd 已加载
launchctl print "gui/$(id -u)/com.aioffer.radar.collector-heartbeat"
```

验收标准：

- 快照新鲜时 heartbeat 不调用 dispatch；
- 快照超过阈值且没有进行中的 run 时，创建一次 `repository_dispatch`；
- 有 run 处于 `queued`/`in_progress` 时不重复触发；
- 快照不可读时保守触发；
- 触发后 collector 成功、部署后校验通过，快照年龄回到阈值以下；
- launchd 日志能看到上述判定；
- GitHub schedule 仍是兜底，不被当作 SLA。

## 局限与运维

- **本机 launchd 依赖 Mac 开机、联网、用户登录**。它不是生产级外部调度；
  合盖睡眠、断网或用户登出时不会运行。生产环境应把同一脚本部署到常在线
  VM/容器或托管 cron，并把 `GITHUB_TOKEN` 放在 secret manager 中。
- GitHub 的 `schedule` 仍可能延迟；它只作为兜底，不是主路径。
- 健康 workflow 也是 GitHub schedule；如果 GitHub 整体调度异常，最终仍由
  Android 客户端的 2 小时守卫拦截，不会把旧数据当成实时数据。
- token 需要轮换；撤销 token 后 launchd 的下一次运行会失败并写日志。
- 不要把 token 写进 launchd plist、仓库、截图或 Issue；日志只记录状态，不记录
  Authorization 头。
