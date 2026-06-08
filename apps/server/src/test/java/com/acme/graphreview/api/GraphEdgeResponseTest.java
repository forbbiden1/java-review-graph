package com.acme.graphreview.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import org.junit.jupiter.api.Test;

class GraphEdgeResponseTest {

    @Test
    void keepsRawRelationDirectionForGenericGraphResponses() {
        RelationRecord relation = new RelationRecord(
                "type:demo:Controller",
                "type:demo:Service",
                RelationType.USES_TYPE,
                "high",
                "src/main/java/demo/Controller.java",
                12
        );

        GraphEdgeResponse response = GraphEdgeResponse.from(relation);

        assertEquals("type:demo:Controller", response.source());
        assertEquals("type:demo:Service", response.target());
        assertEquals("uses_type", response.type());
        assertEquals("high", response.confidence());
    }

    @Test
    void reversesRelationDirectionForDependencyGraphResponses() {
        RelationRecord relation = new RelationRecord(
                "type:demo:Controller",
                "type:demo:Service",
                RelationType.USES_TYPE,
                "high",
                "src/main/java/demo/Controller.java",
                12
        );

        GraphEdgeResponse response = GraphEdgeResponse.fromDependency(relation);

        assertEquals("type:demo:Service", response.source());
        assertEquals("type:demo:Controller", response.target());
        assertEquals("uses_type", response.type());
        assertEquals("high", response.confidence());
    }
}
