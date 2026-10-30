import assert from "node:assert/strict";
import test from "node:test";
import {
  SOURCE_ID,
  SOURCE_URL,
  buildPayload,
  parseCnyMinor
} from "../payload.mjs";

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

test("parses yuan text into minor units without floating point drift", () => {
  assert.equal(parseCnyMinor("¥94.4/月"), 9440);
  assert.equal(parseCnyMinor("¥118/月"), 11800);
  assert.equal(parseCnyMinor("¥1,078.00/月"), 107800);
  assert.throws(() => parseCnyMinor("免费"), /无法/);
});

test("builds three products, three plans and nine official offers", () => {
  const payload = buildPayload({ tabs: TABS, fetchedAtEpochMillis: FETCHED_AT });

  assert.equal(payload.source.id, SOURCE_ID);
  assert.equal(payload.source.sourceType, "OFFICIAL_WEB");
  assert.equal(payload.source.sourceUrl, SOURCE_URL);
  assert.equal(payload.source.fetchedAtEpochMillis, FETCHED_AT);
  assert.match(payload.source.titleOrSummary, /Lite 连续包季 8折 ¥94.4\/月/);

  assert.equal(payload.products.length, 3);
  assert.equal(payload.plans.length, 3);
  assert.equal(payload.offers.length, 9);
  assert.ok(payload.products.every((product) => product.isDemo === false));
  assert.ok(payload.offers.every((offer) => !offer.id.startsWith("demo-")));

  const quarterlyLite = payload.offers.find(
    (offer) => offer.id === `${SOURCE_ID}-lite-quarterly`
  );
  assert.ok(quarterlyLite);
  assert.equal(quarterlyLite.billingPeriod, "QUARTERLY");
  assert.equal(quarterlyLite.monthlyEquivalentMinor, 9440);
  assert.equal(quarterlyLite.actualAmountMinor, 28320);
  assert.equal(quarterlyLite.originalAmountMinor, 35400);
  assert.equal(quarterlyLite.offerType, "PROMOTION");
  assert.equal(quarterlyLite.officialUrl, SOURCE_URL);
  assert.equal(quarterlyLite.source, payload.source);

  const yearlyLite = payload.offers.find(
    (offer) => offer.id === `${SOURCE_ID}-lite-yearly`
  );
  assert.equal(yearlyLite.billingPeriod, "YEARLY");
  assert.equal(yearlyLite.monthlyEquivalentMinor, 8260);
  assert.equal(yearlyLite.actualAmountMinor, 99120);
  assert.equal(yearlyLite.originalAmountMinor, 141600);

  const monthlyLite = payload.offers.find(
    (offer) => offer.id === `${SOURCE_ID}-lite-monthly`
  );
  assert.equal(monthlyLite.actualAmountMinor, 11800);
  assert.equal(monthlyLite.offerType, "NONE");
});

test("rejects a partial or out-of-order tab set", () => {
  assert.throws(
    () => buildPayload({ tabs: TABS.slice(0, 2), fetchedAtEpochMillis: FETCHED_AT }),
    /三个 tab/
  );

  const wrongOrder = structuredClone(TABS);
  wrongOrder[1].label = "连续包年";
  assert.throws(
    () => buildPayload({ tabs: wrongOrder, fetchedAtEpochMillis: FETCHED_AT }),
    /tab 顺序异常/
  );
});

test("rejects missing tiers, missing prices and impossible discounts", () => {
  const missingTier = structuredClone(TABS);
  missingTier[0].cards.pop();
  assert.throws(
    () => buildPayload({ tabs: missingTier, fetchedAtEpochMillis: FETCHED_AT }),
    /三档卡片/
  );

  const missingPrice = structuredClone(TABS);
  missingPrice[2].cards[0].current = "";
  assert.throws(
    () => buildPayload({ tabs: missingPrice, fetchedAtEpochMillis: FETCHED_AT }),
    /当前价/
  );

  const impossible = structuredClone(TABS);
  impossible[1].cards[0].current = "¥128/月";
  assert.throws(
    () => buildPayload({ tabs: impossible, fetchedAtEpochMillis: FETCHED_AT }),
    /不能高于原价/
  );
});
