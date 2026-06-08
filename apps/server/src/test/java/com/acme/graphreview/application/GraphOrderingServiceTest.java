package com.acme.graphreview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.graphreview.application.GraphOrderingService.GraphNodeLayout;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolKind;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import com.acme.model.review.ChangeStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphOrderingServiceTest {

    private final GraphOrderingService graphOrderingService = new GraphOrderingService();

    @Test
    void ordersAcyclicGraphIntoTopologicalLayers() {
        SymbolRecord controller = symbol("Controller", "demo.Controller");
        SymbolRecord service = symbol("Service", "demo.Service");
        SymbolRecord repository = symbol("Repository", "demo.Repository");

        Map<String, GraphNodeLayout> layout = graphOrderingService.orderNodes(
                List.of(controller, service, repository),
                List.of(
                        edge(controller, service, RelationType.USES_TYPE),
                        edge(service, repository, RelationType.USES_TYPE)
                )
        );

        assertEquals(2, layout.get(controller.symbolKey()).layer());
        assertEquals(1, layout.get(service.symbolKey()).layer());
        assertEquals(0, layout.get(repository.symbolKey()).layer());
        assertEquals("main", layout.get(repository.symbolKey()).placement());
    }

    @Test
    void keepsTwoNodeCycleInOneCondensedLayerAndMarksItAsSidePlacement() {
        SymbolRecord left = symbol("Alpha", "demo.Alpha");
        SymbolRecord right = symbol("Beta", "demo.Beta");
        SymbolRecord leaf = symbol("Leaf", "demo.Leaf");

        Map<String, GraphNodeLayout> layout = graphOrderingService.orderNodes(
                List.of(left, right, leaf),
                List.of(
                        edge(left, right, RelationType.USES_TYPE),
                        edge(right, left, RelationType.USES_TYPE),
                        edge(right, leaf, RelationType.USES_TYPE)
                )
        );

        GraphNodeLayout leftLayout = layout.get(left.symbolKey());
        GraphNodeLayout rightLayout = layout.get(right.symbolKey());
        GraphNodeLayout leafLayout = layout.get(leaf.symbolKey());

        assertEquals(leftLayout.layer(), rightLayout.layer());
        assertEquals(leftLayout.group(), rightLayout.group());
        assertEquals("cycle_side", leftLayout.placement());
        assertEquals("cycle_side", rightLayout.placement());
        assertEquals(0, leafLayout.layer());
        assertEquals(leafLayout.layer() + 1, leftLayout.layer());
    }

    private SymbolRecord symbol(String name, String qualifiedName) {
        return new SymbolRecord(
                qualifiedName,
                SymbolType.TYPE,
                SymbolKind.CLASS,
                null,
                name,
                "demo",
                qualifiedName,
                name,
                name,
                "src/main/java/" + name + ".java",
                1,
                20,
                "api-" + name,
                "impl-" + name,
                ChangeStatus.UNCHANGED
        );
    }

    private RelationRecord edge(SymbolRecord source, SymbolRecord target, RelationType relationType) {
        return new RelationRecord(
                source.symbolKey(),
                target.symbolKey(),
                relationType,
                "high",
                source.filePath(),
                1
        );
    }
}
