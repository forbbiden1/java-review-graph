import type { NodeOverrideMap } from "./graph-model";

export function filterNodeOverrides(nodeOverrides: NodeOverrideMap, availableNodeIds: Set<string>) {
  return Object.fromEntries(Object.entries(nodeOverrides).filter(([nodeId]) => availableNodeIds.has(nodeId)));
}

export function hasEqualNodeOverrides(left: NodeOverrideMap, right: NodeOverrideMap) {
  const leftEntries = Object.entries(left);
  const rightEntries = Object.entries(right);

  if (leftEntries.length !== rightEntries.length) {
    return false;
  }

  return leftEntries.every(([nodeId, point]) => {
    const otherPoint = right[nodeId];
    return otherPoint !== undefined && otherPoint.x === point.x && otherPoint.y === point.y;
  });
}
