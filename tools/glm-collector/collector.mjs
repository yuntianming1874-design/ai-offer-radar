#!/usr/bin/env node
import http from "node:http";
import { spawn } from "node:child_process";
import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { buildPayload, SOURCE_URL } from "./payload.mjs";

const DEFAULT_PORT = 8787;
const DEFAULT_HOST = "127.0.0.1";
const DEFAULT_TIMEOUT_MS = 45_000;
const DEFAULT_MAX_AGE_MS = 5 * 60_000;
const TARGET_URL = SOURCE_URL;

const CHROME_CANDIDATES = [
  process.env.CHROME_BIN,
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/Applications/Chromium.app/Contents/MacOS/Chromium",
  "/usr/bin/google-chrome",
  "/usr/bin/google-chrome-stable",
  "/usr/bin/chromium",
  "/usr/bin/chromium-browser"
].filter(Boolean);

function parseArgs(argv) {
  const options = {
    once: false,
    serve: false,
    port: DEFAULT_PORT,
    host: DEFAULT_HOST,
    timeoutMs: DEFAULT_TIMEOUT_MS,
    maxAgeMs: DEFAULT_MAX_AGE_MS,
    chrome: undefined
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    switch (argument) {
      case "--once":
        options.once = true;
        break;
      case "--serve":
        options.serve = true;
        break;
      case "--port":
        options.port = Number(argv[++index]);
        break;
      case "--host":
        options.host = argv[++index];
        break;
      case "--chrome":
        options.chrome = argv[++index];
        break;
      case "--timeout-ms":
        options.timeoutMs = Number(argv[++index]);
        break;
      case "--max-age-seconds":
        options.maxAgeMs = Number(argv[++index]) * 1000;
        break;
      case "--help":
      case "-h":
        printUsage();
        process.exit(0);
        break;
      default:
        throw new Error(`未知参数: ${argument}`);
    }
  }
  if (!Number.isInteger(options.port) || options.port <= 0) {
    throw new Error("--port 必须是正整数");
  }
  if (!Number.isFinite(options.timeoutMs) || options.timeoutMs <= 0) {
    throw new Error("--timeout-ms 必须是正数");
  }
  if (!Number.isFinite(options.maxAgeMs) || options.maxAgeMs < 0) {
    throw new Error("--max-age-seconds 必须是非负数");
  }
  if (options.once && options.serve) {
    throw new Error("--once 与 --serve 只能二选一");
  }
  if (!options.once) options.serve = true;
  return options;
}

function printUsage() {
  process.stderr.write(`GLM Coding Plan 官方套餐页采集器

用法:
  node collector.mjs --once                # 采集一次，把 JSON 打印到 stdout
  node collector.mjs --serve --port 8787   # 提供 http://127.0.0.1:8787/v1/glm-coding-plan

可选:
  --chrome <path>         指定 Chrome/Chromium 可执行文件
  --timeout-ms <ms>       单次采集超时，默认 ${DEFAULT_TIMEOUT_MS}
  --max-age-seconds <s>   服务端缓存时长，默认 300
  --host <host>           监听地址，默认 127.0.0.1（不要暴露到公网）
`);
}

function findChrome(explicitPath) {
  if (explicitPath) {
    if (!existsSync(explicitPath)) {
      throw new Error(`找不到 Chrome: ${explicitPath}`);
    }
    return explicitPath;
  }
  const found = CHROME_CANDIDATES.find((candidate) => existsSync(candidate));
  if (!found) {
    throw new Error(
      "找不到 Chrome/Chromium。请用 --chrome 指定路径，或设置 CHROME_BIN。"
    );
  }
  return found;
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function withTimeout(promise, timeoutMs, message) {
  let timer;
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error(message)), timeoutMs);
    })
  ]).finally(() => clearTimeout(timer));
}

async function launchChrome(chromePath, timeoutMs) {
  const userDataDir = mkdtempSync(path.join(tmpdir(), "glm-collector-"));
  const child = spawn(
    chromePath,
    [
      "--headless=new",
      "--disable-gpu",
      "--no-sandbox",
      "--disable-dev-shm-usage",
      "--disable-background-networking",
      "--disable-default-apps",
      "--disable-extensions",
      "--disable-sync",
      "--no-first-run",
      "--no-default-browser-check",
      "--remote-debugging-port=0",
      `--user-data-dir=${userDataDir}`,
      "about:blank"
    ],
    { stdio: ["ignore", "ignore", "pipe"] }
  );

  let stderr = "";
  let settled = false;
  let timer;
  const result = await new Promise((resolve, reject) => {
    const cleanup = () => {
      clearTimeout(timer);
      child.stderr.removeListener("data", onData);
      child.removeListener("error", onError);
      child.removeListener("exit", onExit);
    };
    const finish = (callback, value) => {
      if (settled) return;
      settled = true;
      cleanup();
      callback(value);
    };
    const onData = (chunk) => {
      stderr += chunk.toString();
      const match = stderr.match(/DevTools listening on (ws:\/\/\S+)/);
      if (match) {
        finish(resolve, { webSocketUrl: match[1], child, userDataDir, stderr: () => stderr });
      }
    };
    const onError = (error) => finish(reject, error);
    const onExit = (code, signal) =>
      finish(reject, new Error(`Chrome 提前退出 (code=${code}, signal=${signal})`));
    child.stderr.on("data", onData);
    child.once("error", onError);
    child.once("exit", onExit);
    timer = setTimeout(
      () => finish(reject, new Error(`Chrome 在 ${timeoutMs}ms 内未启动 DevTools`)),
      timeoutMs
    );
  }).catch((error) => {
    child.kill("SIGKILL");
    throw error;
  });

  return result;
}

