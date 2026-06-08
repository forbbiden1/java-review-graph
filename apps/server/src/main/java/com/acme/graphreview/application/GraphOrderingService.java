package com.acme.graphreview.application;

import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.SymbolKind;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.review.ChangeStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GraphOrderingService {

    public Map<String, GraphNodeLayout> orderNodes(List<SymbolRecord> nodes, List<RelationRecord> edges) {
        if (nodes.isEmpty()) {
            return Map.of();
        }

        Map<String, SymbolRecord> nodeById = nodes.stream()
                .collect(Collectors.toMap(
                        SymbolRecord::symbolKey,
                        symbol -> symbol,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        Map<String, Set<String>> incoming = new LinkedHashMap<>();
        Set<String> selfLoopNodes = new HashSet<>();
        nodeById.keySet().forEach(nodeId -> {
            outgoing.put(nodeId, new LinkedHashSet<>());
            incoming.put(nodeId, new LinkedHashSet<>());
        });

        for (RelationRecord edge : edges) {
            if (!nodeById.containsKey(edge.sourceSymbolKey()) || !nodeById.containsKey(edge.targetSymbolKey())) {
                continue;
            }
            outgoing.get(edge.sourceSymbolKey()).add(edge.targetSymbolKey());
            incoming.get(edge.targetSymbolKey()).add(edge.sourceSymbolKey());
            if (edge.sourceSymbolKey().equals(edge.targetSymbolKey())) {
                selfLoopNodes.add(edge.sourceSymbolKey());
            }
        }

        List<Component> components = buildStronglyConnectedComponents(nodeById, outgoing, selfLoopNodes);
        Map<String, Integer> componentIdByNode = new HashMap<>();
        Map<Integer, Component> componentById = new LinkedHashMap<>();
        components.forEach(component -> {
            componentById.put(component.id(), component);
            component.nodeIds().forEach(nodeId -> componentIdByNode.put(nodeId, component.id()));
        });

        Map<Integer, Set<Integer>> componentOutgoing = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> componentIncoming = new LinkedHashMap<>();
        Map<Integer, Integer> indegreeByComponent = new HashMap<>();
        components.forEach(component -> {
            componentOutgoing.put(component.id(), new LinkedHashSet<>());
            componentIncoming.put(component.id(), new LinkedHashSet<>());
            indegreeByComponent.put(component.id(), 0);
        });

        for (RelationRecord edge : edges) {
            Integer sourceComponentId = componentIdByNode.get(edge.sourceSymbolKey());
            Integer targetComponentId = componentIdByNode.get(edge.targetSymbolKey());
            if (sourceComponentId == null || targetComponentId == null || Objects.equals(sourceComponentId, targetComponentId)) {
                continue;
            }
            if (componentOutgoing.get(sourceComponentId).add(targetComponentId)) {
                componentIncoming.get(targetComponentId).add(sourceComponentId);
                indegreeByComponent.compute(targetComponentId, (key, value) -> value == null ? 1 : value + 1);
            }
        }

        Map<Integer, Integer> layerByComponent = buildLayerByComponent(
                components,
                componentById,
                componentOutgoing,
                indegreeByComponent
        );
        List<List<Integer>> orderedLayers = buildOrderedLayers(
                components,
                componentById,
                componentIncoming,
                componentOutgoing,
                layerByComponent
        );

        Map<String, GraphNodeLayout> layoutByNode = new LinkedHashMap<>();
        for (int layerIndex = 0; layerIndex < orderedLayers.size(); layerIndex += 1) {
            List<Integer> orderedComponentIds = orderedLayers.get(layerIndex);
            int mainOrder = 0;
            int sideOrder = 0;
            for (Integer componentId : orderedComponentIds) {
                Component component = componentById.get(componentId);
                String placement = component.isPairCycle() ? "cycle_side" : "main";
                int componentOrder = component.isPairCycle() ? sideOrder++ : mainOrder++;
                List<String> memberNodeIds = sortComponentMembers(component, nodeById, outgoing, incoming);
                for (int memberIndex = 0; memberIndex < memberNodeIds.size(); memberIndex += 1) {
                    String nodeId = memberNodeIds.get(memberIndex);
                    layoutByNode.put(nodeId, new GraphNodeLayout(
                            layerIndex,
                            componentOrder,
                            "scc-" + componentId,
                            memberIndex,
                            placement
                    ));
                }
            }
        }

        return layoutByNode;
    }

    private Map<Integer, Integer> buildLayerByComponent(
            List<Component> components,
            Map<Integer, Component> componentById,
            Map<Integer, Set<Integer>> componentOutgoing,
            Map<Integer, Integer> indegreeByComponent
    ) {
        Map<Integer, Integer> remainingIndegree = new HashMap<>(indegreeByComponent);
        Map<Integer, Integer> layerByComponent = new HashMap<>();
        PriorityQueue<Integer> readyQueue = new PriorityQueue<>(compareComponentPriority(componentById));

        components.stream()
                .map(Component::id)
                .filter(componentId -> remainingIndegree.getOrDefault(componentId, 0) == 0)
                .forEach(readyQueue::offer);

        while (!readyQueue.isEmpty()) {
            Integer componentId = readyQueue.poll();
            int currentLayer = layerByComponent.getOrDefault(componentId, 0);
            for (Integer targetComponentId : componentOutgoing.getOrDefault(componentId, Set.of())) {
                layerByComponent.merge(targetComponentId, currentLayer + 1, Math::max);
                int nextIndegree = remainingIndegree.compute(targetComponentId, (key, value) -> value == null ? 0 : value - 1);
                if (nextIndegree == 0) {
                    readyQueue.offer(targetComponentId);
                }
            }
        }

        components.forEach(component -> layerByComponent.putIfAbsent(component.id(), 0));
        int maxLayer = layerByComponent.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        layerByComponent.replaceAll((componentId, layer) -> maxLayer - layer);
        return layerByComponent;
    }

    private List<List<Integer>> buildOrderedLayers(
            List<Component> components,
            Map<Integer, Component> componentById,
            Map<Integer, Set<Integer>> componentIncoming,
            Map<Integer, Set<Integer>> componentOutgoing,
            Map<Integer, Integer> layerByComponent
    ) {
        int maxLayer = layerByComponent.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<List<Integer>> layers = new ArrayList<>(maxLayer + 1);
        for (int layer = 0; layer <= maxLayer; layer += 1) {
            final int targetLayer = layer;
            List<Integer> layerComponents = components.stream()
                    .map(Component::id)
                    .filter(componentId -> layerByComponent.getOrDefault(componentId, 0) == targetLayer)
                    .sorted(compareComponentPriority(componentById))
                    .collect(Collectors.toCollection(ArrayList::new));
            layers.add(layerComponents);
        }

        for (int iteration = 0; iteration < 4; iteration += 1) {
            sweepLayers(layers, componentById, componentOutgoing, true);
            sweepLayers(layers, componentById, componentIncoming, false);
        }

        return layers;
    }

    private void sweepLayers(
            List<List<Integer>> layers,
            Map<Integer, Component> componentById,
            Map<Integer, Set<Integer>> neighborMap,
            boolean forward
    ) {
        Map<Integer, Integer> currentPositions = buildPositions(layers);
        int start = forward ? 1 : layers.size() - 2;
        int end = forward ? layers.size() : -1;
        int step = forward ? 1 : -1;

        for (int layerIndex = start; layerIndex != end; layerIndex += step) {
            List<Integer> layer = layers.get(layerIndex);
            Map<Integer, Integer> positionsSnapshot = currentPositions;
            layer.sort((leftId, rightId) -> compareLayerComponents(
                    leftId,
                    rightId,
                    componentById,
                    neighborMap,
                    positionsSnapshot
            ));
            currentPositions = buildPositions(layers);
        }
    }

    private int compareLayerComponents(
            Integer leftId,
            Integer rightId,
            Map<Integer, Component> componentById,
            Map<Integer, Set<Integer>> neighborMap,
            Map<Integer, Integer> currentPositions
    ) {
        Component left = componentById.get(leftId);
        Component right = componentById.get(rightId);
        boolean leftSide = left.isPairCycle();
        boolean rightSide = right.isPairCycle();
        if (leftSide != rightSide) {
            return leftSide ? 1 : -1;
        }

        double leftBarycenter = computeBarycenter(neighborMap.getOrDefault(leftId, Set.of()), currentPositions);
        double rightBarycenter = computeBarycenter(neighborMap.getOrDefault(rightId, Set.of()), currentPositions);
        boolean leftHasNeighbors = !Double.isNaN(leftBarycenter);
        boolean rightHasNeighbors = !Double.isNaN(rightBarycenter);
        if (leftHasNeighbors != rightHasNeighbors) {
            return leftHasNeighbors ? -1 : 1;
        }
        if (leftHasNeighbors) {
            int barycenterCompare = Double.compare(leftBarycenter, rightBarycenter);
            if (barycenterCompare != 0) {
                return barycenterCompare;
            }
        }

        int positionCompare = Integer.compare(
                currentPositions.getOrDefault(leftId, Integer.MAX_VALUE),
                currentPositions.getOrDefault(rightId, Integer.MAX_VALUE)
        );
        if (positionCompare != 0) {
            return positionCompare;
        }

        return compareComponentPriority(componentById).compare(leftId, rightId);
    }

    private double computeBarycenter(Collection<Integer> neighborIds, Map<Integer, Integer> currentPositions) {
        if (neighborIds.isEmpty()) {
            return Double.NaN;
        }
        return neighborIds.stream()
                .mapToInt(neighborId -> currentPositions.getOrDefault(neighborId, 0))
                .average()
                .orElse(Double.NaN);
    }

    private Map<Integer, Integer> buildPositions(List<List<Integer>> layers) {
        Map<Integer, Integer> positions = new HashMap<>();
        layers.forEach(layer -> {
            for (int index = 0; index < layer.size(); index += 1) {
                positions.put(layer.get(index), index);
            }
        });
        return positions;
    }

    private List<Component> buildStronglyConnectedComponents(
            Map<String, SymbolRecord> nodeById,
            Map<String, Set<String>> outgoing,
            Set<String> selfLoopNodes
    ) {
        TarjanState tarjanState = new TarjanState();
        List<String> orderedNodeIds = nodeById.values().stream()
                .sorted(compareSymbols())
                .map(SymbolRecord::symbolKey)
                .toList();
        for (String nodeId : orderedNodeIds) {
            if (tarjanState.indexByNode.containsKey(nodeId)) {
                continue;
            }
            strongConnect(nodeId, nodeById, outgoing, tarjanState);
        }

        List<Component> components = new ArrayList<>(tarjanState.components.size());
        for (int componentIndex = 0; componentIndex < tarjanState.components.size(); componentIndex += 1) {
            List<String> componentNodeIds = new ArrayList<>(tarjanState.components.get(componentIndex));
            componentNodeIds.sort((leftId, rightId) -> compareSymbols().compare(nodeById.get(leftId), nodeById.get(rightId)));
            boolean hasCycle = componentNodeIds.size() > 1
                    || componentNodeIds.stream().anyMatch(selfLoopNodes::contains);
            boolean isPairCycle = componentNodeIds.size() == 2;
            components.add(new Component(componentIndex, componentNodeIds, hasCycle, isPairCycle));
        }
        Map<Integer, Component> componentById = components.stream()
                .collect(Collectors.toMap(Component::id, component -> component));
        Comparator<Integer> componentPriority = compareComponentPriority(componentById);
        components.sort((left, right) -> componentPriority.compare(left.id(), right.id()));

        List<Component> normalizedComponents = new ArrayList<>(components.size());
        for (int index = 0; index < components.size(); index += 1) {
            Component original = components.get(index);
            normalizedComponents.add(new Component(index, original.nodeIds(), original.hasCycle(), original.isPairCycle()));
        }
        return normalizedComponents;
    }

    private void strongConnect(
            String nodeId,
            Map<String, SymbolRecord> nodeById,
            Map<String, Set<String>> outgoing,
            TarjanState tarjanState
    ) {
        tarjanState.indexByNode.put(nodeId, tarjanState.nextIndex);
        tarjanState.lowLinkByNode.put(nodeId, tarjanState.nextIndex);
        tarjanState.nextIndex += 1;
        tarjanState.stack.push(nodeId);
        tarjanState.onStack.add(nodeId);

        List<String> orderedNeighbors = outgoing.getOrDefault(nodeId, Set.of()).stream()
                .sorted((leftId, rightId) -> compareSymbols().compare(nodeById.get(leftId), nodeById.get(rightId)))
                .toList();
        for (String neighborId : orderedNeighbors) {
            if (!tarjanState.indexByNode.containsKey(neighborId)) {
                strongConnect(neighborId, nodeById, outgoing, tarjanState);
                tarjanState.lowLinkByNode.computeIfPresent(
                        nodeId,
                        (key, value) -> Math.min(value, tarjanState.lowLinkByNode.getOrDefault(neighborId, value))
                );
            } else if (tarjanState.onStack.contains(neighborId)) {
                tarjanState.lowLinkByNode.computeIfPresent(
                        nodeId,
                        (key, value) -> Math.min(value, tarjanState.indexByNode.getOrDefault(neighborId, value))
                );
            }
        }

        if (!Objects.equals(tarjanState.lowLinkByNode.get(nodeId), tarjanState.indexByNode.get(nodeId))) {
            return;
        }

        List<String> componentNodes = new ArrayList<>();
        while (!tarjanState.stack.isEmpty()) {
            String currentNodeId = tarjanState.stack.pop();
            tarjanState.onStack.remove(currentNodeId);
            componentNodes.add(currentNodeId);
            if (currentNodeId.equals(nodeId)) {
                break;
            }
        }
        tarjanState.components.add(componentNodes);
    }

    private List<String> sortComponentMembers(
            Component component,
            Map<String, SymbolRecord> nodeById,
            Map<String, Set<String>> outgoing,
            Map<String, Set<String>> incoming
    ) {
        if (component.nodeIds().size() <= 1) {
            return component.nodeIds();
        }

        Comparator<String> memberComparator = Comparator
                .comparingInt((String nodeId) -> externalDegree(nodeId, component.nodeIds(), outgoing, incoming))
                .reversed()
                .thenComparing(nodeId -> nodeById.get(nodeId), compareSymbols());

        return component.nodeIds().stream()
                .sorted(memberComparator)
                .toList();
    }

    private int externalDegree(
            String nodeId,
            List<String> componentNodeIds,
            Map<String, Set<String>> outgoing,
            Map<String, Set<String>> incoming
    ) {
        Set<String> componentNodeIdSet = new HashSet<>(componentNodeIds);
        long externalOutgoing = outgoing.getOrDefault(nodeId, Set.of()).stream()
                .filter(neighborId -> !componentNodeIdSet.contains(neighborId))
                .count();
        long externalIncoming = incoming.getOrDefault(nodeId, Set.of()).stream()
                .filter(neighborId -> !componentNodeIdSet.contains(neighborId))
                .count();
        return (int) (externalOutgoing + externalIncoming);
    }

    private Comparator<Integer> compareComponentPriority(Map<Integer, Component> componentById) {
        return (leftId, rightId) -> {
            Component left = componentById.get(leftId);
            Component right = componentById.get(rightId);
            if (left == null || right == null) {
                return Integer.compare(leftId, rightId);
            }

            int sideCompare = Boolean.compare(left.isPairCycle(), right.isPairCycle());
            if (sideCompare != 0) {
                return sideCompare;
            }

            int cycleCompare = Boolean.compare(right.hasCycle(), left.hasCycle());
            if (cycleCompare != 0) {
                return cycleCompare;
            }

            int sizeCompare = Integer.compare(right.nodeIds().size(), left.nodeIds().size());
            if (sizeCompare != 0) {
                return sizeCompare;
            }

            String leftAnchor = left.nodeIds().isEmpty() ? String.valueOf(left.id()) : left.nodeIds().get(0);
            String rightAnchor = right.nodeIds().isEmpty() ? String.valueOf(right.id()) : right.nodeIds().get(0);
            return leftAnchor.compareTo(rightAnchor);
        };
    }

    private Comparator<SymbolRecord> compareSymbols() {
        return (left, right) -> {
            int statusCompare = Integer.compare(
                    statusWeight(right.changeStatus()),
                    statusWeight(left.changeStatus())
            );
            if (statusCompare != 0) {
                return statusCompare;
            }

            int kindCompare = Integer.compare(
                    kindWeight(right.kind()),
                    kindWeight(left.kind())
            );
            if (kindCompare != 0) {
                return kindCompare;
            }

            int nameCompare = left.name().compareTo(right.name());
            if (nameCompare != 0) {
                return nameCompare;
            }

            return left.qualifiedName().compareTo(right.qualifiedName());
        };
    }

    private int kindWeight(SymbolKind kind) {
        return switch (kind) {
            case CLASS -> 6;
            case RECORD -> 5;
            case INTERFACE -> 4;
            case ENUM -> 3;
            case ANNOTATION -> 2;
            case CONSTRUCTOR -> 1;
            case METHOD -> 0;
        };
    }

    private int statusWeight(ChangeStatus status) {
        return switch (status) {
            case ADDED -> 6;
            case MODIFIED_API -> 5;
            case IMPACTED -> 4;
            case MODIFIED_IMPL -> 3;
            case DELETED -> 2;
            case UNCHANGED -> 1;
        };
    }

    public record GraphNodeLayout(
            int layer,
            int order,
            String group,
            int groupOrder,
            String placement
    ) {
    }

    private record Component(
            int id,
            List<String> nodeIds,
            boolean hasCycle,
            boolean isPairCycle
    ) {
    }

    private static final class TarjanState {
        private final Map<String, Integer> indexByNode = new HashMap<>();
        private final Map<String, Integer> lowLinkByNode = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> components = new ArrayList<>();
        private int nextIndex = 0;
    }
}
