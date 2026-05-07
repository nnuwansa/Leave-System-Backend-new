
package com.LeaveDataManagementSystem.LeaveManagement.Repository;

import com.LeaveDataManagementSystem.LeaveManagement.Model.LateCoverageRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface LateCoverageRepository extends MongoRepository<LateCoverageRecord, String> {
    List<LateCoverageRecord> findAllByOrderByCreatedAtDesc();
    List<LateCoverageRecord> findByEmployeeEmailOrderByCreatedAtDesc(String email);
}