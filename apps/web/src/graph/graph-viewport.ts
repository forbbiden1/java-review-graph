import type { MutableRefObject } from "react";
import type { Viewport } from "@xyflow/react";

import type { StoredGraphScene } from "../platform";
import {
  DEFAULT_VIEWPORT,
  SAFE_MIN_ZOOM,
  StagePoint,
  StageSize,
  VIEWPORT_PADDING,
  ZOOM_SLIDER_MAX,
  ZOOM_SLIDER_MIN,
  ZOOM_SLIDER_SCALE
} from "./graph-model";
import type { LayoutResult } from "./graph-model";

export function resolveInitialViewport(layoutResult: LayoutResult, stageSize: StageSize): Viewport {
  if (stageSize.width === 0 || stageSize.height === 0) {
    return DEFAULT_VIEWPORT;
  }

  const width = Math.max(layoutResult.bounds.width, 220);
  const height = Math.max(layoutResult.bounds.height, 180);
  const fitWidth = Math.max((stageSize.width - VIEWPORT_PADDING) / width, SAFE_MIN_ZOOM);
  const fitHeight = Math.max((stageSize.height - VIEWPORT_PADDING) / height, SAFE_MIN_ZOOM);
  const zoom = sanitizeZoom(Math.min(fitWidth, fitHeight));
  const centerX = layoutResult.bounds.x + layoutResult.bounds.width / 2;
  const centerY = layoutResult.bounds.y + layoutResult.bounds.height / 2;

  return {
    x: stageSize.width / 2 - centerX * zoom,
    y: stageSize.height / 2 - centerY * zoom,
    zoom
  };
}

export function resolveCenteredNodeViewport(layoutResult: LayoutResult, stageSize: StageSize, zoom: number, nodeId: string): Viewport {
  if (stageSize.width === 0 || stageSize.height === 0) {
    return DEFAULT_VIEWPORT;
  }

  const focusedNode = layoutResult.nodes.find((node) => node.id === nodeId);
  if (!focusedNode) {
    return resolveInitialViewport(layoutResult, stageSize);
  }

  return {
    x: stageSize.width / 2 - (focusedNode.position.x + focusedNode.data.size.width / 2) * sanitizeZoom(zoom),
    y: stageSize.height / 2 - (focusedNode.position.y + focusedNode.data.size.height / 2) * sanitizeZoom(zoom),
    zoom: sanitizeZoom(zoom)
  };
}

export function sanitizeStoredViewport(view: StoredGraphScene["view"]): Viewport {
  return {
    x: Number.isFinite(view.offsetX) ? view.offsetX : 0,
    y: Number.isFinite(view.offsetY) ? view.offsetY : 0,
    zoom: sanitizeZoom(view.zoom)
  };
}

export function zoomViewport(
  currentViewport: Viewport,
  originX: number,
  originY: number,
  resolveNextZoom: (currentZoom: number) => number,
  stageSize: StageSize
): Viewport {
  const currentZoom = sanitizeZoom(currentViewport.zoom);
  const nextZoom = sanitizeZoom(resolveNextZoom(currentZoom));
  if (Math.abs(nextZoom - currentZoom) < 0.0000001) {
    return currentViewport;
  }

  const boundedOriginX = clamp(originX, 0, stageSize.width);
  const boundedOriginY = clamp(originY, 0, stageSize.height);
  const worldX = (boundedOriginX - currentViewport.x) / currentZoom;
  const worldY = (boundedOriginY - currentViewport.y) / currentZoom;

  return {
    x: boundedOriginX - worldX * nextZoom,
    y: boundedOriginY - worldY * nextZoom,
    zoom: nextZoom
  };
}

export function updateStagePointer(
  pointerRef: MutableRefObject<StagePoint | null>,
  stageRef: MutableRefObject<HTMLDivElement | null>,
  clientX: number,
  clientY: number
) {
  const stage = stageRef.current;
  if (!stage) {
    return;
  }

  const rect = stage.getBoundingClientRect();
  pointerRef.current = {
    x: clientX - rect.left,
    y: clientY - rect.top
  };
}

export function measureViewportCenter(stageSize: StageSize) {
  return {
    x: stageSize.width / 2,
    y: stageSize.height / 2
  };
}

export function sanitizeZoom(value: number) {
  if (!Number.isFinite(value) || value <= SAFE_MIN_ZOOM) {
    return SAFE_MIN_ZOOM;
  }
  return value;
}

export function zoomToSliderValue(zoom: number) {
  const rawValue = Math.log(sanitizeZoom(zoom)) * ZOOM_SLIDER_SCALE;
  return clamp(Math.round(rawValue), ZOOM_SLIDER_MIN, ZOOM_SLIDER_MAX);
}

export function sliderValueToZoom(value: number) {
  return sanitizeZoom(Math.exp(value / ZOOM_SLIDER_SCALE));
}

export function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}
