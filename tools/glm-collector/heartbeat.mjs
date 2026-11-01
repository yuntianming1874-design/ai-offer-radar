#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";

export const DEFAULT_REPO = "yuntianming1874-design/ai-offer-radar";
export const DEFAULT_SNAPSHOT_URL =
  "https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json";
export const DEFAULT_WORKFLOW_FILE = "glm-collector.yml";
export const DEFAULT_THRESHOLD_MINUTES = 75;
export const DEFAULT_KEYCHAIN_SERVICE = "ai-offer-radar-heartbeat";
export const DEFAULT_KEYCHAIN_ACCOUNT = "github-token";
export const HEARTBEAT_EVENT_TYPE = "glm-heartbeat";

const RUNNING_STATUSES = new Set([
  "queued",
  "in_progress",
  "requested",
  "waiting",
  "pending",
]);

/**
 * Decide whether a collector run is needed.
 *
 * The Android client rejects snapshots older than 2 hours. The heartbeat fires
 * at 75 minutes so there is still time for a collector run, a Pages deployment,
 * and one retry before the hard 2-hour freshness guard.
 */
export function evaluateHeartbeat({
  nowEpochMillis,
  fetchedAtEpochMillis,
  thresholdMinutes = DEFAULT_THRESHOLD_MINUTES,
  latestRunStatus = null,
}) {
  if (!Number.isFinite(fetchedAtEpochMillis) || fetchedAtEpochMillis <= 0) {
    throw new Error("snapshot fetchedAtEpochMillis is missing or invalid");
  }
  if (!Number.isFinite(nowEpochMillis)) {
    throw new Error("nowEpochMillis is missing or invalid");
  }
  if (!Number.isFinite(thresholdMinutes) || thresholdMinutes <= 0) {
    throw new Error("thresholdMinutes must be a positive number");
  }

  const ageMinutes = Math.max(
    0,
    Math.floor((nowEpochMillis - fetchedAtEpochMillis) / 60_000),
  );

  if (ageMinutes < thresholdMinutes) {
    return { action: "fresh", ageMinutes };
  }
  if (latestRunStatus && RUNNING_STATUSES.has(latestRunStatus)) {
    return { action: "wait", ageMinutes, latestRunStatus };
  }
  return { action: "dispatch", ageMinutes, latestRunStatus };
}

function logLine(message) {
  process.stdout.write(`[${new Date().toISOString()}] ${message}\n`);
}

function keychainToken() {
  try {
    return execFileSync(
      "/usr/bin/security",
      [
        "find-generic-password",
        "-s",
        DEFAULT_KEYCHAIN_SERVICE,
        "-a",
        DEFAULT_KEYCHAIN_ACCOUNT,
        "-w",
      ],
      { encoding: "utf8" },
    ).trim();
  } catch {
    throw new Error(
      `GitHub token not found. Set GITHUB_TOKEN or store it with: security add-generic-password -U -s ${DEFAULT_KEYCHAIN_SERVICE} -a ${DEFAULT_KEYCHAIN_ACCOUNT} -w '<token>'`,
    );
  }
}

async function fetchWithTimeout(fetchImpl, url, options, timeoutMs) {
  const response = await fetchImpl(url, {
    ...options,
    signal: AbortSignal.timeout(timeoutMs),
  });
  return response;
}

export async function fetchSnapshotAge({
  snapshotUrl = DEFAULT_SNAPSHOT_URL,
  fetchImpl = globalThis.fetch,
  nowEpochMillis = Date.now(),
  timeoutMs = 15_000,
  log = logLine,
} = {}) {
  const url = new URL(snapshotUrl);
  url.searchParams.set("heartbeat", String(nowEpochMillis));
  let response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      url,
      {
        headers: {
          accept: "application/json",
          "cache-control": "no-cache",
          pragma: "no-cache",
        },
      },
      timeoutMs,
    );
  } catch (error) {
    log(`snapshot fetch failed: ${error.message}`);
    return { ageMinutes: Infinity, fetchedAtEpochMillis: null };
  }
  if (!response.ok) {
    log(`snapshot fetch failed: HTTP ${response.status}`);
    return { ageMinutes: Infinity, fetchedAtEpochMillis: null };
  }
  let payload;
  try {
    payload = await response.json();
  } catch (error) {
    log(`snapshot JSON parse failed: ${error.message}`);
    return { ageMinutes: Infinity, fetchedAtEpochMillis: null };
  }
  const fetchedAtEpochMillis = payload?.source?.fetchedAtEpochMillis;
  if (!Number.isFinite(fetchedAtEpochMillis)) {
    log("snapshot source.fetchedAtEpochMillis is missing");
    return { ageMinutes: Infinity, fetchedAtEpochMillis: null };
  }
  const ageMinutes = Math.max(
    0,
    Math.floor((nowEpochMillis - fetchedAtEpochMillis) / 60_000),
  );
  log(
    `snapshot fetchedAt=${new Date(fetchedAtEpochMillis).toISOString()} age=${ageMinutes}m`,
  );
  return { ageMinutes, fetchedAtEpochMillis };
}

