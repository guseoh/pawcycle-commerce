import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");
const accountCss = source("../app/account-visual-v2.css");

test("G6-01: address actions remain outside the scrolling fields and failures stay in the dialog", () => {
  const address = source("../app/addresses/page.tsx");
  const body = address.indexOf('className="address-dialog-body form-section"');
  const actions = address.indexOf('className="button-row address-dialog-actions"');
  assert.ok(body > 0 && actions > body);
  assert.match(address.slice(body, actions), /error.*role="alert"/);
  assert.match(address.slice(body, actions), /disabled=\{busy\} required=\{required\}/);
  assert.match(accountCss, /\.address-dialog-body\s*\{[^}]*min-height:0;[^}]*overflow-y:auto/);
  assert.match(accountCss, /\.address-dialog-actions\s*\{[^}]*flex:none/);
  assert.match(address, /onCancel=.*closeForm\(\)/);
  assert.match(address, /formOpener\.current\?\.focus\(\)/);
});

test("G6-01: pet edit focus enters the input and returns only on explicit completion", () => {
  const pet = source("./pet-profile-screen.tsx");
  assert.match(pet, /if \(editing\) editNameRef\.current\?\.focus\(\)/);
  assert.match(pet, /restoreEditFocus\.current = petId/);
  assert.match(pet, /node && restoreEditFocus\.current === pet\.petId/);
  assert.match(pet, /onCancel=\{\(\) => finishEdit\(pet\.petId\)\}/);
  assert.equal((pet.match(/finishEdit\(pet\.petId\)/g) ?? []).length, 3);
  assert.match(pet, /subscriptionApi\.pets\.patch\(pet\.petId, changes, csrf\)/);
});
