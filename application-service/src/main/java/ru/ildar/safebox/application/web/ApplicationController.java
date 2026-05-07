package ru.ildar.safebox.application.web;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ildar.safebox.application.domain.ApplicationStatus;
import ru.ildar.safebox.application.service.ApplicationMapper;
import ru.ildar.safebox.application.service.ApplicationService;
import ru.ildar.safebox.application.web.dto.ApplicationResponse;
import ru.ildar.safebox.application.web.dto.CreateApplicationRequest;
import ru.ildar.safebox.application.web.dto.UpdateStatusRequest;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final ApplicationMapper mapper;

    public ApplicationController(ApplicationService service, ApplicationMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest req) {
        var saved = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/applications/" + saved.getId()))
                .body(mapper.toResponse(saved));
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.get(id));
    }

    @GetMapping
    public Page<ApplicationResponse> list(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(clientId, status, PageRequest.of(page, size)).map(mapper::toResponse);
    }

    @PatchMapping("/{id}/status")
    public ApplicationResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest req) {
        return mapper.toResponse(service.changeStatus(id, req.status()));
    }

    @PostMapping("/{id}/confirm")
    public ApplicationResponse confirm(@PathVariable UUID id) {
        return mapper.toResponse(service.confirm(id));
    }
}
