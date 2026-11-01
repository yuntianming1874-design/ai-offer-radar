import assert from "node:assert/strict";
import test from "node:test";

import {
  evaluateHeartbeat,
  runHeartbeat,
} from "../heartbeat.mjs";

const NOW = 1_793_500_000_000;
const MINUTE = 60_000;

function snapshotResponse(fetchedAtEpochMillis) {
  return {
    ok: true,
    status: 200,
    async json() {
      return { source: { fetchedAtEpochMillis } };
    },
    async text() {
      return "";
    },
  };
}

function runResponse(status) {
  return {
    ok: true,
    status: 200,
    async json() {
      return { workflow_runs: status ? [{ status }] : [] };
    },
    async text() {
      return "";
    },
  };
}

function dispatchResponse(status = 204) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return {};
    },
    async text() {
      return "";
    },
  };
}

function fakeFetch({ snapshot, latestRunStatus = null, dispatchStatus = 204 }) {
  const calls = [];
  const fetchImpl = async (url, options = {}) => {
    calls.push({ url: String(url), options });
    if (String(url).includes("glm-coding-plan.json")) {
      return snapshotResponse(snapshot);
    }
    if (String(url).includes("/actions/workflows/")) {
      return runResponse(latestRunStatus);
    }
    if (String(url).includes("/dispatches")) {
      return dispatchResponse(dispatchStatus);
    }
    throw new Error(`unexpected fetch: ${url}`);
  };
  return { calls, fetchImpl };
}

test("evaluateHeartbeat returns fresh below the threshold", () => {
  const result = evaluateHeartbeat({
    nowEpochMillis: NOW,
    fetchedAtEpochMillis: NOW - 60 * MINUTE,
    thresholdMinutes: 75,
  });
  assert.equal(result.action, "fresh");
  assert.equal(result.ageMinutes, 60);
});

test("evaluateHeartbeat returns dispatch when stale and idle", () => {
  const result = evaluateHeartbeat({
    nowEpochMillis: NOW,
    fetchedAtEpochMillis: NOW - 90 * MINUTE,
    thresholdMinutes: 75,
    latestRunStatus: "completed",
  });
  assert.equal(result.action, "dispatch");
  assert.equal(result.ageMinutes, 90);
});

test("evaluateHeartbeat waits for an in-progress collector run", () => {
  const result = evaluateHeartbeat({
    nowEpochMillis: NOW,
    fetchedAtEpochMillis: NOW - 90 * MINUTE,
    thresholdMinutes: 75,
    latestRunStatus: "in_progress",
  });
  assert.equal(result.action, "wait");
  assert.equal(result.latestRunStatus, "in_progress");
});

test("runHeartbeat does not require a token while the snapshot is fresh", async () => {
  const { calls, fetchImpl } = fakeFetch({ snapshot: NOW - 30 * MINUTE });
  const logs = [];
  const result = await runHeartbeat({
    token: "",
    nowEpochMillis: NOW,
    thresholdMinutes: 75,
    fetchImpl,
    snapshotUrl: "https://example.test/glm-coding-plan.json",
    log: (line) => logs.push(line),
  });
  assert.equal(result.action, "fresh");
  assert.equal(calls.length, 1);
  assert.match(logs.join("\n"), /fresh/);
});

test("runHeartbeat dispatches a stale snapshot when no run is active", async () => {
  const snapshot = NOW - 90 * MINUTE;
  const { calls, fetchImpl } = fakeFetch({
    snapshot,
    latestRunStatus: "completed",
  });
  const logs = [];
  const result = await runHeartbeat({
    token: "test-token",
    nowEpochMillis: NOW,
    thresholdMinutes: 75,
    fetchImpl,
    snapshotUrl: "https://example.test/glm-coding-plan.json",
    log: (line) => logs.push(line),
  });
  assert.equal(result.action, "dispatch");
  assert.equal(result.dispatched, true);
  const dispatch = calls.find((call) => call.url.endsWith("/dispatches"));
  assert.ok(dispatch);
  assert.equal(dispatch.options.method, "POST");
  assert.match(dispatch.options.body, /glm-heartbeat/);
  assert.match(logs.join("\n"), /dispatched glm-heartbeat/);
});

test("runHeartbeat does not dispatch while a collector run is active", async () => {
  const { calls, fetchImpl } = fakeFetch({
    snapshot: NOW - 90 * MINUTE,
    latestRunStatus: "queued",
  });
  const result = await runHeartbeat({
    token: "test-token",
    nowEpochMillis: NOW,
    thresholdMinutes: 75,
    fetchImpl,
    snapshotUrl: "https://example.test/glm-coding-plan.json",
    log: () => {},
  });
  assert.equal(result.action, "wait");
  assert.equal(result.dispatched, false);
  assert.equal(calls.some((call) => call.url.endsWith("/dispatches")), false);
});

test("runHeartbeat treats an unreadable snapshot as stale", async () => {
  const { calls, fetchImpl } = fakeFetch({
    snapshot: NOW,
    latestRunStatus: "completed",
  });
  const brokenFetch = async (url, options) => {
    if (String(url).includes("glm-coding-plan.json")) {
      return { ok: false, status: 503, async text() { return ""; } };
    }
    return fetchImpl(url, options);
  };
  const result = await runHeartbeat({
    token: "test-token",
    nowEpochMillis: NOW,
    thresholdMinutes: 75,
    fetchImpl: brokenFetch,
    snapshotUrl: "https://example.test/glm-coding-plan.json",
    log: () => {},
  });
  assert.equal(result.action, "dispatch");
  assert.equal(result.dispatched, true);
  assert.ok(calls.some((call) => call.url.endsWith("/dispatches")));
});

test("runHeartbeat dry-run does not dispatch", async () => {
  const { calls, fetchImpl } = fakeFetch({
    snapshot: NOW - 90 * MINUTE,
    latestRunStatus: "completed",
  });
  const result = await runHeartbeat({
    token: "test-token",
    nowEpochMillis: NOW,
    thresholdMinutes: 75,
    dryRun: true,
    fetchImpl,
    snapshotUrl: "https://example.test/glm-coding-plan.json",
    log: () => {},
  });
  assert.equal(result.action, "dispatch");
  assert.equal(result.dryRun, true);
  assert.equal(result.dispatched, false);
  assert.equal(calls.some((call) => call.url.endsWith("/dispatches")), false);
});
