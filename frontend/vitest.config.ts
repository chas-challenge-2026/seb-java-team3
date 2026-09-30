import { configDefaults, defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config.ts";

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      // e2e/ körs av Playwright, inte Vitest
      exclude: [...configDefaults.exclude, "e2e/**"],
      coverage: {
        provider: "v8",
        reporter: ["text", "html"],
        exclude: ["src/main.tsx", "src/test/**"],
      },
    },
  }),
);
