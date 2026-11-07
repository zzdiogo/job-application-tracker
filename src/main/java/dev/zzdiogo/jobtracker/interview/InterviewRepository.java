package dev.zzdiogo.jobtracker.interview;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByApplicationIdOrderByScheduledAtAscIdAsc(Long applicationId);

    Optional<Interview> findByIdAndApplicationId(Long id, Long applicationId);
}
