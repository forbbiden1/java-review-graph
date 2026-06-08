package com.acme.graphreview.application;

import com.acme.graphreview.domain.StoredSymbolChange;
import java.util.List;

public interface SymbolChangeRepository {

    void saveAll(List<StoredSymbolChange> changes);

    List<StoredSymbolChange> findByProjectIdAndSnapshotId(String projectId, String snapshotId);
}
