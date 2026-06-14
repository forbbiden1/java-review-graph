import "@xyflow/react/dist/style.css";

import ELK from "elkjs/lib/elk-api.js";
import elkWorkerUrl from "elkjs/lib/elk-worker.min.js?url";
import {
  type ChangeEvent,
  type CSSProperties,
  memo,
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
  BaseEdge,
  EdgeLabelRenderer,
  type Edge,
  type EdgeProps,
  Handle,
  MarkerType,
  MiniMap,
  type Node,
  type NodeChange,
  type NodeProps,
  Position,
  ReactFlow,
  ReactFlowProvider,
  type Viewport,
  getStraightPath,
  useOnViewportChange,
  useReactFlow,
  useViewport
} from "@xyflow/react";
import type { GraphEdge, GraphNode } from "../api/client";
import { clearGraphScene, loadGraphScene, saveGraphScene, type StoredGraphScene } from "../platform";

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

type StageSize = {
  height: number;
  width: number;
};

type StagePoint = {
  x: number;
  y: number;
};

type LayoutBounds = {
  height: number;
  width: number;
  x: number;
  y: number;
};

type NodeOverrideMap = Record<string, { x: number; y: number }>;
type GraphScopeMode = "connected" | "direct";

type ScopedGraph = {
  edges: GraphEdge[];
  focusNode: GraphNode | null;
  nodes: GraphNode[];
};

type FlowNodeData = {
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

type FlowNode = Node<FlowNodeData, "graphNode">;

type FlowEdgeData = {
  edgeType: string;
  label: string;
};

type FlowEdge = Edge<FlowEdgeData, "graphEdge">;

type LayoutResult = {
  bounds: LayoutBounds;
  edges: FlowEdge[];
  focusNodeId: string | null;
  nodes: FlowNode[];
};

const elk = new ELK({ workerUrl: elkWorkerUrl });

const SAFE_MIN_ZOOM = 0.000001;
const VIEWPORT_PADDING = 160;
const ZOOM_SLIDER_MIN = -2000;
const ZOOM_SLIDER_MAX = 2000;
const ZOOM_SLIDER_SCALE = 80;
const DEFAULT_VIEWPORT: Viewport = { x: 0, y: 0, zoom: 1 };
const NODE_HORIZONTAL_PADDING = 28;
const NODE_VERTICAL_PADDING = 22;
const NODE_MIN_WIDTH = 164;
const NODE_MAX_WIDTH = 340;
const NODE_MIN_HEIGHT = 62;
const NODE_CHAR_WIDTH = 7.2;
const NODE_KIND_CHAR_WIDTH = 5.4;
const NODE_CHARS_PER_LINE = 24;
const ELK_PADDING = 96;
const ELK_NODE_SPACING = 128;
const ELK_LAYER_SPACING = 320;

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

const GraphFlowNode = memo(function GraphFlowNode({ data, selected }: NodeProps<FlowNode>) {
  return (
    <div
      className={`graph-node status-${data.rawNode.status} ${selected ? "is-selected" : ""}`}
      title={data.rawNode.qualifiedName}
      onPointerDown={(event) => {
        try {
          event.currentTarget.setPointerCapture(event.pointerId);
        } catch {
          // Ignore unsupported pointer-capture cases and keep click handling best-effort.
        }
        data.onPointerDown(data.nodeId, event.clientX, event.clientY);
      }}
      onPointerUp={(event) => {
        try {
          if (event.currentTarget.hasPointerCapture(event.pointerId)) {
            event.currentTarget.releasePointerCapture(event.pointerId);
          }
        } catch {
          // Ignore unsupported pointer-capture cases.
        }
        data.onPointerUp(data.nodeId, event.clientX, event.clientY);
      }}
      onPointerCancel={(event) => {
        try {
          if (event.currentTarget.hasPointerCapture(event.pointerId)) {
            event.currentTarget.releasePointerCapture(event.pointerId);
          }
        } catch {
          // Ignore unsupported pointer-capture cases.
        }
      }}
    >
      <Handle type="target" position={Position.Left} className="graph-handle graph-handle-target" isConnectable={false} />
      <span className="graph-node-name">{data.rawNode.name}</span>
      <span className="graph-node-kind">{data.kindLabel}</span>
      <Handle type="source" position={Position.Right} className="graph-handle graph-handle-source" isConnectable={false} />
    </div>
  );
});

const GraphFlowEdge = memo(function GraphFlowEdge({
  data,
  id,
  markerEnd,
  selected,
  sourceX,
  sourceY,
  style,
  targetX,
  targetY
}: EdgeProps<FlowEdge>) {
  const { zoom } = useViewport();
  const [path, labelX, labelY] = getStraightPath({
    sourceX,
    sourceY,
    targetX,
    targetY
  });
  const showLabel = zoom >= 0.82 && Math.hypot(targetX - sourceX, targetY - sourceY) * zoom >= 150;

  return (
    <>
      <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} />
      {showLabel && data?.label ? (
        <EdgeLabelRenderer>
          <div
            className={`graph-edge-label graph-edge-label-${data.edgeType} ${selected ? "is-selected" : ""}`}
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`
            }}
          >
            {data.label}
          </div>
        </EdgeLabelRenderer>
      ) : null}
    </>
  );
});

const nodeTypes = {
  graphNode: GraphFlowNode
};

const edgeTypes = {
  graphEdge: GraphFlowEdge
};

async function buildElkLayout(
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

function buildScopedGraph(
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

function nodeStatusColor(status: string) {
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

function resolveInitialViewport(layoutResult: LayoutResult, stageSize: StageSize): Viewport {
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

function resolveCenteredNodeViewport(layoutResult: LayoutResult, stageSize: StageSize, zoom: number, nodeId: string): Viewport {
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

function sanitizeStoredViewport(view: StoredGraphScene["view"]): Viewport {
  return {
    x: Number.isFinite(view.offsetX) ? view.offsetX : 0,
    y: Number.isFinite(view.offsetY) ? view.offsetY : 0,
    zoom: sanitizeZoom(view.zoom)
  };
}

function filterNodeOverrides(nodeOverrides: NodeOverrideMap, availableNodeIds: Set<string>) {
  return Object.fromEntries(Object.entries(nodeOverrides).filter(([nodeId]) => availableNodeIds.has(nodeId)));
}

function hasEqualNodeOverrides(left: NodeOverrideMap, right: NodeOverrideMap) {
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

function zoomViewport(
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

function updateStagePointer(
  pointerRef: React.MutableRefObject<StagePoint | null>,
  stageRef: React.MutableRefObject<HTMLDivElement | null>,
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

function measureViewportCenter(stageSize: StageSize) {
  return {
    x: stageSize.width / 2,
    y: stageSize.height / 2
  };
}

function sanitizeZoom(value: number) {
  if (!Number.isFinite(value) || value <= SAFE_MIN_ZOOM) {
    return SAFE_MIN_ZOOM;
  }
  return value;
}

function zoomToSliderValue(zoom: number) {
  const rawValue = Math.log(sanitizeZoom(zoom)) * ZOOM_SLIDER_SCALE;
  return clamp(Math.round(rawValue), ZOOM_SLIDER_MIN, ZOOM_SLIDER_MAX);
}

function sliderValueToZoom(value: number) {
  return sanitizeZoom(Math.exp(value / ZOOM_SLIDER_SCALE));
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}
