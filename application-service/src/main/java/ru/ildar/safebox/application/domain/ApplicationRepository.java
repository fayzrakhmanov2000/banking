package ru.ildar.safebox.application.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    @Query("""
        select a from ApplicationEntity a
        where (:clientId is null or a.clientId = :clientId)
          and (:status is null or a.status = :status)
        """)
    Page<ApplicationEntity> search(UUID clientId, ApplicationStatus status, Pageable pageable);
}
