import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// scripts/dev.js tells Vite which port the Spring Boot API ended up on.
const apiPort = process.env.API_PORT || '8080';

export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: { port: 5173, proxy: { '/api': `http://localhost:${apiPort}` } },
  // Spring Boot serves the production build from its static folder.
  build: { outDir: '../backend/src/main/resources/static', emptyOutDir: true },
});
