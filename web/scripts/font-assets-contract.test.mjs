import test from "node:test";
import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { resolve } from "node:path";
import { fontAssetsContract, licensePaths } from "./font-assets-contract.mjs";

const webRoot = resolve(import.meta.dirname, "..");

const expectedRoles = [
  "Cormorant Garamond Regular 400",
  "Cormorant Garamond SemiBold 600",
  "MaruBuri Regular 400",
  "MaruBuri SemiBold 600",
];

test("Android와 PWA가 같은 네 폰트 역할만 요구한다", () => {
  assert.deepEqual(fontAssetsContract.android.map(([role]) => role), expectedRoles);
  assert.deepEqual(fontAssetsContract.web.map(([role]) => role), expectedRoles);
});

test("공식 원본 형식만 허용하고 외부 URL은 포함하지 않는다", () => {
  const paths = [
    ...fontAssetsContract.android.map(([, path]) => path),
    ...fontAssetsContract.web.map(([, path]) => path),
    ...licensePaths,
  ];
  assert.equal(paths.some((path) => /^https?:/i.test(path)), false);
  assert.equal(paths.filter((path) => /\.(?:ttf|otf|woff2)$/i.test(path)).length, 8);
  assert.equal(paths.some((path) => /\.(?:woff|eot|zip|part|crdownload)$/i.test(path)), false);
});

test("플랫폼별 폰트 파일은 정확히 네 개다", () => {
  assert.equal(fontAssetsContract.android.length, 4);
  assert.equal(fontAssetsContract.web.length, 4);
});

test("PWA 스타일과 서비스 워커가 네 로컬 폰트를 오프라인 캐시한다", async () => {
  const css = await readFile(resolve(webRoot, "src/styles/app.css"), "utf8");
  const serviceWorker = await readFile(resolve(webRoot, "public/sw.js"), "utf8");
  const bundled = (await Promise.all([
    readdir(resolve(webRoot, "src/assets/fonts/cormorant-garamond")),
    readdir(resolve(webRoot, "src/assets/fonts/maruburi")),
  ])).flat();
  assert.equal(bundled.filter((name) => /\.(?:ttf|otf|woff2?)$/i.test(name)).length, 4);
  assert.equal((css.match(/@font-face/g) ?? []).length, 4);
  assert.match(css, /"Segoe UI", "Malgun Gothic", system-ui, sans-serif/);
  assert.match(serviceWorker, /fontAssets/);
  assert.match(serviceWorker, /cache\.addAll\(\[\.\.\.new Set\(fontAssets\)\]\)/);
});
