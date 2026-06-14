import ELK from "elkjs/lib/elk-api.js";
import elkWorkerUrl from "elkjs/lib/elk-worker.min.js?url";
import { MarkerType, Position } from "@xyflow/react";

import type { GraphEdge, GraphNode } from "../api/client";
import {
  ELK_LAYER_SPACING,
  ELK_NODE_SPACING,
  ELK_PADDING,
  FlowEdge,
  FlowNode,
  FlowNodeData,
  GraphScopeMode,
  LayoutBounds,
  LayoutResult,
  NODE_CHAR_WIDTH,
  NODE_CHARS_PER_LINE,
  NODE_HORIZONTAL_PADDING,
  NODE_KIND_CHAR_WIDTH,
  NODE_MAX_WIDTH,
  NODE_MIN_HEIGHT,
  NODE_MIN_WIDTH,
  NODE_VERTICAL_PADDING,
  NodeOverrideMap,
  ScopedGraph
} from "./graph-model";

const elk = new ELK({ workerUrl: elkWorkerUrl });

export async function buildElkLayout(
  nodes: GraphNode[],
  edges: GraphEdge[],
  nodeOverrides: NodeOverrideMap,
  formatNodeKind: (kind: string) => string,
  formatEdgeType: (edgeType: string) => string,
  selectedNodeId: string | null,
  onPointerDown: (nodeId: string, clientX: number, clientY: number) => void,
  onPointerUp: (nodeId: string, clientX: number, clientY: number) => void
): Promise<LayoutResult> {
  if (nodes.length === 0) {
    return {
      bounds: { height: 0, width: 0, x: 0, y: 0 },
      edges: [],
      focusNodeId: null,
      nodes: []
    };
  }

  const orderedNodes = nodes.slice().sort(compareGraphNodes);
  const nodeSizes = new Map<string, { height: number; width: number }>(
    orderedNodes.map((node) => [node.id, estimateNodeSize(node, formatNodeKind(node.kind))])
  );
  const visibleNodeIds = new Set(orderedNodes.map((node) => node.id));
  const filteredEdges = edges.filter(
    (edge) => visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target) && edge.source !== edge.target
  );

  const elkGraph = {
    id: "root",
    layoutOptions: {
      "elk.algorithm": "layered",
      "elk.direction": "RIGHT",
      "elk.layered.considerModelOrder.strategy": "NODES_AND_EDGES",
      "elk.layered.spacing.nodeNodeBetweenLayers": String(ELK_LAYER_SPACING),
      "elk.spacing.nodeNode": String(ELK_NODE_SPACING),
      "elk.padding": `[top=${ELK_PADDING},left=${ELK_PADDING},bottom=${ELK_PADDING},right=${ELK_PADDING}]`
    },
    children: orderedNodes.map((node) => {
      const size = nodeSizes.get(node.id)!;
      return {
        id: node.id,
        height: size.height,
        width: size.width
      };
    }),
    edges: filteredEdges.map((edge, index) => ({
      id: `edge:${edge.source}:${edge.target}:${edge.type}:${index}`,
      sources: [edge.source],
      targets: [edge.target]
    }))
  };

  const layout = await elk.layout(elkGraph);
  const layoutChildren = new Map((layout.children ?? []).map((child) => [child.id, child]));
  const degreeById = buildDegreeMap(orderedNodes, filteredEdges);

  const flowNodes = orderedNodes.map((node) => {
    const child = layoutChildren.get(node.id);
    const size = nodeSizes.get(node.id)!;
    const defaultPosition = {
      x: Number.isFinite(child?.x) ? Number(child?.x) : ELK_PADDING,
      y: Number.isFinite(child?.y) ? Number(child?.y) : ELK_PADDING
    };
    const override = nodeOverrides[node.id];
    const position = override ?? defaultPosition;

    return {
      id: node.id,
      type: "graphNode",
      position,
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      data: {
        kindLabel: formatNodeKind(node.kind),
        nodeId: node.id,
        onPointerDown,
        onPointerUp,
        rawNode: node,
        size
      },
      draggable: true,
      selectable: true,
      selected: node.id === selectedNodeId,
      zIndex: node.id === selectedNodeId ? 2 : 1
    } satisfies FlowNode;
  });

  const flowEdges = filteredEdges.map((edge, index) => {
    const appearance = edgeAppearance(edge.type);
    return {
      id: `flow-edge:${edge.source}:${edge.target}:${edge.type}:${index}`,
      type: "graphEdge",
      source: edge.source,
      target: edge.target,
      data: {
        edgeType: edge.type,
        label: formatEdgeType(edge.type)
      },
      markerEnd: {
        color: appearance.stroke,
        type: MarkerType.ArrowClosed
      },
      selectable: false,
      style: {
        stroke: appearance.stroke,
        strokeDasharray: appearance.strokeDasharray,
        strokeLinecap: "round",
        strokeWidth: 1.45
      }
    } satisfies FlowEdge;
  });

  return {
    bounds: measureBounds(flowNodes, nodeSizes),
    edges: flowEdges,
    focusNodeId: pickFocusNodeId(orderedNodes, degreeById),
    nodes: flowNodes
  };
}

