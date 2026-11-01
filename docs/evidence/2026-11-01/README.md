# 2026-11-01 证据

## P2.3 外部 heartbeat（本地 + 远端）

- `phase12-endpoint-ci/p2-external-heartbeat.txt`
- 结果：
  - Node 23/23（4 payload + 6 校验/CLI + 5 发布校验 + 8 heartbeat）。
  - 手动 `repository_dispatch` → run 41330211849 成功（17:11:37Z–17:16:00Z）。
  - heartbeat 脚本读 Keychain token、强制 stale → 真实 dispatch run 41330761449
    成功（17:18:02Z–17:21:07Z）。
  - macOS launchd `com.aioffer.radar.collector-heartbeat` 安装并 loaded；首次运行
    读到快照 5m，判定 fresh。
  - 线上快照 3 products / 3 plans / 9 offers；`verify-published` 通过，
    SHA-256 `728a5bc7…`，`fetchedAt=2026-11-01T17:18:29Z`。
- 未验证/限制：launchd 只在本机，Mac 休眠/断网不触发；GitHub `schedule` 仍只作兜底；
  生产需把 `heartbeat.mjs` 放到常在线主机。
- 基线：`0f4b322`；提交/推送 `ad78a3a`。

## 历史证据

- 阶段 12/P1/P2：`../2026-10-31/README.md`
- 阶段 9–12：`../2026-10-30/README.md`
