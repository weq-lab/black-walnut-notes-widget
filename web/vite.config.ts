import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
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
