import "@xyflow/react/dist/style.css";

import {
  type ChangeEvent,
  type CSSProperties,
  type ReactNode,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState
} from "react";
import {
  applyNodeChanges,
  Background,
  BackgroundVariant,
  MiniMap,
  type NodeChange,
  ReactFlow,
  ReactFlowProvider,
  type Viewport,
  useOnViewportChange,
  useReactFlow
} from "@xyflow/react";
import type { GraphEdge, GraphNode } from "../api/client";
import { clearGraphScene, loadGraphScene, saveGraphScene } from "../platform";
import { buildElkLayout, buildScopedGraph, nodeStatusColor } from "./graph-layout";
import {
  DEFAULT_VIEWPORT,
  type FlowEdge,
  type FlowNode,
  type FlowNodeData,
  type GraphScopeMode,
  type LayoutResult,
  type NodeOverrideMap,
  SAFE_MIN_ZOOM,
  type StagePoint,
  type StageSize,
  ZOOM_SLIDER_MAX,
  ZOOM_SLIDER_MIN
} from "./graph-model";
import { edgeTypes, nodeTypes } from "./graph-renderers";
import { filterNodeOverrides, hasEqualNodeOverrides } from "./graph-scene";
import {
  measureViewportCenter,
  resolveCenteredNodeViewport,
  resolveInitialViewport,
  sanitizeStoredViewport,
  sanitizeZoom,
  sliderValueToZoom,
  updateStagePointer,
  zoomToSliderValue,
  zoomViewport
} from "./graph-viewport";

type GraphCanvasProps = {
  edges: GraphEdge[];
  emptyBody: string;
  emptyTitle: string;
  formatEdgeType: (edgeType: string) => string;
  formatNodeKind: (kind: string) => string;
  labels: {
    clearScope: string;
    focused: (name: string) => string;
    instructions: string;
    isolateHint: string;
    reset: string;
    scopeConnected: string;
    scopeDirect: string;
    viewportEmptyBody: string;
    viewportEmptyTitle: string;
    visible: (visibleCount: number, totalCount: number) => string;
    zoom: (zoomPercent: number) => string;
    zoomIn: string;
    zoomOut: string;
  };
  immersive?: boolean;
  nodes: GraphNode[];
  onNodeClick: (node: GraphNode) => void;
  overlayAction?: ReactNode;
  overlayPanel?: ReactNode;
  sceneStorageKey?: string | null;
  selectedNodeId: string | null;
};

export function GraphCanvas(props: GraphCanvasProps) {
  if (props.nodes.length === 0) {
    return (
      <div className="empty-state">
        <strong>{props.emptyTitle}</strong>
        <p>{props.emptyBody}</p>
      </div>
    );
  }

  return (
    <ReactFlowProvider>
      <GraphCanvasInner {...props} />
    </ReactFlowProvider>
  );
}

