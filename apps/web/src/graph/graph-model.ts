import type { Edge, Node, Viewport } from "@xyflow/react";

import type { GraphEdge, GraphNode } from "../api/client";

export type StageSize = {
  height: number;
  width: number;
};

export type StagePoint = {
  x: number;
  y: number;
};

export type LayoutBounds = {
  height: number;
  width: number;
  x: number;
  y: number;
};

export type NodeOverrideMap = Record<string, { x: number; y: number }>;

export type GraphScopeMode = "connected" | "direct";

export type ScopedGraph = {
  edges: GraphEdge[];
  focusNode: GraphNode | null;
  nodes: GraphNode[];
};

export type FlowNodeData = {
  kindLabel: string;
  nodeId: string;
  onPointerDown: (nodeId: string, clientX: number, clientY: number) => void;
  onPointerUp: (nodeId: string, clientX: number, clientY: number) => void;
  rawNode: GraphNode;
  size: {
    height: number;
    width: number;
  };
};

export type FlowNode = Node<FlowNodeData, "graphNode">;

export type FlowEdgeData = {
  edgeType: string;
  label: string;
};

export type FlowEdge = Edge<FlowEdgeData, "graphEdge">;

export type LayoutResult = {
  bounds: LayoutBounds;
  edges: FlowEdge[];
  focusNodeId: string | null;
  nodes: FlowNode[];
};

export const SAFE_MIN_ZOOM = 0.000001;
export const VIEWPORT_PADDING = 160;
export const ZOOM_SLIDER_MIN = -2000;
export const ZOOM_SLIDER_MAX = 2000;
export const ZOOM_SLIDER_SCALE = 80;
export const DEFAULT_VIEWPORT: Viewport = { x: 0, y: 0, zoom: 1 };
export const NODE_HORIZONTAL_PADDING = 28;
export const NODE_VERTICAL_PADDING = 22;
export const NODE_MIN_WIDTH = 164;
export const NODE_MAX_WIDTH = 340;
export const NODE_MIN_HEIGHT = 62;
export const NODE_CHAR_WIDTH = 7.2;
export const NODE_KIND_CHAR_WIDTH = 5.4;
export const NODE_CHARS_PER_LINE = 24;
export const ELK_PADDING = 96;
export const ELK_NODE_SPACING = 128;
export const ELK_LAYER_SPACING = 320;
