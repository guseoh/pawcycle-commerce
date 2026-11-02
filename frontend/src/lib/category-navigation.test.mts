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

test("mixed link and button navigation moves focus without activating links", () => {
  const items = ["link", "button", "link"];
  assert.equal(items[categoryNavigationIndex("ArrowDown", 0, items.length)], "button");
  assert.equal(items[categoryNavigationIndex("End", 1, items.length)], "link");
  assert.equal(categoryNavigationIndex("Enter", 2, items.length), -1);
});
