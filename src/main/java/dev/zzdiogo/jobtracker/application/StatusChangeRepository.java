package dev.zzdiogo.jobtracker.application;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {

    List<StatusChange> findByApplicationIdOrderByChangedAtAscIdAsc(Long applicationId);
}
