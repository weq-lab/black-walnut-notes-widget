import { readFile, writeFile } from "node:fs/promises";

const path = new URL("../dist/sw.js", import.meta.url);
const source = await readFile(path, "utf8");
if (!source.includes("__BUILD_VERSION__")) throw new Error("Service worker build token is missing.");
await writeFile(path, source.replaceAll("__BUILD_VERSION__", Date.now().toString(36)), "utf8");
