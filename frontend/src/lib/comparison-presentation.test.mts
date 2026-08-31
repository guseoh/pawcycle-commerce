import assert from "node:assert/strict";
import test from "node:test";
import { formatComparisonFacets } from "./comparison-presentation.ts";

test("상품 비교 특징은 내부 facet key를 고객에게 노출하지 않는다", () => {
  assert.equal(formatComparisonFacets(["material:실크", "usage:산책", "usage:산책"]), "실크 · 산책");
  assert.equal(formatComparisonFacets(["부드러운 소재"]), "부드러운 소재");
  assert.equal(formatComparisonFacets(["material:", ""]), "-");
});
