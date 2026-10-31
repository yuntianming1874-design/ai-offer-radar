import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { buildPayload } from "../payload.mjs";
import { validatePayload } from "../validate.mjs";

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

test("accepts a fresh, complete official payload", () => {
  assert.deepEqual(
    validatePayload(validPayload(), {
      nowEpochMillis: FETCHED_AT + 60_000,
      maxAgeSeconds: 3_600
    }),
    []
  );
});

test("rejects a payload older than the freshness window", () => {
  const errors = validatePayload(validPayload(), {
    nowEpochMillis: FETCHED_AT + 3_601_000,
    maxAgeSeconds: 3_600
  });

  assert.ok(errors.some((error) => error.includes("过期")), errors.join("\n"));
});

test("rejects a payload fetched in the future", () => {
  const errors = validatePayload(validPayload(), {
    nowEpochMillis: FETCHED_AT - 10 * 60_000,
    maxAgeSeconds: 3_600
  });

  assert.ok(errors.some((error) => error.includes("未来")), errors.join("\n"));
});

test("rejects an incomplete or duplicated offer set", () => {
  const missing = validPayload();
  missing.offers = missing.offers.filter(
    (offer) => offer.billingPeriod !== "QUARTERLY"
  );
  assert.ok(
    validatePayload(missing, { nowEpochMillis: FETCHED_AT }).some((error) =>
      error.includes("9")
    )
  );

  const duplicated = validPayload();
  duplicated.offers[1].id = duplicated.offers[0].id;
  assert.ok(
    validatePayload(duplicated, { nowEpochMillis: FETCHED_AT }).some((error) =>
      error.includes("重复")
    )
  );
});

test("rejects missing provenance or cross-source offers", () => {
  const noUrl = validPayload();
  noUrl.source.sourceUrl = "";
  assert.ok(
    validatePayload(noUrl, { nowEpochMillis: FETCHED_AT }).some((error) =>
      error.includes("sourceUrl")
    )
  );

  const wrongSource = validPayload();
  wrongSource.offers[0].source.id = "other-source";
  assert.ok(
    validatePayload(wrongSource, { nowEpochMillis: FETCHED_AT }).some((error) =>
      error.includes("来源")
    )
  );
});

test("CLI fails closed on stale data and accepts fresh data", () => {
  const directory = mkdtempSync(join(tmpdir(), "glm-validate-"));
  const file = join(directory, "payload.json");
  writeFileSync(file, JSON.stringify(validPayload()));
  try {
    const stale = spawnSync(
      process.execPath,
      [
        "tools/glm-collector/validate-payload.mjs",
        file,
        "--now-epoch-millis",
        String(FETCHED_AT + 3_601_000),
        "--max-age-seconds",
        "3600"
      ],
      { encoding: "utf8" }
    );
    assert.equal(stale.status, 1);
    assert.match(stale.stderr, /过期/);

    const fresh = spawnSync(
      process.execPath,
      [
        "tools/glm-collector/validate-payload.mjs",
        file,
        "--now-epoch-millis",
        String(FETCHED_AT + 1_000)
      ],
      { encoding: "utf8" }
    );
    assert.equal(fresh.status, 0, fresh.stderr);
    assert.match(fresh.stdout, /通过/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