export function buildScopedGraph(
  nodes: GraphNode[],
  edges: GraphEdge[],
  scopedNodeId: string | null,
  scopeMode: GraphScopeMode
): ScopedGraph {
  if (!scopedNodeId) {
    return {
      edges,
      focusNode: null,
      nodes
    };
  }

  const focusNode = nodes.find((node) => node.id === scopedNodeId) ?? null;
  if (!focusNode) {
    return {
      edges,
      focusNode: null,
      nodes
    };
  }

  if (scopeMode === "direct") {
    const scopedNodeIds = new Set<string>([scopedNodeId]);
    const scopedEdges = edges.filter((edge) => {
      const isRelatedEdge = edge.source === scopedNodeId || edge.target === scopedNodeId;
      if (!isRelatedEdge) {
        return false;
      }
      scopedNodeIds.add(edge.source);
      scopedNodeIds.add(edge.target);
      return true;
    });

    return {
      edges: scopedEdges,
      focusNode,
      nodes: nodes.filter((node) => scopedNodeIds.has(node.id))
    };
  }

  const adjacency = new Map<string, Set<string>>();
  nodes.forEach((node) => adjacency.set(node.id, new Set()));
  edges.forEach((edge) => {
    adjacency.get(edge.source)?.add(edge.target);
    adjacency.get(edge.target)?.add(edge.source);
  });

  const scopedNodeIds = new Set<string>([scopedNodeId]);
  const queue = [scopedNodeId];

  while (queue.length > 0) {
    const currentNodeId = queue.shift()!;
    adjacency.get(currentNodeId)?.forEach((nextNodeId) => {
      if (scopedNodeIds.has(nextNodeId)) {
        return;
      }
      scopedNodeIds.add(nextNodeId);
      queue.push(nextNodeId);
    });
  }

  return {
    edges: edges.filter((edge) => scopedNodeIds.has(edge.source) && scopedNodeIds.has(edge.target)),
    focusNode,
    nodes: nodes.filter((node) => scopedNodeIds.has(node.id))
  };
}

export function nodeStatusColor(status: string) {
  switch (status.toLowerCase()) {
    case "added":
      return "#1a7f37";
    case "modified_api":
      return "#bc4c00";
    case "modified_impl":
      return "#9a6700";
    case "deleted":
      return "#cf222e";
    case "impacted":
      return "#0969da";
    default:
      return "#8b949e";
  }
}

function edgeAppearance(edgeType: string) {
  switch (edgeType) {
    case "extends":
      return {
        stroke: "rgba(9, 105, 218, 0.62)",
        strokeDasharray: undefined
      };
    case "implements":
      return {
        stroke: "rgba(188, 76, 0, 0.62)",
        strokeDasharray: "8 5"
      };
    case "uses_type":
      return {
        stroke: "rgba(87, 96, 106, 0.5)",
        strokeDasharray: undefined
      };
    case "calls":
      return {
        stroke: "rgba(26, 127, 55, 0.46)",
        strokeDasharray: undefined
      };
    default:
      return {
        stroke: "rgba(87, 96, 106, 0.56)",
        strokeDasharray: undefined
      };
  }
}

