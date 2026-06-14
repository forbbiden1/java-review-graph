import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.indexOf("@xyflow/react") >= 0) {
            return "graph-flow";
          }
          if (id.indexOf("reactflow") >= 0 || id.indexOf("@reactflow") >= 0) {
            return "graph-flow";
          }
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  }
});
