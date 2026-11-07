package dev.zzdiogo.jobtracker.reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByApplication_IdOrderByRemindAtAscIdAsc(Long applicationId);

    Optional<Reminder> findByIdAndApplication_Id(Long id, Long applicationId);

    @Query("""
            select r.id from Reminder r
            where r.sentAt is null and r.remindAt <= :now and r.attempts < :maxAttempts
            order by r.remindAt
            """)
    List<Long> findDueIds(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts);
}
