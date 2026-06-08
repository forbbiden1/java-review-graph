package com.acme.graphreview.application;

import com.acme.graphreview.domain.RegisteredProject;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    Optional<RegisteredProject> findById(String id);

    Optional<RegisteredProject> findByRootPath(String rootPath);

    List<RegisteredProject> findAll();

    RegisteredProject save(RegisteredProject project);

    void deleteById(String id);
}