export async function fetchLatestRunStatus({
  repo = DEFAULT_REPO,
  workflowFile = DEFAULT_WORKFLOW_FILE,
  token,
  fetchImpl = globalThis.fetch,
  apiBaseUrl = "https://api.github.com",
  timeoutMs = 15_000,
} = {}) {
  if (!token) {
    throw new Error("GitHub token is required to inspect workflow runs");
  }
  const url = `${apiBaseUrl}/repos/${repo}/actions/workflows/${workflowFile}/runs?per_page=1`;
  const response = await fetchWithTimeout(
    fetchImpl,
    url,
    {
      headers: {
        accept: "application/vnd.github+json",
        authorization: `Bearer ${token}`,
        "x-github-api-version": "2022-11-28",
      },
    },
    timeoutMs,
  );
  if (!response.ok) {
    throw new Error(
      `cannot inspect collector runs: HTTP ${response.status} ${response.statusText}`,
    );
  }
  const body = await response.json();
  return body?.workflow_runs?.[0]?.status ?? null;
}

export async function dispatchCollector({
  repo = DEFAULT_REPO,
  token,
  fetchImpl = globalThis.fetch,
  apiBaseUrl = "https://api.github.com",
  timeoutMs = 15_000,
} = {}) {
  if (!token) {
    throw new Error("GitHub token is required to dispatch the collector");
  }
  const response = await fetchWithTimeout(
    fetchImpl,
    `${apiBaseUrl}/repos/${repo}/dispatches`,
    {
      method: "POST",
      headers: {
        accept: "application/vnd.github+json",
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
        "x-github-api-version": "2022-11-28",
      },
      body: JSON.stringify({ event_type: HEARTBEAT_EVENT_TYPE }),
    },
    timeoutMs,
  );
  if (response.status !== 204) {
    const detail = await response.text().catch(() => "");
    throw new Error(
      `collector dispatch failed: HTTP ${response.status} ${detail.slice(0, 200)}`,
    );
  }
}

export async function runHeartbeat({
  repo = DEFAULT_REPO,
  snapshotUrl = DEFAULT_SNAPSHOT_URL,
  workflowFile = DEFAULT_WORKFLOW_FILE,
  thresholdMinutes = DEFAULT_THRESHOLD_MINUTES,
  token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN,
  dryRun = false,
  fetchImpl = globalThis.fetch,
  nowEpochMillis = Date.now(),
  apiBaseUrl = "https://api.github.com",
  log = logLine,
} = {}) {
  if (!Number.isFinite(thresholdMinutes) || thresholdMinutes <= 0) {
    throw new Error("thresholdMinutes must be a positive number");
  }
  const snapshot = await fetchSnapshotAge({
    snapshotUrl,
    fetchImpl,
    nowEpochMillis,
    log,
  });

  if (snapshot.ageMinutes !== Infinity && snapshot.ageMinutes < thresholdMinutes) {
    log(`heartbeat: fresh (${snapshot.ageMinutes}m < ${thresholdMinutes}m)`);
    return { action: "fresh", ageMinutes: snapshot.ageMinutes, dispatched: false };
  }

  let latestRunStatus = null;
  let effectiveAge = snapshot.ageMinutes;
  if (snapshot.ageMinutes !== Infinity) {
    const authToken = token || keychainToken();
    latestRunStatus = await fetchLatestRunStatus({
      repo,
      workflowFile,
      token: authToken,
      fetchImpl,
      apiBaseUrl,
    });
    const decision = evaluateHeartbeat({
      nowEpochMillis,
      fetchedAtEpochMillis: snapshot.fetchedAtEpochMillis,
      thresholdMinutes,
      latestRunStatus,
    });
    if (decision.action === "fresh") {
      log(`heartbeat: fresh (${decision.ageMinutes}m < ${thresholdMinutes}m)`);
      return { ...decision, dispatched: false };
    }
    if (decision.action === "wait") {
      log(
        `heartbeat: stale (${decision.ageMinutes}m) but latest run is ${decision.latestRunStatus}; waiting`,
      );
      return { ...decision, dispatched: false };
    }
    log(`heartbeat: stale (${decision.ageMinutes}m >= ${thresholdMinutes}m)`);
    if (dryRun) {
      log("heartbeat: dry-run, not dispatching");
      return { ...decision, dispatched: false, dryRun: true };
    }
    await dispatchCollector({
      repo,
      token: authToken,
      fetchImpl,
      apiBaseUrl,
    });
    log(`heartbeat: dispatched ${HEARTBEAT_EVENT_TYPE} to ${repo}`);
    return { ...decision, dispatched: true };
  }

  // A missing or unreadable snapshot is treated as stale. This lets the
  // heartbeat recover from a deployment outage as well as from old bytes.
  effectiveAge = Infinity;
  const authToken = token || keychainToken();
  latestRunStatus = await fetchLatestRunStatus({
    repo,
    workflowFile,
    token: authToken,
    fetchImpl,
    apiBaseUrl,
  });
  if (latestRunStatus && RUNNING_STATUSES.has(latestRunStatus)) {
    log(`heartbeat: snapshot unreadable but latest run is ${latestRunStatus}; waiting`);
    return { action: "wait", ageMinutes: effectiveAge, latestRunStatus, dispatched: false };
  }
  if (dryRun) {
    log("heartbeat: snapshot unreadable, dry-run, not dispatching");
    return { action: "dispatch", ageMinutes: effectiveAge, dispatched: false, dryRun: true };
  }
  await dispatchCollector({ repo, token: authToken, fetchImpl, apiBaseUrl });
  log(`heartbeat: snapshot unreadable; dispatched ${HEARTBEAT_EVENT_TYPE} to ${repo}`);
  return { action: "dispatch", ageMinutes: effectiveAge, dispatched: true };
}

