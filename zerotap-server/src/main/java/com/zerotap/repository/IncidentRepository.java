package com.zerotap.repository;

import com.zerotap.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {
    List<Incident> findByUserIdOrderByStartTimeDesc(String userId);
    List<Incident> findByStatusInOrderByStartTimeDesc(List<String> statuses);
    List<Incident> findByUserIdAndStatus(String userId, String status);
}
