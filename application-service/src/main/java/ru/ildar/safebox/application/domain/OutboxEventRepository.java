package ru.ildar.safebox.application.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {

    @Query("select o from OutboxEvent o where o.status = ru.ildar.safebox.application.domain.OutboxEvent$Status.PENDING order by o.createdAt asc")
    List<OutboxEvent> findPending(Pageable pageable);
}
