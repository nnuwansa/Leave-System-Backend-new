package com.LeaveDataManagementSystem.LeaveManagement.Repository;

import com.LeaveDataManagementSystem.LeaveManagement.Model.EmergencyLeaveRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface EmergencyLeaveRepository extends MongoRepository<EmergencyLeaveRequest, String> {

    // All requests for a specific employee
    List<EmergencyLeaveRequest> findByEmployeeEmail(String employeeEmail);

    // All requests assigned to a specific approval officer
    List<EmergencyLeaveRequest> findByApprovalOfficerEmail(String approvalOfficerEmail);

    // All requests with a specific status
    List<EmergencyLeaveRequest> findByStatus(String status);

    // Pending requests for a specific approval officer
    List<EmergencyLeaveRequest> findByApprovalOfficerEmailAndStatus(
            String approvalOfficerEmail, String status);

    // All requests ordered by created date (newest first)
    List<EmergencyLeaveRequest> findAllByOrderByCreatedAtDesc();
}