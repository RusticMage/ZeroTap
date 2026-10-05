package com.zerotap.repository;

import com.zerotap.model.TrustedContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TrustedContactRepository extends JpaRepository<TrustedContact, String> {
    List<TrustedContact> findByUserIdOrderByIsPrimaryDesc(String userId);
}
