import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { formatComparisonFacets } from "./comparison-presentation.ts";

const comparisonScreenSource = readFileSync(new URL("../components/comparison-screen.tsx", import.meta.url), "utf8");

test("상품 비교 특징은 내부 facet key를 고객에게 노출하지 않는다", () => {
  assert.equal(formatComparisonFacets(["material:실크", "usage:산책", "usage:산책"]), "실크 · 산책");
  assert.equal(formatComparisonFacets(["부드러운 소재"]), "부드러운 소재");
  assert.equal(formatComparisonFacets(["material:", ""]), "-");
  assert.match(comparisonScreenSource, /\["facets", "주요 특징"\]/);
  assert.doesNotMatch(comparisonScreenSource, /\["facets", "Facet"\]/);
});
