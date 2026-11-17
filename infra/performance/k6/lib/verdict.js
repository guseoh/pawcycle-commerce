// Shared response-contract and verdict helpers for local k6 cohorts.
// These helpers only judge responses and summarise results; they never change
// workload shape, request rate, duration, VU envelope, or dataset.

function jsonBody(response) {
  try {
    return response.json();
  } catch (error) {
    return undefined;
  }
}

// GET /api/products → ProductListView(items, page, size, totalElements, totalPages)
export function validProductList(response) {
  if (response.status !== 200) return false;
  const body = jsonBody(response);
  return !!body
    && Array.isArray(body.items)
    && Number.isInteger(body.page)
    && Number.isInteger(body.size)
    && typeof body.totalElements === "number"
    && body.items.every((item) => item && item.productId !== undefined && item.productId !== null);
}

// GET /api/products/{productId} → ProductDetailView(productId, ..., skus, ...)
export function validProductDetail(response, productId) {
  if (response.status !== 200) return false;
  const body = jsonBody(response);
  return !!body
    && String(body.productId) === String(productId)
    && Array.isArray(body.skus);
}

// GET /products → server-rendered HTML document
export function validHtmlPage(response) {
  if (response.status !== 200) return false;
  const contentType = String(response.headers["Content-Type"] || response.headers["content-type"] || "");
  const body = typeof response.body === "string" ? response.body : "";
  return contentType.toLowerCase().includes("text/html") && /<html[\s>]/i.test(body);
}

// Collects every threshold result k6 evaluated. k6 exits 99 when any fails;
// this makes the same verdict visible in the saved JSON summary.
export function thresholdVerdict(data) {
  const failed = [];
  let evaluated = 0;
  Object.entries(data.metrics || {}).forEach(([metric, value]) => {
    Object.entries(value.thresholds || {}).forEach(([expression, result]) => {
      evaluated += 1;
      if (!result.ok) failed.push(`${metric}: ${expression}`);
    });
  });
  return { passed: evaluated > 0 && failed.length === 0, evaluatedThresholds: evaluated, failedThresholds: failed };
}

// Open-model (arrival-rate) accounting. `expected` is the scheduled iteration
// count for the measurement window; `completed` counts measured responses.
// `notCompleted` covers both dropped iterations and iterations cut off by
// gracefulStop, which k6 does not report separately.
export function arrivalAccounting(data, expected, completed) {
  const dropped = data.metrics.dropped_iterations ? data.metrics.dropped_iterations.values.count : 0;
  return {
    scheduledIterations: expected,
    completedIterations: completed,
    droppedIterations: dropped,
    notCompletedIterations: Math.max(0, expected - completed),
  };
}
