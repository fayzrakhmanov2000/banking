package ru.ildar.safebox.application.service;

import org.springframework.stereotype.Component;
import ru.ildar.safebox.application.domain.ApplicationEntity;
import ru.ildar.safebox.application.web.dto.ApplicationResponse;

@Component
public class ApplicationMapper {
    public ApplicationResponse toResponse(ApplicationEntity e) {
        return new ApplicationResponse(
                e.getId(), e.getClientId(), e.getCellId(), e.getStatus(),
                e.getRentalFrom(), e.getRentalTo(), e.getPrice(),
                e.getVersion(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
