package com.zerotap.repository;

import com.zerotap.model.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, String> {
    List<Evidence> findByIncidentIdOrderByTimestampDesc(String incidentId);
    List<Evidence> findByUserIdOrderByTimestampDesc(String userId);
    List<Evidence> findTop20ByOrderByTimestampDesc();
}
