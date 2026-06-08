import type { DesktopBridge } from "./platform";

declare global {
  interface Window {
    javaReviewGraphDesktop?: DesktopBridge;
  }
}

export {};
