#!/usr/bin/env node
import { createHash } from "node:crypto";
import { pathToFileURL } from "node:url";
import { validatePayload } from "./validate.mjs";

const DEFAULT_ATTEMPTS = 12;
const DEFAULT_DELAY_MS = 5_000;
const DEFAULT_MAX_AGE_SECONDS = 1_800;
const DEFAULT_TIMEOUT_MS = 15_000;

export class PublishedEndpointError extends Error {
  constructor(message) {
    super(message);
    this.name = "PublishedEndpointError";
  }
}

/**
 * Fetches the deployed JSON, waits for GitHub Pages propagation, and verifies
 * that the live bytes are exactly the artifact that CI just validated. This is
 * deliberately stricter than checking HTTP 200: an old deployment or a CDN
 * error page must not make a green workflow look healthy.
 *
 * @param {{
 *   url: string,
 *   expectedSha256?: string | null,
 *   maxAgeSeconds?: number,
 *   attempts?: number,
 *   delayMs?: number,
 *   timeoutMs?: number,
 *   nowEpochMillis?: () => number,
 *   fetchImpl?: typeof fetch,
 *   sleep?: (ms: number) => Promise<void>,
 *   log?: (message: string) => void
 * }} options
 */
export async function verifyPublished(options) {
  const {
    url,
    expectedSha256 = null,
    maxAgeSeconds = DEFAULT_MAX_AGE_SECONDS,
    attempts = DEFAULT_ATTEMPTS,
    delayMs = DEFAULT_DELAY_MS,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    nowEpochMillis = () => Date.now(),
    fetchImpl = globalThis.fetch,
    sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
    log = () => {}
  } = options;

  if (typeof url !== "string" || !/^https?:\/\//.test(url)) {
    throw new PublishedEndpointError("endpoint 必须是 http(s) URL");
  }
  if (!Number.isInteger(attempts) || attempts < 1 || attempts > 60) {
    throw new PublishedEndpointError("--attempts 必须是 1..60 的整数");
  }
  if (!Number.isFinite(maxAgeSeconds) || maxAgeSeconds <= 0) {
    throw new PublishedEndpointError("--max-age-seconds 必须是正数");
  }
  if (!Number.isFinite(delayMs) || delayMs < 0) {
    throw new PublishedEndpointError("--delay-ms 必须是非负数");
  }
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new PublishedEndpointError("--timeout-ms 必须是正数");
  }
  if (typeof fetchImpl !== "function") {
    throw new PublishedEndpointError("当前运行环境没有 fetch");
  }

  const normalizedSha = expectedSha256 == null ? null : expectedSha256.trim().toLowerCase();
  if (normalizedSha !== null && !/^[a-f0-9]{64}$/.test(normalizedSha)) {
    throw new PublishedEndpointError("--expected-sha256 必须是 64 位十六进制");
  }

  let lastError = null;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const separator = url.includes("?") ? "&" : "?";
      const requestUrl = `${url}${separator}cachebust=${nowEpochMillis()}-${attempt}`;
      const response = await fetchImpl(requestUrl, {
        headers: {
          "cache-control": "no-cache",
          pragma: "no-cache"
        },
        signal: AbortSignal.timeout(timeoutMs)
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const body = await response.text();
      const actualSha = createHash("sha256").update(body).digest("hex");
      if (normalizedSha !== null && actualSha !== normalizedSha) {
        throw new Error(
          `线上内容 SHA-256 与构建产物不一致：期望 ${normalizedSha}，实际 ${actualSha}`
        );
      }

      let payload;
      try {
        payload = JSON.parse(body);
      } catch (error) {
        throw new Error(`线上响应不是合法 JSON：${error.message}`);
      }
      const errors = validatePayload(payload, {
        nowEpochMillis: nowEpochMillis(),
        maxAgeSeconds
      });
      if (errors.length > 0) {
        throw new Error(`线上 payload 校验失败：${errors.join("；")}`);
      }

      return {
        attempts: attempt,
        url: requestUrl,
        bytes: Buffer.byteLength(body, "utf8"),
        sha256: actualSha,
        products: payload.products.length,
        plans: payload.plans.length,
        offers: payload.offers.length,
        fetchedAtEpochMillis: payload.source.fetchedAtEpochMillis
      };
    } catch (error) {
      lastError = error;
      log(`第 ${attempt}/${attempts} 次发布端点校验失败：${error.message}`);
      if (attempt < attempts) {
        await sleep(delayMs);
      }
    }
  }

  throw new PublishedEndpointError(
    `发布端点校验失败（尝试 ${attempts} 次）：${lastError?.message ?? "未知错误"}`
  );
}

function parseArgs(argv) {
  const options = {
    url: null,
    expectedSha256: null,
    maxAgeSeconds: DEFAULT_MAX_AGE_SECONDS,
    attempts: DEFAULT_ATTEMPTS,
    delayMs: DEFAULT_DELAY_MS,
    timeoutMs: DEFAULT_TIMEOUT_MS,
    nowEpochMillis: undefined
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--expected-sha256") {
      options.expectedSha256 = argv[++index];
    } else if (arg === "--max-age-seconds") {
      options.maxAgeSeconds = Number(argv[++index]);
    } else if (arg === "--attempts") {
      options.attempts = Number(argv[++index]);
    } else if (arg === "--delay-ms") {
      options.delayMs = Number(argv[++index]);
    } else if (arg === "--timeout-ms") {
      options.timeoutMs = Number(argv[++index]);
    } else if (arg === "--now-epoch-millis") {
      options.nowEpochMillis = Number(argv[++index]);
    } else if (arg.startsWith("--")) {
      throw new Error(`未知参数：${arg}`);
    } else if (options.url === null) {
      options.url = arg;
    } else {
      throw new Error(`多余参数：${arg}`);
    }
  }

  if (!options.url) {
    throw new Error(
      "用法：verify-published.mjs <endpoint-url> [--expected-sha256 HEX] " +
        "[--max-age-seconds N] [--attempts N] [--delay-ms N]"
    );
  }
  if (
    options.nowEpochMillis !== undefined &&
    !Number.isFinite(options.nowEpochMillis)
  ) {
    throw new Error("--now-epoch-millis 必须是数字");
  }
  return options;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const result = await verifyPublished({
    url: options.url,
    expectedSha256: options.expectedSha256,
    maxAgeSeconds: options.maxAgeSeconds,
    attempts: options.attempts,
    delayMs: options.delayMs,
    timeoutMs: options.timeoutMs,
    nowEpochMillis:
      options.nowEpochMillis === undefined ? () => Date.now() : () => options.nowEpochMillis,
    log: (message) => process.stdout.write(`${message}\n`)
  });

  process.stdout.write(
    `发布端点校验通过：第 ${result.attempts} 次尝试，` +
      `${result.products} 档 / ${result.offers} 个 offer，` +
      `payload SHA-256 ${result.sha256}\n`
  );
}

const isMainModule =
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(process.argv[1]).href;

if (isMainModule) {
  try {
    await main();
  } catch (error) {
    process.stderr.write(`发布端点校验失败：${error.message}\n`);
    process.exitCode = 1;
  }
}