function GraphCanvasInner({
  edges,
  emptyBody,
  emptyTitle,
  formatEdgeType,
  formatNodeKind,
  immersive = false,
  labels,
  nodes,
  onNodeClick,
  overlayAction,
  overlayPanel,
  sceneStorageKey,
  selectedNodeId
}: GraphCanvasProps) {
  const stageRef = useRef<HTMLDivElement | null>(null);
  const isPointerOverStageRef = useRef(false);
  const lastStagePointerRef = useRef<StagePoint | null>(null);
  const sceneHydratedRef = useRef(false);
  const restoredSceneKeyRef = useRef<string | null>(null);
  const initializedSceneKeyRef = useRef<string | null>(null);
  const previousScopeSignatureRef = useRef<string | null>(null);
  const persistenceTimerRef = useRef<number | null>(null);
  const nodeOverridesRef = useRef<NodeOverrideMap>({});
  const flowNodesRef = useRef<FlowNode[]>([]);
  const onNodeClickRef = useRef(onNodeClick);
  const stageSizeRef = useRef<StageSize>({ height: 0, width: 0 });
  const viewportRef = useRef<Viewport>(DEFAULT_VIEWPORT);
  const reactFlowReadyRef = useRef(false);
  const pendingViewportRef = useRef<Viewport | null>(null);
  const nodePointerDownRef = useRef<{ nodeId: string; x: number; y: number } | null>(null);
  const lastTapRef = useRef<{ nodeId: string; timestamp: number } | null>(null);
  const singleClickTimerRef = useRef<number | null>(null);
  const [flowNodes, setFlowNodes] = useState<FlowNode[]>([]);
  const [flowEdges, setFlowEdges] = useState<FlowEdge[]>([]);
  const [viewport, setViewport] = useState<Viewport>(DEFAULT_VIEWPORT);
  const [stageSize, setStageSize] = useState<StageSize>({ height: 0, width: 0 });
  const [layoutResult, setLayoutResult] = useState<LayoutResult | null>(null);
  const [nodeOverrides, setNodeOverrides] = useState<NodeOverrideMap>({});
  const [scopedNodeId, setScopedNodeId] = useState<string | null>(null);
  const [scopeMode, setScopeMode] = useState<GraphScopeMode>("direct");
  const [isViewportActive, setIsViewportActive] = useState(false);
  const [isCanvasDragging, setIsCanvasDragging] = useState(false);
  const reactFlow = useReactFlow<FlowNode, FlowEdge>();

  useEffect(() => {
    nodeOverridesRef.current = nodeOverrides;
  }, [nodeOverrides]);

  useEffect(() => {
    flowNodesRef.current = flowNodes;
  }, [flowNodes]);

  useEffect(() => {
    onNodeClickRef.current = onNodeClick;
  }, [onNodeClick]);

  useEffect(() => {
    stageSizeRef.current = stageSize;
  }, [stageSize]);

  useEffect(() => {
    viewportRef.current = viewport;
  }, [viewport]);

  useOnViewportChange({
    onChange: (nextViewport) => {
      viewportRef.current = nextViewport;
      setViewport(nextViewport);
    }
  });

  useEffect(() => {
    return () => {
      if (singleClickTimerRef.current !== null) {
        window.clearTimeout(singleClickTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (persistenceTimerRef.current !== null) {
      window.clearTimeout(persistenceTimerRef.current);
      persistenceTimerRef.current = null;
    }

    const storedScene = loadGraphScene(sceneStorageKey);
    const availableNodeIds = new Set(nodes.map((node) => node.id));
    const nextOverrides = filterNodeOverrides(storedScene?.nodeOverrides ?? {}, availableNodeIds);
    const nextScopedNodeId =
      storedScene?.scopedNodeId && availableNodeIds.has(storedScene.scopedNodeId) ? storedScene.scopedNodeId : null;
    const nextScopeMode = storedScene?.scopeMode === "connected" ? "connected" : "direct";
    const nextViewport = storedScene ? sanitizeStoredViewport(storedScene.view) : DEFAULT_VIEWPORT;

    restoredSceneKeyRef.current = storedScene ? sceneStorageKey ?? null : null;
    initializedSceneKeyRef.current = null;
    previousScopeSignatureRef.current = null;
    sceneHydratedRef.current = true;
    nodeOverridesRef.current = nextOverrides;
    setNodeOverrides(nextOverrides);
    setScopedNodeId(nextScopedNodeId);
    setScopeMode(nextScopeMode);
    applyViewport(nextViewport);
  }, [sceneStorageKey, nodes]);

  useEffect(() => {
    const currentNodeIds = new Set(nodes.map((node) => node.id));
    setNodeOverrides((currentOverrides) => {
      const nextOverrides = filterNodeOverrides(currentOverrides, currentNodeIds);
      nodeOverridesRef.current = nextOverrides;
      return hasEqualNodeOverrides(currentOverrides, nextOverrides) ? currentOverrides : nextOverrides;
    });
  }, [nodes]);

  useEffect(() => {
    if (!scopedNodeId) {
      return;
    }

    if (!nodes.some((node) => node.id === scopedNodeId)) {
      setScopedNodeId(null);
    }
  }, [nodes, scopedNodeId]);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) {
      return;
    }

    const updateSize = () =>
      setStageSize({
        height: stage.clientHeight,
        width: stage.clientWidth
      });

    updateSize();

    const observer = new ResizeObserver(() => updateSize());
    observer.observe(stage);
    return () => observer.disconnect();
  }, []);

  const scopedGraph = useMemo(() => buildScopedGraph(nodes, edges, scopedNodeId, scopeMode), [edges, nodes, scopedNodeId, scopeMode]);

  useEffect(() => {
    if (!sceneHydratedRef.current) {
      return;
    }

    let cancelled = false;

    void buildElkLayout(
      scopedGraph.nodes,
      scopedGraph.edges,
      nodeOverridesRef.current,
      formatNodeKind,
      formatEdgeType,
      selectedNodeId,
      handleGraphNodePointerDown,
      handleGraphNodePointerUp
    ).then((nextLayoutResult) => {
        if (cancelled) {
          return;
        }

        setFlowNodes(nextLayoutResult.nodes);
        setFlowEdges(nextLayoutResult.edges);
        setLayoutResult(nextLayoutResult);
      });

    return () => {
      cancelled = true;
    };
  }, [formatEdgeType, formatNodeKind, scopedGraph.edges, scopedGraph.nodes, selectedNodeId]);

  useLayoutEffect(() => {
    if (!layoutResult || stageSize.width === 0 || stageSize.height === 0) {
      return;
    }

    const activeSceneKey = sceneStorageKey ?? "__graph-scene__";
    const scopeSignature = `${scopedNodeId ?? "__none__"}:${scopeMode}`;

    if (initializedSceneKeyRef.current !== activeSceneKey) {
      initializedSceneKeyRef.current = activeSceneKey;
      previousScopeSignatureRef.current = scopeSignature;

      if (restoredSceneKeyRef.current !== activeSceneKey) {
        applyViewport(resolveInitialViewport(layoutResult, stageSize));
      }
      return;
    }

    if (previousScopeSignatureRef.current !== scopeSignature) {
      previousScopeSignatureRef.current = scopeSignature;
      if (scopedNodeId) {
        applyViewport(resolveCenteredNodeViewport(layoutResult, stageSize, viewport.zoom, scopedNodeId), 160);
      } else {
        applyViewport(resolveInitialViewport(layoutResult, stageSize), 160);
      }
    }
  }, [layoutResult, sceneStorageKey, scopedNodeId, scopeMode, stageSize, viewport.zoom]);

  useEffect(() => {
    if (!sceneHydratedRef.current || !sceneStorageKey || flowNodes.length === 0) {
      return;
    }

    if (persistenceTimerRef.current !== null) {
      window.clearTimeout(persistenceTimerRef.current);
    }

    persistenceTimerRef.current = window.setTimeout(() => {
      saveGraphScene(sceneStorageKey, {
        version: 1,
        nodeOverrides,
        scopedNodeId,
        scopeMode,
        view: {
          offsetX: viewport.x,
          offsetY: viewport.y,
          zoom: viewport.zoom
        }
      });
      restoredSceneKeyRef.current = sceneStorageKey;
      persistenceTimerRef.current = null;
    }, 140);

    return () => {
      if (persistenceTimerRef.current !== null) {
        window.clearTimeout(persistenceTimerRef.current);
        persistenceTimerRef.current = null;
      }
    };
  }, [flowNodes.length, nodeOverrides, sceneStorageKey, scopedNodeId, scopeMode, viewport]);

  const sliderValue = zoomToSliderValue(viewport.zoom);
  const sliderProgress = ((sliderValue - ZOOM_SLIDER_MIN) / (ZOOM_SLIDER_MAX - ZOOM_SLIDER_MIN)) * 100;

  function resolveZoomOrigin() {
    if (isPointerOverStageRef.current && lastStagePointerRef.current) {
      return lastStagePointerRef.current;
    }
    return measureViewportCenter(stageSize);
  }

  function updateZoom(originX: number, originY: number, resolveNextZoom: (currentZoom: number) => number) {
    applyViewport(zoomViewport(viewportRef.current, originX, originY, resolveNextZoom, stageSize));
  }

  function zoomAt(nextZoomFactor: number, originX: number, originY: number) {
    updateZoom(originX, originY, (currentZoom) => currentZoom * nextZoomFactor);
  }

  function setAbsoluteZoom(nextZoom: number, originX: number, originY: number) {
    updateZoom(originX, originY, () => nextZoom);
  }

  function handleZoomSliderChange(event: ChangeEvent<HTMLInputElement>) {
    const nextZoom = sliderValueToZoom(Number(event.target.value));
    const { x, y } = resolveZoomOrigin();
    stageRef.current?.focus();
    setIsViewportActive(true);
    setAbsoluteZoom(nextZoom, x, y);
  }

  function resetViewAndLayout() {
    clearGraphScene(sceneStorageKey);
    restoredSceneKeyRef.current = null;
    initializedSceneKeyRef.current = null;
    previousScopeSignatureRef.current = null;
    setScopedNodeId(null);
    setScopeMode("direct");
    setNodeOverrides({});
    nodeOverridesRef.current = {};

    if (layoutResult) {
      applyViewport(resolveInitialViewport(layoutResult, stageSize), 160);
    } else {
      applyViewport(DEFAULT_VIEWPORT);
    }
  }

  function handleGraphNodeActivate(nodeId: string) {
    const nextNode = flowNodesRef.current.find((item) => item.id === nodeId);
    if (!nextNode) {
      return;
    }

    onNodeClickRef.current(nextNode.data.rawNode);
  }

  function handleGraphNodeScopeToggle(nodeId: string) {
    setScopedNodeId((currentNodeId) => (currentNodeId === nodeId ? null : nodeId));
  }

  function handleGraphNodePointerDown(nodeId: string, clientX: number, clientY: number) {
    nodePointerDownRef.current = {
      nodeId,
      x: clientX,
      y: clientY
    };
  }

  function handleGraphNodePointerUp(nodeId: string, clientX: number, clientY: number) {
    const pointerDown = nodePointerDownRef.current;
    nodePointerDownRef.current = null;
    if (!pointerDown || pointerDown.nodeId !== nodeId) {
      return;
    }

    if (Math.hypot(clientX - pointerDown.x, clientY - pointerDown.y) > 5) {
      return;
    }

    const now = Date.now();
    const lastTap = lastTapRef.current;
    const isDoubleTap = lastTap?.nodeId === nodeId && now - lastTap.timestamp <= 220;

    if (singleClickTimerRef.current !== null) {
      window.clearTimeout(singleClickTimerRef.current);
      singleClickTimerRef.current = null;
    }

    if (isDoubleTap) {
      lastTapRef.current = null;
      handleGraphNodeScopeToggle(nodeId);
      return;
    }

    lastTapRef.current = {
      nodeId,
      timestamp: now
    };

    singleClickTimerRef.current = window.setTimeout(() => {
      handleGraphNodeActivate(nodeId);
      singleClickTimerRef.current = null;
    }, 220);
  }

  function applyViewport(nextViewport: Viewport, duration = 0) {
    const sanitizedViewport = {
      x: Number.isFinite(nextViewport.x) ? nextViewport.x : 0,
      y: Number.isFinite(nextViewport.y) ? nextViewport.y : 0,
      zoom: sanitizeZoom(nextViewport.zoom)
    };

    viewportRef.current = sanitizedViewport;
    pendingViewportRef.current = sanitizedViewport;
    setViewport(sanitizedViewport);

    if (!reactFlowReadyRef.current) {
      return;
    }

    void reactFlow.setViewport(sanitizedViewport, duration > 0 ? { duration } : undefined);
  }

  function handleNodesChange(changes: NodeChange<FlowNode>[]) {
    const nextChanges = changes.filter((change) => change.type !== "select");
    if (nextChanges.length === 0) {
      return;
    }

    setFlowNodes((currentNodes) => applyNodeChanges(nextChanges, currentNodes));
  }

  function handleNodeDragStop(_event: MouseEvent | TouchEvent, node: FlowNode) {
    setNodeOverrides((currentOverrides) => ({
      ...currentOverrides,
      [node.id]: {
        x: node.position.x,
        y: node.position.y
      }
    }));
    setIsCanvasDragging(false);
  }

  if (layoutResult && layoutResult.nodes.length === 0) {
    return (
      <div className="empty-state">
        <strong>{emptyTitle}</strong>
        <p>{emptyBody}</p>
      </div>
    );
  }

  const visibleCount = flowNodes.length;
  const totalCount = nodes.length;
  const focusedNode = scopedGraph.focusNode;

  return (
    <div className={`graph-shell ${immersive ? "is-immersive" : ""}`}>
      <div className="graph-tools">
        <div className="graph-chip">{labels.visible(visibleCount, totalCount)}</div>
        <div className="graph-chip graph-chip-focus">{focusedNode ? labels.focused(focusedNode.name) : labels.isolateHint}</div>
        <div className="graph-chip">{labels.instructions}</div>
        <div className="graph-tool-actions">
          <div className="graph-scope-toggle" role="tablist" aria-label={labels.isolateHint}>
            <button
              type="button"
              className={scopeMode === "direct" ? "is-active" : ""}
              disabled={!focusedNode}
              onClick={() => setScopeMode("direct")}
            >
              {labels.scopeDirect}
            </button>
            <button
              type="button"
              className={scopeMode === "connected" ? "is-active" : ""}
              disabled={!focusedNode}
              onClick={() => setScopeMode("connected")}
            >
              {labels.scopeConnected}
            </button>
          </div>
          {focusedNode ? (
            <button type="button" onClick={() => setScopedNodeId(null)}>
              {labels.clearScope}
            </button>
          ) : null}
          <button
            type="button"
            title={labels.zoomOut}
            aria-label={labels.zoomOut}
            onClick={() => {
              const { x, y } = resolveZoomOrigin();
              zoomAt(0.88, x, y);
            }}
          >
            -
          </button>
          <label className="graph-zoom-slider" style={{ "--graph-zoom-progress": `${sliderProgress}%` } as CSSProperties}>
            <input
              type="range"
              min={ZOOM_SLIDER_MIN}
              max={ZOOM_SLIDER_MAX}
              step={1}
              value={sliderValue}
              title={labels.zoom(Math.round(viewport.zoom * 100))}
              aria-label={labels.zoom(Math.round(viewport.zoom * 100))}
              onChange={handleZoomSliderChange}
            />
          </label>
          <button
            type="button"
            title={labels.zoomIn}
            aria-label={labels.zoomIn}
            onClick={() => {
              const { x, y } = resolveZoomOrigin();
              zoomAt(1.14, x, y);
            }}
          >
            +
          </button>
          <button type="button" onClick={resetViewAndLayout}>
            {labels.reset}
          </button>
        </div>
      </div>

      <div
        ref={stageRef}
        tabIndex={0}
        className={`graph-stage graph-flow-stage ${immersive ? "is-immersive" : ""} ${isCanvasDragging ? "is-dragging" : ""} ${isViewportActive ? "is-active" : ""}`}
        onFocus={() => setIsViewportActive(true)}
        onBlur={() => setIsViewportActive(false)}
        onPointerEnter={(event) => {
          isPointerOverStageRef.current = true;
          updateStagePointer(lastStagePointerRef, stageRef, event.clientX, event.clientY);
          setIsViewportActive(true);
        }}
        onPointerMove={(event) => {
          updateStagePointer(lastStagePointerRef, stageRef, event.clientX, event.clientY);
        }}
        onPointerLeave={() => {
          isPointerOverStageRef.current = false;
          setIsViewportActive(false);
        }}
      >
        {flowNodes.length === 0 ? (
          <div className="graph-viewport-empty">
            <strong>{labels.viewportEmptyTitle}</strong>
            <p>{labels.viewportEmptyBody}</p>
          </div>
        ) : null}

        {overlayAction ? <div className="graph-stage-overlay-action">{overlayAction}</div> : null}
        {overlayPanel ? <div className="graph-stage-overlay-panel">{overlayPanel}</div> : null}

        <ReactFlow<FlowNode, FlowEdge>
          className="graph-flow"
          nodes={flowNodes}
          edges={flowEdges}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          nodesConnectable={false}
          nodesDraggable
          nodesFocusable={false}
          elementsSelectable={false}
          fitView={false}
          maxZoom={Number.POSITIVE_INFINITY}
          minZoom={SAFE_MIN_ZOOM}
          nodeClickDistance={4}
          onlyRenderVisibleElements
          panOnDrag
          panOnScroll={false}
          preventScrolling
          proOptions={{ hideAttribution: true }}
          selectionOnDrag={false}
          zoomOnDoubleClick={false}
          zoomOnPinch
          zoomOnScroll
          onInit={() => {
            reactFlowReadyRef.current = true;
            const pendingViewport = pendingViewportRef.current ?? viewportRef.current;
            if (pendingViewport) {
              void reactFlow.setViewport(pendingViewport);
            }
          }}
          onMoveStart={() => setIsCanvasDragging(true)}
          onMoveEnd={() => setIsCanvasDragging(false)}
          onNodeDragStart={() => setIsCanvasDragging(true)}
          onNodeDragStop={handleNodeDragStop}
          onNodesChange={handleNodesChange}
        >
          <Background className="graph-flow-background" color="rgba(9, 105, 218, 0.08)" gap={48} variant={BackgroundVariant.Lines} />
          <MiniMap
            pannable
            zoomable
            className="graph-minimap"
            maskColor="rgba(244, 239, 228, 0.78)"
            nodeColor={(node) => nodeStatusColor((node.data as FlowNodeData).rawNode.status)}
          />
        </ReactFlow>
      </div>
    </div>
  );
}
