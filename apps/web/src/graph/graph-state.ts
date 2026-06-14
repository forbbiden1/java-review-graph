import type { Viewport } from "@xyflow/react";

import type { StoredGraphScene } from "../platform";
import { DEFAULT_VIEWPORT, type GraphScopeMode, type NodeOverrideMap } from "./graph-model";
import { filterNodeOverrides, hasEqualNodeOverrides } from "./graph-scene";
import { sanitizeStoredViewport } from "./graph-viewport";

export type GraphViewState = {
  nodeOverrides: NodeOverrideMap;
  scopedNodeId: string | null;
  scopeMode: GraphScopeMode;
  viewport: Viewport;
};

type GraphViewAction =
  | { state: GraphViewState; type: "restoreScene" }
  | { availableNodeIds: Set<string>; type: "syncAvailableNodes" }
  | { type: "setViewport"; viewport: Viewport }
  | { scopeMode: GraphScopeMode; type: "setScopeMode" }
  | { nodeId: string; type: "toggleScopedNode" }
  | { type: "clearScope" }
  | { nodeId: string; position: { x: number; y: number }; type: "setNodeOverride" }
  | { type: "reset"; viewport: Viewport };

export function createGraphViewState(overrides: Partial<GraphViewState> = {}): GraphViewState {
  return {
    nodeOverrides: {},
    scopedNodeId: null,
    scopeMode: "direct",
    viewport: DEFAULT_VIEWPORT,
    ...overrides
  };
}

export function createGraphViewStateFromScene(
  storedScene: StoredGraphScene | null,
  availableNodeIds: Set<string>
): GraphViewState {
  return createGraphViewState({
    nodeOverrides: filterNodeOverrides(storedScene?.nodeOverrides ?? {}, availableNodeIds),
    scopedNodeId:
      storedScene?.scopedNodeId && availableNodeIds.has(storedScene.scopedNodeId) ? storedScene.scopedNodeId : null,
    scopeMode: storedScene?.scopeMode === "connected" ? "connected" : "direct",
    viewport: storedScene ? sanitizeStoredViewport(storedScene.view) : DEFAULT_VIEWPORT
  });
}

export function graphViewReducer(state: GraphViewState, action: GraphViewAction): GraphViewState {
  switch (action.type) {
    case "restoreScene":
      return action.state;
    case "syncAvailableNodes": {
      const nextOverrides = filterNodeOverrides(state.nodeOverrides, action.availableNodeIds);
      const nextScopedNodeId =
        state.scopedNodeId && action.availableNodeIds.has(state.scopedNodeId) ? state.scopedNodeId : null;

      if (nextScopedNodeId === state.scopedNodeId && hasEqualNodeOverrides(state.nodeOverrides, nextOverrides)) {
        return state;
      }

      return {
        ...state,
        nodeOverrides: nextOverrides,
        scopedNodeId: nextScopedNodeId
      };
    }
    case "setViewport":
      return {
        ...state,
        viewport: action.viewport
      };
    case "setScopeMode":
      return state.scopeMode === action.scopeMode
        ? state
        : {
            ...state,
            scopeMode: action.scopeMode
          };
    case "toggleScopedNode":
      return {
        ...state,
        scopedNodeId: state.scopedNodeId === action.nodeId ? null : action.nodeId
      };
    case "clearScope":
      return state.scopedNodeId === null
        ? state
        : {
            ...state,
            scopedNodeId: null
          };
    case "setNodeOverride":
      return {
        ...state,
        nodeOverrides: {
          ...state.nodeOverrides,
          [action.nodeId]: action.position
        }
      };
    case "reset":
      return createGraphViewState({ viewport: action.viewport });
    default:
      return state;
  }
}
