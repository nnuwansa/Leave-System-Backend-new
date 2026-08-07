//package com.LeaveDataManagementSystem.LeaveManagement.Repository;
//
//import com.LeaveDataManagementSystem.LeaveManagement.Model.ShortLeaveEntitlement;
//import org.springframework.data.mongodb.repository.MongoRepository;
//import org.springframework.stereotype.Repository;
//
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface ShortLeaveEntitlementRepository extends MongoRepository<ShortLeaveEntitlement, String> {
//
//    // Find short leave entitlement for specific employee, year, and month
//    Optional<ShortLeaveEntitlement> findByEmployeeEmailAndYearAndMonth(String employeeEmail, int year, int month);
//
//    // Check if short leave entitlement exists for employee, year, and month
//    boolean existsByEmployeeEmailAndYearAndMonth(String employeeEmail, int year, int month);
//
//    // Find all short leave entitlements for a specific employee and year
//    List<ShortLeaveEntitlement> findByEmployeeEmailAndYear(String employeeEmail, int year);
//
//    // Find all short leave entitlements for a specific employee
//    List<ShortLeaveEntitlement> findByEmployeeEmailOrderByYearDescMonthDesc(String employeeEmail);
//
//    // Delete all short leave entitlements for a specific employee
//    void deleteByEmployeeEmail(String employeeEmail);
//
//    // Find all entitlements for a specific year and month
//    List<ShortLeaveEntitlement> findByYearAndMonth(int year, int month);
//}


package com.LeaveDataManagementSystem.LeaveManagement.Repository;

import com.LeaveDataManagementSystem.LeaveManagement.Model.ShortLeaveEntitlement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShortLeaveEntitlementRepository extends MongoRepository<ShortLeaveEntitlement, String> {

    // ── NEW: userId-based lookups (preferred going forward) ────────────────
    Optional<ShortLeaveEntitlement> findByUserIdAndYearAndMonth(String userId, int year, int month);
    boolean existsByUserIdAndYearAndMonth(String userId, int year, int month);
    List<ShortLeaveEntitlement> findByUserIdAndYear(String userId, int year);
    List<ShortLeaveEntitlement> findByUserIdOrderByYearDescMonthDesc(String userId);
    void deleteByUserId(String userId);

    // Find short leave entitlement for specific employee, year, and month
    Optional<ShortLeaveEntitlement> findByEmployeeEmailAndYearAndMonth(String employeeEmail, int year, int month);

    // Check if short leave entitlement exists for employee, year, and month
    boolean existsByEmployeeEmailAndYearAndMonth(String employeeEmail, int year, int month);

    // Find all short leave entitlements for a specific employee and year
    List<ShortLeaveEntitlement> findByEmployeeEmailAndYear(String employeeEmail, int year);

    // Find all short leave entitlements for a specific employee
    List<ShortLeaveEntitlement> findByEmployeeEmailOrderByYearDescMonthDesc(String employeeEmail);

    // Delete all short leave entitlements for a specific employee
    void deleteByEmployeeEmail(String employeeEmail);

    // Find all entitlements for a specific year and month
    List<ShortLeaveEntitlement> findByYearAndMonth(int year, int month);
}