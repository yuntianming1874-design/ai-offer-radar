// Converts the rendered GLM Coding Plan cards into the exact JSON shape that
// RemoteSyncPayload/RemotePayloadNormalizer expect. Keep this file dependency
// free so it can be tested with `node --test`.
export const SOURCE_URL = "https://www.bigmodel.cn/glm-coding";
export const SOURCE_ID = "glm-coding-plan-official";
export const SOURCE_TITLE = "GLM Coding Plan 官方套餐页";

const TIERS = [
  { name: "Lite", slug: "lite" },
  { name: "Pro", slug: "pro" },
  { name: "Max", slug: "max" }
];

const PERIODS = [
  { key: "MONTHLY", months: 1, marker: "月" },
  { key: "QUARTERLY", months: 3, marker: "季" },
  { key: "YEARLY", months: 12, marker: "年" }
];

export function parseCnyMinor(text) {
  const match = String(text ?? "")
    .replace(/,/g, "")
    .match(/¥\s*(\d+(?:\.\d{1,2})?)/);
  if (!match) {
    throw new Error(`无法从价格文本中解析人民币金额: ${JSON.stringify(text)}`);
  }
  const [whole, fraction = ""] = match[1].split(".");
  return Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
}

function formatCny(minor) {
  return `¥${(minor / 100).toFixed(2).replace(/\.00$/, "")}`;
}

function requireField(value, field) {
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(`采集结果缺少 ${field}`);
  }
  return value.trim();
}

export function buildPayload({ tabs, fetchedAtEpochMillis = Date.now() }) {
  if (!Array.isArray(tabs) || tabs.length !== PERIODS.length) {
    throw new Error("官方套餐页必须同时采集连续包月、连续包季、连续包年三个 tab");
  }
  if (!Number.isSafeInteger(fetchedAtEpochMillis) || fetchedAtEpochMillis <= 0) {
    throw new Error("采集时间必须为正整数毫秒");
  }

  const perTab = tabs.map((tab, index) => {
    const period = PERIODS[index];
    const label = requireField(tab?.label, `tab ${index} 文案`);
    if (!label.includes(period.marker)) {
      throw new Error(`tab 顺序异常: 期望包含“${period.marker}”，实际为 ${label}`);
    }
    const cards = tab?.cards;
    if (!Array.isArray(cards) || cards.length !== TIERS.length) {
      throw new Error(`tab ${label} 必须包含 Lite/Pro/Max 三档卡片`);
    }
    const byTier = new Map();
    for (const card of cards) {
      const name = requireField(card?.title, `tab ${label} 的档位名称`);
      const tier = TIERS.find((candidate) => candidate.name === name);
      if (!tier) {
        throw new Error(`未知套餐档位: ${name}`);
      }
      if (byTier.has(tier.slug)) {
        throw new Error(`套餐档位重复: ${name}`);
      }
      const current = requireField(card?.current, `${name} 当前价`);
      const rawOriginal =
        typeof card?.original === "string" ? card.original.trim() : "";
      // The monthly tab does not render a struck-through original price when
      // there is no discount. In that case the current price is the original.
      const original = rawOriginal || current;
      const currentMonthlyMinor = parseCnyMinor(current);
      const originalMonthlyMinor = parseCnyMinor(original);
      if (currentMonthlyMinor <= 0 || originalMonthlyMinor <= 0) {
        throw new Error(`${name} 价格必须大于 0`);
      }
      if (currentMonthlyMinor > originalMonthlyMinor) {
        throw new Error(`${name} 当前价不能高于原价`);
      }
      byTier.set(tier.slug, {
        current,
        original,
        currentMonthlyMinor,
        originalMonthlyMinor
      });
    }
    for (const tier of TIERS) {
      if (!byTier.has(tier.slug)) {
        throw new Error(`tab ${label} 缺少 ${tier.name} 价格`);
      }
    }
    return { period, label, byTier };
  });

  const source = {
    id: SOURCE_ID,
    sourceType: "OFFICIAL_WEB",
    sourceUrl: SOURCE_URL,
    titleOrSummary: `${SOURCE_TITLE}；抓取文本：${perTab
      .flatMap(({ label, byTier }) =>
        TIERS.map((tier) => {
          const price = byTier.get(tier.slug);
          return `${tier.name} ${label} ${price.current}（原价 ${price.original}）`;
        })
      )
      .join("；")}`,
    fetchedAtEpochMillis
  };

  const products = TIERS.map((tier) => ({
    id: `${SOURCE_ID}-${tier.slug}`,
    name: `GLM Coding Plan ${tier.name}`,
    logoUrl: null,
    isDemo: false
  }));

  const plans = TIERS.map((tier) => ({
    id: `${SOURCE_ID}-${tier.slug}`,
    productId: `${SOURCE_ID}-${tier.slug}`,
    name: tier.name,
    planType: "STANDARD"
  }));

  const offers = [];
  for (const { period, label, byTier } of perTab) {
    for (const tier of TIERS) {
      const price = byTier.get(tier.slug);
      const actualAmountMinor = price.currentMonthlyMinor * period.months;
      const originalAmountMinor = price.originalMonthlyMinor * period.months;
      offers.push({
        id: `${SOURCE_ID}-${tier.slug}-${period.key.toLowerCase()}`,
        productId: `${SOURCE_ID}-${tier.slug}`,
        planId: `${SOURCE_ID}-${tier.slug}`,
        planType: "STANDARD",
        offerType:
          originalAmountMinor > actualAmountMinor ? "PROMOTION" : "NONE",
        title: `GLM Coding Plan ${tier.name} ${label}`,
        discountDescription: `${label}：${price.current}（原价 ${price.original}），折合 ${formatCny(
          price.currentMonthlyMinor
        )}/月`,
        regionCode: "CN",
        currencyCode: "CNY",
        billingPeriod: period.key,
        taxIncluded: true,
        originalAmountMinor,
        actualAmountMinor,
        monthlyEquivalentMinor: price.currentMonthlyMinor,
        startsAtEpochMillis: fetchedAtEpochMillis,
        endsAtEpochMillis: null,
        updatedAtEpochMillis: fetchedAtEpochMillis,
        source,
        officialUrl: SOURCE_URL
      });
    }
  }

  return {
    schemaVersion: 1,
    source,
    products,
    plans,
    offers
  };
}
