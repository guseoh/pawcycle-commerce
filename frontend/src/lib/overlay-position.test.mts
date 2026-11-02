import test from "node:test";
import assert from "node:assert/strict";
import { desktopOverlayPosition } from "./overlay-position.ts";

test("desktop overlay follows the trigger and clamps the right edge at 1024px", () => {
  const position = desktopOverlayPosition({ left: 900, top: 200, bottom: 244 }, { width: 320, height: 300 }, { width: 1024, height: 768 });
  assert.equal(position.left, 688);
  assert.equal(position.top, 252);
  assert.ok(position.top + Math.min(300, position.maxHeight) <= 752);
});

test("a trigger near the bottom opens upward within the viewport", () => {
  const position = desktopOverlayPosition({ left: 100, top: 680, bottom: 724 }, { width: 320, height: 400 }, { width: 1280, height: 800 });
  assert.equal(position.top, 272);
  assert.ok(position.top + 400 < 680);
});

test("a tall overlay is scroll bounded and an offscreen left anchor is clamped", () => {
  const position = desktopOverlayPosition({ left: -40, top: 80, bottom: 124 }, { width: 320, height: 900 }, { width: 1440, height: 900 });
  assert.equal(position.left, 16);
  assert.equal(position.maxHeight, 480);
});

test("a tall upward overlay is bounded by the space above in a short desktop viewport", () => {
  const position = desktopOverlayPosition({ left: 700, top: 500, bottom: 544 }, { width: 320, height: 900 }, { width: 1024, height: 768 });
  assert.equal(position.top, 16);
  assert.equal(position.maxHeight, 476);
  assert.equal(position.left, 688);
});
