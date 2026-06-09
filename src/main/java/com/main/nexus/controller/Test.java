package com.main.nexus.controller;
// Substitui os retornos de Project por ProjectResponseDTO
@GetMapping
public ResponseEntity<List<ProjectResponseDTO>> listMyProjects() {
    UserDTO logged = getLoggedUser();
    Company company = companyService.findByUserId(logged.id())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatusCode.valueOf(404), "Company not found"));
    return ResponseEntity.ok(
            projectService.findByCompany(company)
                    .stream()
                    .map(this::toResponseDTO)
                    .toList());
}

@GetMapping("/{id}")
public ResponseEntity<ProjectResponseDTO> findById(@PathVariable Long id) {
    return ResponseEntity.ok(toResponseDTO(projectService.findById(id)));
}

@PostMapping
public ResponseEntity<ProjectResponseDTO> create(@RequestBody ProjectRequestDTO request) {
    UserDTO logged = getLoggedUser();
    Company company = companyService.findByUserId(logged.id())
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatusCode.valueOf(404), "Company not found"));

    Project project = new Project();
    project.setCompany(company);
    project.setTitle(request.title());
    project.setDescription(request.description());
    project.setMinimumBudget(request.minimumBudget());
    project.setMaximumBudget(request.maximumBudget());
    project.setDeadline(request.deadline());
    project.setWorkMode(request.workMode());

    if (request.skillIds() != null) {
        project.setRequiredSkills(skillService.findAllById(request.skillIds()));
    }

    return ResponseEntity.ok(toResponseDTO(projectService.save(project)));
}

@PutMapping("/{id}")
public ResponseEntity<ProjectResponseDTO> update(
        @PathVariable Long id,
        @RequestBody ProjectRequestDTO request) {
    Project existing = projectService.findById(id);
    existing.setTitle(request.title());
    existing.setDescription(request.description());
    existing.setMinimumBudget(request.minimumBudget());
    existing.setMaximumBudget(request.maximumBudget());
    existing.setDeadline(request.deadline());
    existing.setWorkMode(request.workMode());

    if (request.skillIds() != null) {
        existing.setRequiredSkills(skillService.findAllById(request.skillIds()));
    }

    return ResponseEntity.ok(toResponseDTO(projectService.update(existing)));
}

// Método de conversão
private ProjectResponseDTO toResponseDTO(Project p) {
    return new ProjectResponseDTO(
            p.getId(),
            p.getTitle(),
            p.getDescription(),
            p.getMinimumBudget(),
            p.getMaximumBudget(),
            p.getDeadline(),
            p.getWorkMode(),
            p.getStatus(),
            p.getCreatedAt(),
            p.getRequiredSkills().stream().map(Skill::getName).toList(),
            p.getCompany().getId(),
            p.getCompany().getCompanyName()
    );
}