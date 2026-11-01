# 2026-11-01 证据

## P2.3 外部 heartbeat（本地）

- `phase12-endpoint-ci/p2-external-heartbeat.txt`
- 结果：Node 23/23；线上快照 70m 判定 fresh；本地 mock API 90m 正确发送
  `glm-heartbeat` dispatch；`glm-collector.yml` 已支持
  `repository_dispatch`/`schedule`/`workflow_dispatch`；launchd 安装器语法通过、
  尚未安装。
- **未验证**：真实 GitHub `repository_dispatch`、launchd 安装/日志、远端
  `repository_dispatch` run、heartbeat 激活后的 schedule 兜底观察。
- 原因：GitHub 设备码授权被用户选择“稍后”，当前无可用 `GITHUB_TOKEN`。
- 基线：`0f4b322`；本目录记录的是心跳代码本地验证，不等于远端已激活。

## 历史证据

- 阶段 12/P1/P2：`../2026-10-31/README.md`
- 阶段 9–12：`../2026-10-30/README.md`
