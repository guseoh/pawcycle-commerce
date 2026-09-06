import { readdirSync } from "node:fs";
import { dirname, extname, join, relative, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sourceRoot = join(frontendRoot, "src");

function testFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return testFiles(path);
    return entry.isFile() && extname(entry.name) === ".mts" && entry.name.endsWith(".test.mts") ? [path] : [];
  });
}

const files = testFiles(sourceRoot).sort((left, right) => relative(frontendRoot, left).localeCompare(relative(frontendRoot, right)));
if (files.length === 0) {
  console.error("No frontend test files were found.");
  process.exit(1);
}

const result = spawnSync(process.execPath, ["--experimental-strip-types", "--test", ...files], { stdio: "inherit" });
process.exit(result.status ?? 1);
