package com.backend.server.reportedhours;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.backend.server.users.User;

@Repository
public interface WorkDayRepository extends JpaRepository<WorkDay, Long> {
    
    List<WorkDay> findAllByUserId(Long userId);

    @Query(value = "SELECT * FROM reported_hours WHERE user_id = :userId ORDER BY date DESC LIMIT :limit", nativeQuery = true)
    List<WorkDay> findLastShiftsForUser(@Param("userId") Long userId, @Param("limit") int limit);


    @Query("SELECT wd FROM WorkDay wd JOIN wd.user u WHERE u.company.id = :companyId")
    List<WorkDay> findAllByCompanyId(Long companyId);

    Optional<WorkDay> findByUserAndDate(User user, LocalDate date);
}