async function connectCdp(webSocketUrl, timeoutMs) {
  const socket = new WebSocket(webSocketUrl);
  const pending = new Map();
  let nextId = 1;
  const closed = new Promise((_, reject) => {
    socket.addEventListener("close", () => reject(new Error("CDP WebSocket 已关闭")));
    socket.addEventListener("error", () => reject(new Error("CDP WebSocket 连接失败")));
  });
  socket.addEventListener("message", (event) => {
    let message;
    try {
      message = JSON.parse(event.data);
    } catch {
      return;
    }
    if (message.id && pending.has(message.id)) {
      const { resolve, reject, timer } = pending.get(message.id);
      pending.delete(message.id);
      clearTimeout(timer);
      if (message.error) {
        reject(new Error(`${message.error.message} (${message.error.code})`));
      } else {
        resolve(message.result ?? {});
      }
    }
  });
  await withTimeout(
    new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    }),
    timeoutMs,
    "连接 Chrome DevTools 超时"
  );

  const send = (method, params = {}, sessionId) => {
    const id = nextId++;
    const promise = new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        pending.delete(id);
        reject(new Error(`CDP 命令超时: ${method}`));
      }, timeoutMs);
      pending.set(id, { resolve, reject, timer });
      socket.send(JSON.stringify({ id, method, params, sessionId }));
    });
    return Promise.race([
      promise,
      closed.then(() => {
        throw new Error("CDP WebSocket 已关闭");
      })
    ]);
  };

  return {
    send,
    close: () => socket.close()
  };
}

async function evaluate(cdp, sessionId, expression) {
  const result = await cdp.send(
    "Runtime.evaluate",
    {
      expression,
      awaitPromise: true,
      returnByValue: true
    },
    sessionId
  );
  if (result.exceptionDetails) {
    const details = result.exceptionDetails;
    const description =
      details.exception?.description || details.text || "页面脚本执行失败";
    throw new Error(description);
  }
  return result.result?.value;
}

async function waitForCards(cdp, sessionId, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const state = await evaluate(
        cdp,
        sessionId,
        `(() => {
          const cards = [...document.querySelectorAll('.cp-card')];
          const tabs = [...document.querySelectorAll('.switch-tab-item')];
          const prices = [...document.querySelectorAll('.cp-card__price-current')]
            .map((element) => element.textContent.trim());
          return {
            cards: cards.length,
            tabs: tabs.length,
            prices
          };
        })()`
      );
      if (
        state &&
        state.cards === 3 &&
        state.tabs === 3 &&
        state.prices.length === 3 &&
        state.prices.every((price) => /¥\s*\d/.test(price))
      ) {
        return;
      }
    } catch (error) {
      lastError = error;
    }
    await sleep(300);
  }
  throw new Error(
    `等待官方套餐卡片超时${lastError ? `: ${lastError.message}` : ""}`
  );
}

async function readCards(cdp, sessionId) {
  return evaluate(
    cdp,
    sessionId,
    `[...document.querySelectorAll('.cp-card')].map((card) => ({
      title: card.querySelector('.cp-card__title')?.textContent?.trim() ?? '',
      current: card.querySelector('.cp-card__price-current')?.textContent?.trim() ?? '',
      original: card.querySelector('.cp-card__price-original')?.textContent?.trim() ?? ''
    }))`
  );
}