function compareGraphNodes(left: GraphNode, right: GraphNode) {
  const leftLayer = Number.isFinite(left.layer) ? left.layer ?? 0 : Number.MAX_SAFE_INTEGER;
  const rightLayer = Number.isFinite(right.layer) ? right.layer ?? 0 : Number.MAX_SAFE_INTEGER;
  if (leftLayer !== rightLayer) {
    return leftLayer - rightLayer;
  }

  const leftPlacement = left.placement === "cycle_side" ? 1 : 0;
  const rightPlacement = right.placement === "cycle_side" ? 1 : 0;
  if (leftPlacement !== rightPlacement) {
    return leftPlacement - rightPlacement;
  }

  const leftOrder = Number.isFinite(left.order) ? left.order ?? 0 : Number.MAX_SAFE_INTEGER;
  const rightOrder = Number.isFinite(right.order) ? right.order ?? 0 : Number.MAX_SAFE_INTEGER;
  if (leftOrder !== rightOrder) {
    return leftOrder - rightOrder;
  }

  const leftGroupOrder = Number.isFinite(left.groupOrder) ? left.groupOrder ?? 0 : Number.MAX_SAFE_INTEGER;
  const rightGroupOrder = Number.isFinite(right.groupOrder) ? right.groupOrder ?? 0 : Number.MAX_SAFE_INTEGER;
  if (leftGroupOrder !== rightGroupOrder) {
    return leftGroupOrder - rightGroupOrder;
  }

  const leftStatusWeight = statusWeight(left.status);
  const rightStatusWeight = statusWeight(right.status);
  if (leftStatusWeight !== rightStatusWeight) {
    return rightStatusWeight - leftStatusWeight;
  }

  return left.name.localeCompare(right.name);
}

function statusWeight(status: string) {
  switch (status.toLowerCase()) {
    case "added":
      return 6;
    case "modified_api":
      return 5;
    case "impacted":
      return 4;
    case "modified_impl":
      return 3;
    case "deleted":
      return 2;
    default:
      return 1;
  }
}

function buildDegreeMap(nodes: GraphNode[], edges: GraphEdge[]) {
  const adjacency = new Map(nodes.map((node) => [node.id, new Set<string>()]));
  edges.forEach((edge) => {
    adjacency.get(edge.source)?.add(edge.target);
    adjacency.get(edge.target)?.add(edge.source);
  });
  return new Map(nodes.map((node) => [node.id, adjacency.get(node.id)?.size ?? 0]));
}

function pickFocusNodeId(nodes: GraphNode[], degreeById: Map<string, number>) {
  const rankedNodes = nodes.slice().sort((left, right) => {
    const selected = compareGraphNodes(left, right);
    if (selected !== 0) {
      return selected;
    }
    return (degreeById.get(right.id) ?? 0) - (degreeById.get(left.id) ?? 0);
  });
  return rankedNodes[0]?.id ?? null;
}

function estimateNodeSize(node: GraphNode, kindLabel: string) {
  const estimatedLineCount = Math.max(1, Math.ceil(node.name.length / NODE_CHARS_PER_LINE));
  const widestLine = Math.max(node.name.length * NODE_CHAR_WIDTH, kindLabel.length * NODE_KIND_CHAR_WIDTH);
  return {
    height: Math.max(NODE_MIN_HEIGHT, NODE_VERTICAL_PADDING + estimatedLineCount * 18 + 18),
    width: clamp(widestLine + NODE_HORIZONTAL_PADDING, NODE_MIN_WIDTH, NODE_MAX_WIDTH)
  };
}

function measureBounds(nodes: FlowNode[], nodeSizes: Map<string, { height: number; width: number }>): LayoutBounds {
  if (nodes.length === 0) {
    return { height: 0, width: 0, x: 0, y: 0 };
  }

  const initial = {
    minX: Number.POSITIVE_INFINITY,
    maxX: Number.NEGATIVE_INFINITY,
    minY: Number.POSITIVE_INFINITY,
    maxY: Number.NEGATIVE_INFINITY
  };

  const bounds = nodes.reduce((currentBounds, node) => {
    const size = nodeSizes.get(node.id) ?? { height: NODE_MIN_HEIGHT, width: NODE_MIN_WIDTH };
    return {
      minX: Math.min(currentBounds.minX, node.position.x),
      maxX: Math.max(currentBounds.maxX, node.position.x + size.width),
      minY: Math.min(currentBounds.minY, node.position.y),
      maxY: Math.max(currentBounds.maxY, node.position.y + size.height)
    };
  }, initial);

  return {
    height: bounds.maxY - bounds.minY,
    width: bounds.maxX - bounds.minX,
    x: bounds.minX,
    y: bounds.minY
  };
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}
