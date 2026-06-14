import type { GraphEdge, GraphNode } from "../api/client";

export const LARGE_GRAPH_NODE_THRESHOLD = 180;
export const LARGE_GRAPH_PREVIEW_NODE_LIMIT = 140;

type ProgressiveGraph = {
  edges: GraphEdge[];
  hiddenNodeCount: number;
  isLimited: boolean;
  nodes: GraphNode[];
};

export function buildProgressiveGraph(
  nodes: GraphNode[],
  edges: GraphEdge[],
  selectedNodeId: string | null,
  forceAll: boolean
): ProgressiveGraph {
  if (forceAll || nodes.length <= LARGE_GRAPH_NODE_THRESHOLD) {
    return {
      edges,
      hiddenNodeCount: 0,
      isLimited: false,
      nodes
    };
  }

  const degreeById = buildDegreeMap(nodes, edges);
  const selectedNode = selectedNodeId ? nodes.find((node) => node.id === selectedNodeId) : null;
  const rankedNodes = nodes.slice().sort((left, right) => compareProgressiveNodes(left, right, degreeById, selectedNodeId));
  const selectedNodes = rankedNodes.slice(0, LARGE_GRAPH_PREVIEW_NODE_LIMIT);

  if (selectedNode && !selectedNodes.some((node) => node.id === selectedNode.id)) {
    selectedNodes[selectedNodes.length - 1] = selectedNode;
  }

  const visibleNodeIds = new Set(selectedNodes.map((node) => node.id));

  return {
    edges: edges.filter((edge) => visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target)),
    hiddenNodeCount: nodes.length - visibleNodeIds.size,
    isLimited: true,
    nodes: selectedNodes
  };
}

function compareProgressiveNodes(
  left: GraphNode,
  right: GraphNode,
  degreeById: Map<string, number>,
  selectedNodeId: string | null
) {
  const leftScore = progressiveNodeScore(left, degreeById, selectedNodeId);
  const rightScore = progressiveNodeScore(right, degreeById, selectedNodeId);
  if (leftScore !== rightScore) {
    return rightScore - leftScore;
  }

  const leftLayer = Number.isFinite(left.layer) ? left.layer ?? 0 : Number.MAX_SAFE_INTEGER;
  const rightLayer = Number.isFinite(right.layer) ? right.layer ?? 0 : Number.MAX_SAFE_INTEGER;
  if (leftLayer !== rightLayer) {
    return leftLayer - rightLayer;
  }

  const leftOrder = Number.isFinite(left.order) ? left.order ?? 0 : Number.MAX_SAFE_INTEGER;
  const rightOrder = Number.isFinite(right.order) ? right.order ?? 0 : Number.MAX_SAFE_INTEGER;
  if (leftOrder !== rightOrder) {
    return leftOrder - rightOrder;
  }

  return left.name.localeCompare(right.name);
}

function progressiveNodeScore(node: GraphNode, degreeById: Map<string, number>, selectedNodeId: string | null) {
  const selectedScore = node.id === selectedNodeId ? 1000 : 0;
  const statusScore = statusPriority(node.status) * 100;
  const degreeScore = Math.min(degreeById.get(node.id) ?? 0, 60);
  return selectedScore + statusScore + degreeScore;
}

function statusPriority(status: string) {
  switch (status.toLowerCase()) {
    case "added":
      return 7;
    case "modified_api":
      return 6;
    case "impacted":
      return 5;
    case "modified_impl":
      return 4;
    case "deleted":
      return 3;
    case "unchanged":
      return 1;
    default:
      return 2;
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