async function collectOnce({ chromePath, timeoutMs }) {
  const startedAt = Date.now();
  const launched = await launchChrome(chromePath, timeoutMs);
  let cdp;
  let targetId;
  try {
    cdp = await connectCdp(launched.webSocketUrl, timeoutMs);
    const target = await cdp.send("Target.createTarget", { url: "about:blank" });
    targetId = target.targetId;
    const attached = await cdp.send("Target.attachToTarget", {
      targetId,
      flatten: true
    });
    const sessionId = attached.sessionId;
    await cdp.send("Page.enable", {}, sessionId);
    await cdp.send("Runtime.enable", {}, sessionId);
    await cdp.send("Page.navigate", { url: TARGET_URL }, sessionId);
    await waitForCards(cdp, sessionId, timeoutMs);

    const labels = await evaluate(
      cdp,
      sessionId,
      `[...document.querySelectorAll('.switch-tab-item')].map((tab) => tab.textContent.trim())`
    );
    if (!Array.isArray(labels) || labels.length !== 3) {
      throw new Error("官方套餐页没有出现三个计费周期 tab");
    }

    const tabs = [];
    for (let index = 0; index < 3; index += 1) {
      await evaluate(
        cdp,
        sessionId,
        `(() => {
          const tabs = [...document.querySelectorAll('.switch-tab-item')];
          if (!tabs[${index}]) throw new Error('缺少第 ${index + 1} 个 tab');
          tabs[${index}].click();
          return true;
        })()`
      );
      await sleep(1_200);
      const cards = await readCards(cdp, sessionId);
      if (!Array.isArray(cards) || cards.length !== 3) {
        throw new Error(`tab ${labels[index]} 未读取到三张套餐卡片`);
      }
      tabs.push({ label: labels[index], cards });
    }

    const fetchedAtEpochMillis = Date.now();
    const payload = buildPayload({ tabs, fetchedAtEpochMillis });
    process.stderr.write(
      `采集成功: ${tabs.length} tabs / ${payload.offers.length} offers / ${Date.now() - startedAt}ms\n`
    );
    return payload;
  } finally {
    if (targetId && cdp) {
      await cdp.send("Target.closeTarget", { targetId }).catch(() => {});
    }
    cdp?.close();
    launched.child.kill("SIGKILL");
    await sleep(200);
    rmSync(launched.userDataDir, { recursive: true, force: true });
  }
}

function createServer({ chromePath, timeoutMs, maxAgeMs, host, port }) {
  let cache = null;
  let lastSuccessAt = null;
  let lastError = null;
  let inFlight = null;

  const refresh = () => {
    if (inFlight) return inFlight;
    inFlight = collectOnce({ chromePath, timeoutMs })
      .then((payload) => {
        cache = payload;
        lastSuccessAt = Date.now();
        lastError = null;
        return payload;
      })
      .catch((error) => {
        lastError = error.message;
        throw error;
      })
      .finally(() => {
        inFlight = null;
      });
    return inFlight;
  };

  const server = http.createServer(async (request, response) => {
    const url = new URL(request.url ?? "/", "http://127.0.0.1");
    if (request.method !== "GET") {
      response.writeHead(405, { "Content-Type": "application/json; charset=utf-8" });
      response.end(JSON.stringify({ error: "method not allowed" }));
      return;
    }
    if (url.pathname === "/health") {
      response.writeHead(200, {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store"
      });
      response.end(
        JSON.stringify({
          ok: lastError === null,
          sourceUrl: SOURCE_URL,
          lastSuccessAt,
          lastError,
          cachedFetchedAt: cache?.source?.fetchedAtEpochMillis ?? null
        })
      );
      return;
    }
    if (url.pathname !== "/v1/glm-coding-plan") {
      response.writeHead(404, { "Content-Type": "application/json; charset=utf-8" });
      response.end(JSON.stringify({ error: "not found" }));
      return;
    }
    try {
      const payload =
        cache && lastSuccessAt && Date.now() - lastSuccessAt < maxAgeMs
          ? cache
          : await refresh();
      response.writeHead(200, {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store",
        "X-Collected-At": String(payload.source.fetchedAtEpochMillis)
      });
      response.end(JSON.stringify(payload));
    } catch (error) {
      response.writeHead(503, {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store"
      });
      response.end(
        JSON.stringify({
          error: error.message,
          sourceUrl: SOURCE_URL,
          hint: "检查本机 Chrome 是否能访问官方套餐页，并重试"
        })
      );
    }
  });

  server.listen(port, host, () => {
    process.stderr.write(
      `GLM 采集器监听 http://${host}:${port}/v1/glm-coding-plan (缓存 ${Math.round(
        maxAgeMs / 1000
      )}s)\n`
    );
  });
  refresh().catch((error) => {
    process.stderr.write(`预热采集失败: ${error.message}\n`);
  });

  const shutdown = () => {
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 2_000).unref();
  };
  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
  return server;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const chromePath = findChrome(options.chrome);
  if (options.once) {
    const payload = await collectOnce({
      chromePath,
      timeoutMs: options.timeoutMs
    });
    process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
    return;
  }
  createServer({
    chromePath,
    timeoutMs: options.timeoutMs,
    maxAgeMs: options.maxAgeMs,
    host: options.host,
    port: options.port
  });
}

main().catch((error) => {
  process.stderr.write(`GLM 采集器失败: ${error.message}\n`);
  process.exit(1);
});
