package com.zerotap.repository;

import com.zerotap.model.SafeSpace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SafeSpaceRepository extends JpaRepository<SafeSpace, String> {
    List<SafeSpace> findByTypeOrderByPriorityDesc(String type);
}
