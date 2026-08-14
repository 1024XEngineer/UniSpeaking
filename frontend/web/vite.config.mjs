import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { injectUmamiScript } from "./src/analytics/umamiConfig.js";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    optimizeDeps: {
      include: ["react", "react-dom/client"],
    },
    server: {
      host: "0.0.0.0",
      port: 5174,
      strictPort: true,
      allowedHosts: ["terminal.local", "127.0.0.1", "localhost", "100.100.57.60"],
      proxy: {
        "/api": "http://127.0.0.1:8080",
        "/ws": {
          target: "ws://127.0.0.1:8080",
          ws: true,
        },
        // Keep the production `/backend` base URL usable during local Vite development.
        "/backend/ws": {
          target: "ws://127.0.0.1:8080",
          ws: true,
          rewrite: (path) => path.replace(/^\/backend/, ""),
        },
        "/backend": {
          target: "http://127.0.0.1:8080",
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/backend/, ""),
        },
      },
      warmup: {
        clientFiles: ["./src/controller/main.jsx"],
      },
    },
    plugins: [
      react(),
      {
        name: "unispeaking-umami",
        transformIndexHtml(html) {
          return injectUmamiScript(html, env);
        },
      },
    ],
  };
});
