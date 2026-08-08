import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    /*
     * Everything previously landed in one ~1.25MB chunk, so a first visit downloaded the charting
     * library and the entire component library before anything could render. Splitting the large,
     * rarely-changing vendor libraries into their own chunks lets them stay cached across deploys,
     * while application code — which changes far more often — invalidates on its own.
     *
     * This uses Rolldown's `advancedChunks` (Vite 8's bundler) rather than Rollup's `manualChunks`,
     * which is not part of the Rolldown options surface.
     */
    rolldownOptions: {
      output: {
        advancedChunks: {
          groups: [
            { name: 'vendor-react', test: /node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/ },
            // Recharts pulls in d3; only chart-bearing pages need it.
            { name: 'vendor-charts', test: /node_modules[\\/](recharts|d3-|victory-|decimal\.js)/ },
            { name: 'vendor-mui', test: /node_modules[\\/](@mui|@emotion)[\\/]/ },
          ],
        },
      },
    },
    chunkSizeWarningLimit: 700,
  },
});
