import { readFile, stat } from "node:fs/promises";
import { resolve } from "node:path";
import { fontAssetsContract, licensePaths } from "./font-assets-contract.mjs";

const webRoot = resolve(import.meta.dirname, "..");
const missing = [];

async function isUsableFont(relativePath) {
  const path = resolve(webRoot, relativePath);
  try {
    const info = await stat(path);
    if (!info.isFile() || info.size === 0) return false;
    const handle = await readFile(path);
    return handle.subarray(0, 4).equals(Buffer.from([0, 1, 0, 0]));
  } catch {
    return false;
  }
}

for (const [platform, assets] of Object.entries(fontAssetsContract)) {
  for (const [role, relativePath] of assets) {
    if (!(await isUsableFont(relativePath))) missing.push(`${platform} · ${role}: ${relativePath}`);
  }
}

for (const relativePath of licensePaths) {
  try {
    if ((await stat(resolve(webRoot, relativePath))).size === 0) missing.push(`라이선스: ${relativePath}`);
  } catch {
    missing.push(`라이선스: ${relativePath}`);
  }
}

if (missing.length) {
  console.error(["필수 공식 폰트 자산 검증에 실패했습니다.", ...missing.map((item) => `- 누락/손상: ${item}`)].join("\n"));
  process.exitCode = 1;
} else {
  console.log("Android와 PWA의 공식 TTF 네 파일 및 라이선스 문서를 확인했습니다.");
}
