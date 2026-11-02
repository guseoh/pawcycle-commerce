import test from "node:test";
import assert from "node:assert/strict";
import { categoryNavigationIndex } from "./category-navigation.ts";

test("category parent arrows wrap and Home/End select endpoints", () => {
  assert.equal(categoryNavigationIndex("ArrowDown", 3, 4), 0);
  assert.equal(categoryNavigationIndex("ArrowUp", 0, 4), 3);
  assert.equal(categoryNavigationIndex("Home", 2, 4), 0);
  assert.equal(categoryNavigationIndex("End", 1, 4), 3);
});
test("empty discovery and native Tab do not select a parent", () => {
  assert.equal(categoryNavigationIndex("ArrowDown", -1, 0), -1);
  assert.equal(categoryNavigationIndex("Tab", 0, 4), -1);
  assert.equal(categoryNavigationIndex("Enter", 0, 4), -1);
});
