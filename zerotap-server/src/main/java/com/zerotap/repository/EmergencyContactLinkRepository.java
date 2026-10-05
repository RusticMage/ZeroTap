package com.zerotap.repository;

import com.zerotap.model.EmergencyContactLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmergencyContactLinkRepository extends JpaRepository<EmergencyContactLink, String> {
    Optional<EmergencyContactLink> findByContactTokenAndActiveTrue(String contactToken);
    Optional<EmergencyContactLink> findByUserIdAndActiveTrue(String userId);
    List<EmergencyContactLink> findAllByUserId(String userId);
}
