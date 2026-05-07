package com.LeaveDataManagementSystem.LeaveManagement.Repository;

import com.LeaveDataManagementSystem.LeaveManagement.Model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    // ── Primary lookups ─────────────────────────────────────────────────────
    User findByEmail(String email);
    Optional<User> findUserByEmail(String email);
    boolean existsByEmail(String email);
    void deleteByEmail(String email);

    // ── Department queries ───────────────────────────────────────────────────
    List<User> findByDepartment(String department);
    List<User> findByOtherDepartmentsContaining(String department);

    @Query("{ '$or': [ { 'department': ?0 }, { 'otherDepartments': ?0 } ] }")
    List<User> findByDepartmentOrOtherDepartmentsContaining(String department);
}