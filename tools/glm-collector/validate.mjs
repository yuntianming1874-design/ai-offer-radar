import { SOURCE_ID } from "./payload.mjs";

const PERIODS = new Set(["MONTHLY", "QUARTERLY", "YEARLY"]);
const MAX_FUTURE_SKEW_MS = 5 * 60 * 1000;

/**
 * Validates a collector payload before it is published by CI. The checks are
 * intentionally strict: a malformed or stale payload must never replace the
 * last known-good JSON on the static site.
 *
 * @param {unknown} payload
 * @param {{ nowEpochMillis?: number, maxAgeSeconds?: number }} [options]
 * @returns {string[]} human-readable errors; empty means valid
 */
export function validatePayload(payload, options = {}) {
  const errors = [];
  const nowEpochMillis = options.nowEpochMillis ?? Date.now();
  const maxAgeSeconds = options.maxAgeSeconds ?? 60 * 60;

  if (payload === null || typeof payload !== "object" || Array.isArray(payload)) {
    return ["采集结果必须是 JSON 对象"];
  }
  if (payload.schemaVersion !== 1) {
    errors.push(`不支持的 schemaVersion：${payload.schemaVersion}（期望 1）`);
  }

  const source = payload.source;
  if (!source || typeof source !== "object" || Array.isArray(source)) {
    errors.push("缺少 source");
  } else {
    if (source.id !== SOURCE_ID) {
      errors.push(`source.id 必须是 ${SOURCE_ID}，实际为 ${source.id}`);
    }
    if (source.sourceType !== "OFFICIAL_WEB") {
      errors.push(`source.sourceType 必须是 OFFICIAL_WEB，实际为 ${source.sourceType}`);
    }
    if (typeof source.sourceUrl !== "string" || source.sourceUrl.trim() === "") {
      errors.push("source.sourceUrl 不能为空");
    }
    if (
      typeof source.titleOrSummary !== "string" ||
      source.titleOrSummary.trim() === ""
    ) {
      errors.push("source.titleOrSummary 不能为空");
    }
    if (!Number.isFinite(source.fetchedAtEpochMillis) || source.fetchedAtEpochMillis <= 0) {
      errors.push("source.fetchedAtEpochMillis 必须是正整数");
    } else {
      const ageMillis = nowEpochMillis - source.fetchedAtEpochMillis;
      if (ageMillis > maxAgeSeconds * 1000) {
        errors.push(
          `采集数据已过期：抓取于 ${new Date(source.fetchedAtEpochMillis).toISOString()}，` +
            `超过 ${maxAgeSeconds}s`
        );
      }
      if (ageMillis < -MAX_FUTURE_SKEW_MS) {
        errors.push(
          `采集数据抓取时间在未来：${new Date(source.fetchedAtEpochMillis).toISOString()}`
        );
      }
    }
  }

  const products = Array.isArray(payload.products) ? payload.products : [];
  const plans = Array.isArray(payload.plans) ? payload.plans : [];
  const offers = Array.isArray(payload.offers) ? payload.offers : [];

  if (products.length !== 3) {
    errors.push(`products 必须为 3 档，实际为 ${products.length}`);
  }
  if (plans.length !== 3) {
    errors.push(`plans 必须为 3 条，实际为 ${plans.length}`);
  }
  if (offers.length !== 9) {
    errors.push(`offers 必须为 3 档 × 3 周期 = 9 条，实际为 ${offers.length}`);
  }

  checkUniqueIds("products", products, errors);
  checkUniqueIds("plans", plans, errors);
  checkUniqueIds("offers", offers, errors);

  const productIds = new Set(products.map((product) => product?.id));
  const planById = new Map(plans.map((plan) => [plan?.id, plan]));
  for (const product of products) {
    if (!product || typeof product !== "object") {
      errors.push("products 中存在非对象条目");
      continue;
    }
    if (typeof product.id !== "string" || product.id.startsWith("demo-")) {
      errors.push(`产品 ID 非法：${product.id}`);
    }
    if (product.isDemo !== false) {
      errors.push(`产品 ${product.id} 必须是 isDemo=false`);
    }
  }
  for (const plan of plans) {
    if (!plan || typeof plan !== "object") {
      errors.push("plans 中存在非对象条目");
      continue;
    }
    if (!productIds.has(plan.productId)) {
      errors.push(`套餐 ${plan.id} 引用了不存在的 productId ${plan.productId}`);
    }
  }

  const periodsByProduct = new Map();
  for (const offer of offers) {
    if (!offer || typeof offer !== "object") {
      errors.push("offers 中存在非对象条目");
      continue;
    }
    const label = offer.id ?? "(无 id)";
    if (typeof offer.id !== "string" || offer.id.startsWith("demo-")) {
      errors.push(`offer ${label} 的 ID 非法`);
    }
    if (!productIds.has(offer.productId)) {
      errors.push(`offer ${label} 引用了不存在的 productId ${offer.productId}`);
    }
    const plan = planById.get(offer.planId);
    if (!plan) {
      errors.push(`offer ${label} 引用了不存在的 planId ${offer.planId}`);
    } else if (plan.productId !== offer.productId) {
      errors.push(`offer ${label} 的 planId 与 productId 不匹配`);
    }
    if (!PERIODS.has(offer.billingPeriod)) {
      errors.push(`offer ${label} 的 billingPeriod 非法：${offer.billingPeriod}`);
    } else {
      const periods = periodsByProduct.get(offer.productId) ?? new Set();
      periods.add(offer.billingPeriod);
      periodsByProduct.set(offer.productId, periods);
    }
    if (offer.planType !== "STANDARD") {
      errors.push(`offer ${label} 的 planType 必须是 STANDARD`);
    }
    if (typeof offer.title !== "string" || offer.title.trim() === "") {
      errors.push(`offer ${label} 的 title 不能为空`);
    }
    if (offer.regionCode !== "CN") {
      errors.push(`offer ${label} 的 regionCode 必须是 CN`);
    }
    if (offer.currencyCode !== "CNY") {
      errors.push(`offer ${label} 的 currencyCode 必须是 CNY`);
    }
    if (!Number.isFinite(offer.actualAmountMinor) || offer.actualAmountMinor <= 0) {
      errors.push(`offer ${label} 的 actualAmountMinor 必须大于 0`);
    }
    if (
      !Number.isFinite(offer.monthlyEquivalentMinor) ||
      offer.monthlyEquivalentMinor <= 0
    ) {
      errors.push(`offer ${label} 的 monthlyEquivalentMinor 必须大于 0`);
    }
    if (
      !Number.isFinite(offer.originalAmountMinor) ||
      offer.originalAmountMinor < offer.actualAmountMinor
    ) {
      errors.push(`offer ${label} 的 originalAmountMinor 不能低于实际价`);
    }
    if (
      !offer.source ||
      offer.source.id !== source?.id ||
      offer.source.id !== SOURCE_ID
    ) {
      errors.push(`offer ${label} 的来源 ID 与 source 不匹配`);
    }
    if (typeof offer.officialUrl !== "string" || offer.officialUrl.trim() === "") {
      errors.push(`offer ${label} 缺少 officialUrl`);
    }
  }

  for (const product of products) {
    const periods = periodsByProduct.get(product?.id) ?? new Set();
    if (periods.size !== 3) {
      errors.push(
        `产品 ${product?.id} 必须覆盖 MONTHLY/QUARTERLY/YEARLY，实际为 ${[...periods].join(",")}`
      );
    }
  }

  return errors;
}

function checkUniqueIds(label, items, errors) {
  const seen = new Set();
  for (const item of items) {
    const id = item?.id;
    if (typeof id !== "string") {
      errors.push(`${label} 中存在缺少 id 的条目`);
      continue;
    }
    if (seen.has(id)) {
      errors.push(`${label} 中存在重复 id：${id}`);
    }
    seen.add(id);
  }
}
