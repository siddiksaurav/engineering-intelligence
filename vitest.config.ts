import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// Load .env.local (and .env*) so integration tests can reach the local
// Supabase stack via process.env. loadEnv with an empty prefix returns every
// key regardless of NEXT_PUBLIC_ prefixing.
const env = loadEnv("test", process.cwd(), "");

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.ts"],
    env,
  },
});
