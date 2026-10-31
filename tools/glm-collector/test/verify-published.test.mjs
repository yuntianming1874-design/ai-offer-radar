import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import test from "node:test";
import { buildPayload } from "../payload.mjs";
import { verifyPublished } from "../verify-published.mjs";

const FETCHED_AT = 1_790_000_000_000;

function card(title, current, original) {
  return { title, current, original };
}

const TABS = [
  {
    label: "连续包月",
    cards: [
      card("Lite", "¥118/月", "¥118/月"),
      card("Pro", "¥538/月", "¥538/月"),
      card("Max", "¥1078/月", "¥1078/月")
    ]
  },
  {
    label: "连续包季 8折",
    cards: [
      card("Lite", "¥94.4/月", "¥118/月"),
      card("Pro", "¥430.4/月", "¥538/月"),
      card("Max", "¥862.4/月", "¥1078/月")
    ]
  },
  {
    label: "连续包年 7折",
    cards: [
      card("Lite", "¥82.6/月", "¥118/月"),
      card("Pro", "¥376.6/月", "¥538/月"),
      card("Max", "¥754.6/月", "¥1078/月")
    ]
  }
];

function validPayload() {
  return buildPayload({ tabs: TABS, fetchedAtEpochMillis: FETCHED_AT });
}

function response(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body
  };
}

function sha256(text) {
  return createHash("sha256").update(text).digest("hex");
}

test("accepts a fresh published payload whose bytes match the artifact", async () => {
  const payload = validPayload();
  const body = JSON.stringify(payload);
  const result = await verifyPublished({
    url: "https://example.test/glm-coding-plan.json",
    expectedSha256: sha256(body),
    nowEpochMillis: () => FETCHED_AT + 60_000,
    fetchImpl: async () => response(body),
    sleep: async () => {}
  });

  assert.equal(result.attempts, 1);
  assert.equal(result.sha256, sha256(body));
  assert.equal(result.products, 3);
  assert.equal(result.offers, 9);
});

test("retries propagation failures before accepting the new snapshot", async () => {
  const body = JSON.stringify(validPayload());
  let calls = 0;
  const result = await verifyPublished({
    url: "https://example.test/glm-coding-plan.json",
    nowEpochMillis: () => FETCHED_AT + 60_000,
    attempts: 3,
    fetchImpl: async () => {
      calls += 1;
      return calls === 1 ? response("Not Found", 404) : response(body);
    },
    sleep: async () => {}
  });

  assert.equal(calls, 2);
  assert.equal(result.attempts, 2);
});

test("fails when the published payload is older than the freshness window", async () => {
  const body = JSON.stringify(validPayload());
  let calls = 0;
  await assert.rejects(
    verifyPublished({
      url: "https://example.test/glm-coding-plan.json",
      maxAgeSeconds: 1800,
      attempts: 2,
      nowEpochMillis: () => FETCHED_AT + 3_600_000,
      fetchImpl: async () => {
        calls += 1;
        return response(body);
      },
      sleep: async () => {}
    }),
    /过期/
  );
  assert.equal(calls, 2);
});

test("fails when the deployed bytes differ from the collected artifact", async () => {
  const body = JSON.stringify(validPayload());
  await assert.rejects(
    verifyPublished({
      url: "https://example.test/glm-coding-plan.json",
      expectedSha256: "0".repeat(64),
      nowEpochMillis: () => FETCHED_AT + 60_000,
      attempts: 2,
      fetchImpl: async () => response(body),
      sleep: async () => {}
    }),
    /SHA-256/
  );
});

test("CLI verifies a locally served endpoint and reports counts", async () => {
  const body = JSON.stringify(validPayload());
  const server = createServer((request, response) => {
    response.writeHead(200, { "content-type": "application/json" });
    response.end(body);
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  try {
    const output = await new Promise((resolve, reject) => {
      const child = spawn(
        process.execPath,
        [
          "tools/glm-collector/verify-published.mjs",
          `http://127.0.0.1:${port}/glm-coding-plan.json`,
          "--max-age-seconds",
          "3600",
          "--attempts",
          "1",
          "--now-epoch-millis",
          String(FETCHED_AT + 60_000)
        ],
        { encoding: "utf8" }
      );
      let stdout = "";
      let stderr = "";
      child.stdout.on("data", (chunk) => {
        stdout += chunk;
      });
      child.stderr.on("data", (chunk) => {
        stderr += chunk;
      });
      child.on("close", (code) => {
        if (code === 0) resolve({ stdout, stderr });
        else reject(new Error(`exit ${code}: ${stderr}`));
      });
    });
    assert.match(output.stdout, /3 档/);
    assert.match(output.stdout, /9 个 offer/);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
