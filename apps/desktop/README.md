# Desktop Workspace

This Electron workspace wraps the existing React review UI from `apps/web`.

## Scripts

```bash
npm install
npm run dev
```

The renderer reuses the browser UI code, while desktop-only settings are exposed through a preload bridge.
