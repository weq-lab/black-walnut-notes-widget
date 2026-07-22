import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import type { Plugin } from "vite";
import { copyFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

function copyPublicShell(): Plugin {
  const root = fileURLToPath(new URL(".", import.meta.url));
  const publicRoot = resolve(root, "public");
  const outputRoot = resolve(root, "dist");
  const files = [
    "manifest.webmanifest",
    "sw.js",
    "icons/icon-192.png",
    "icons/icon-512.png",
    "icons/icon-maskable-512.png",
  ];
  return {
    name: "copy-public-shell",
    apply: "build",
    async closeBundle() {
      await Promise.all(files.map(async (relativePath) => {
        const target = resolve(outputRoot, relativePath);
        await mkdir(dirname(target), { recursive: true });
        await copyFile(resolve(publicRoot, relativePath), target);
      }));
    },
  };
}

export default defineConfig({
  publicDir: "public",
  plugins: [react(), copyPublicShell()],
  build: {
    sourcemap: false,
    target: "es2022",
  },
  test: {
    environment: "jsdom",
    include: ["src/tests/**/*.test.ts"],
    restoreMocks: true,
  },
});
