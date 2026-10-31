#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { validatePayload } from "./validate.mjs";

function parseArgs(argv) {
  const options = {
    file: null,
    maxAgeSeconds: 60 * 60,
    nowEpochMillis: undefined
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--max-age-seconds") {
      options.maxAgeSeconds = Number(argv[++index]);
    } else if (arg === "--now-epoch-millis") {
      options.nowEpochMillis = Number(argv[++index]);
    } else if (arg.startsWith("--")) {
      throw new Error(`未知参数：${arg}`);
    } else if (options.file === null) {
      options.file = arg;
    } else {
      throw new Error(`多余参数：${arg}`);
    }
  }
  if (!options.file) {
    throw new Error("用法：validate-payload.mjs <payload.json> [--max-age-seconds N]");
  }
  if (!Number.isFinite(options.maxAgeSeconds) || options.maxAgeSeconds <= 0) {
    throw new Error("--max-age-seconds 必须是正数");
  }
  if (
    options.nowEpochMillis !== undefined &&
    !Number.isFinite(options.nowEpochMillis)
  ) {
    throw new Error("--now-epoch-millis 必须是数字");
  }
  return options;
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  let payload;
  try {
    payload = JSON.parse(readFileSync(options.file, "utf8"));
  } catch (error) {
    process.stderr.write(`无法读取或解析 ${options.file}：${error.message}\n`);
    process.exit(1);
  }

  const errors = validatePayload(payload, {
    maxAgeSeconds: options.maxAgeSeconds,
    nowEpochMillis: options.nowEpochMillis
  });
  if (errors.length > 0) {
    process.stderr.write(`采集 payload 校验失败（${errors.length} 项）：\n`);
    for (const error of errors) {
      process.stderr.write(`- ${error}\n`);
    }
    process.exit(1);
  }

  const offerCount = payload.offers.length;
  const fetchedAt = new Date(payload.source.fetchedAtEpochMillis).toISOString();
  process.stdout.write(
    `采集 payload 校验通过：${payload.products.length} 档 / ${offerCount} 个 offer，` +
      `抓取时间 ${fetchedAt}\n`
  );
}

try {
  main();
} catch (error) {
  process.stderr.write(`校验失败：${error.message}\n`);
  process.exit(1);
}
