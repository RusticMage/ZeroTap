package com.zerotap.repository;

import com.zerotap.model.PairingCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PairingCodeRepository extends JpaRepository<PairingCode, String> {
    Optional<PairingCode> findByCodeAndUsedFalseAndExpiresAtAfter(String code, Instant now);
    Optional<PairingCode> findByCode(String code);
    List<PairingCode> findByUserIdAndUsedFalse(String userId);
    void deleteByUserId(String userId);
}
