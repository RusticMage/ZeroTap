package com.zerotap.repository;

import com.zerotap.model.LocationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<LocationRecord, Long> {
    List<LocationRecord> findByUserIdOrderByTimestampDesc(String userId);
    Optional<LocationRecord> findFirstByUserIdOrderByTimestampDesc(String userId);
}
