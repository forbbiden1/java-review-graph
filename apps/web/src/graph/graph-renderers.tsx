import {
  BaseEdge,
  EdgeLabelRenderer,
  type EdgeProps,
  Handle,
  Position,
  type NodeProps,
  getStraightPath,
  useViewport
} from "@xyflow/react";
import { memo } from "react";

import type { FlowEdge, FlowNode, FlowNodeData } from "./graph-model";

export const GraphFlowNode = memo(function GraphFlowNode({ data, selected }: NodeProps<FlowNode>) {
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

export const GraphFlowEdge = memo(function GraphFlowEdge({
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

export const nodeTypes = {
  graphNode: GraphFlowNode
};

export const edgeTypes = {
  graphEdge: GraphFlowEdge
};