function parseArgs(argv) {
  const options = {
    repo: process.env.HEARTBEAT_REPO || DEFAULT_REPO,
    snapshotUrl: process.env.HEARTBEAT_SNAPSHOT_URL || DEFAULT_SNAPSHOT_URL,
    workflowFile: process.env.HEARTBEAT_WORKFLOW || DEFAULT_WORKFLOW_FILE,
    thresholdMinutes: Number(
      process.env.HEARTBEAT_THRESHOLD_MINUTES || DEFAULT_THRESHOLD_MINUTES,
    ),
    apiBaseUrl: process.env.HEARTBEAT_API_BASE_URL || "https://api.github.com",
    dryRun: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const next = () => {
      index += 1;
      if (index >= argv.length) throw new Error(`${arg} requires a value`);
      return argv[index];
    };
    switch (arg) {
      case "--dry-run":
        options.dryRun = true;
        break;
      case "--threshold-minutes":
        options.thresholdMinutes = Number(next());
        break;
      case "--snapshot-url":
        options.snapshotUrl = next();
        break;
      case "--repo":
        options.repo = next();
        break;
      case "--workflow":
        options.workflowFile = next();
        break;
      case "--api-base-url":
        options.apiBaseUrl = next();
        break;
      case "--help":
        options.help = true;
        break;
      default:
        throw new Error(`unknown argument: ${arg}`);
    }
  }
  return options;
}

function usage() {
  return [
    "Usage: node tools/glm-collector/heartbeat.mjs [options]",
    "",
    "Options:",
    `  --threshold-minutes <n>  Dispatch when the snapshot is older than n minutes (default ${DEFAULT_THRESHOLD_MINUTES}).`,
    "  --snapshot-url <url>     Published JSON URL.",
    "  --repo <owner/repo>      GitHub repository.",
    "  --workflow <file>        Collector workflow file name.",
    "  --api-base-url <url>     GitHub API base URL (default https://api.github.com).",
    "  --dry-run                Log the decision without dispatching.",
    "  --help                   Show this help.",
    "",
    "Token: GITHUB_TOKEN/GH_TOKEN, or macOS Keychain service",
    `       ${DEFAULT_KEYCHAIN_SERVICE} account ${DEFAULT_KEYCHAIN_ACCOUNT}.`,
  ].join("\n");
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    process.stdout.write(`${usage()}\n`);
    return 0;
  }
  const result = await runHeartbeat(options);
  if (result.dryRun) return 0;
  return result.dispatched || result.action === "fresh" || result.action === "wait" ? 0 : 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main()
    .then((code) => {
      process.exitCode = code;
    })
    .catch((error) => {
      console.error(`heartbeat failed: ${error.message}`);
      process.exitCode = 1;
    });
}
