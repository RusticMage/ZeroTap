package com.zerotap.repository;

import com.zerotap.model.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, String> {
    List<CheckIn> findByIncidentIdOrderByTimestampDesc(String incidentId);
    List<CheckIn> findByRecipientIdOrderByTimestampDesc(String recipientId);
}
