package com.LeaveDataManagementSystem.LeaveManagement.Controller;

import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
import com.LeaveDataManagementSystem.LeaveManagement.DTO.ChangePasswordRequest;
import com.LeaveDataManagementSystem.LeaveManagement.Model.User;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Service.LeaveService;
import com.LeaveDataManagementSystem.LeaveManagement.Service.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/employee")
public class EmployeeController {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordService passwordService;

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(token);
            User user = userRepository.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(404).body("User Not Found");
            }
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Error Fetching User Data");
        }
    }

    @PutMapping("/change-password/{email}")
    public ResponseEntity<?> changePassword(
            @PathVariable String email,
            @RequestBody ChangePasswordRequest request
    ) {
        try {
            String msg = passwordService.changePassword(email, request);
            return ResponseEntity.ok(msg);
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }

    // ✅ Delete employee
    @DeleteMapping("/{email}")
    public ResponseEntity<String> deleteEmployee(@PathVariable String email) {
        if (userRepository.existsByEmail(email)) {
            userRepository.deleteByEmail(email);
            return ResponseEntity.ok("✅ Employee deleted successfully");
        }
        return ResponseEntity.status(404).body("❌ Employee not found");
    }

    // ✅ Employee self-update (allowed fields only)
    @PutMapping("/update-profile/{email}")
    public ResponseEntity<?> updateOwnProfile(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String email,
            @RequestBody Map<String, String> updates) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String tokenEmail = jwtUtil.extractUsername(token);

            if (!tokenEmail.equalsIgnoreCase(email)) {
                return ResponseEntity.status(403).body("❌ You can only update your own profile");
            }

            User user = userRepository.findByEmail(email);
            if (user == null) {
                return ResponseEntity.status(404).body("❌ User not found");
            }

            // Only these fields allowed — email, department, designation, name, roles are blocked
            if (updates.containsKey("fullName"))         user.setFullName(updates.get("fullName"));
            if (updates.containsKey("phoneNumber"))      user.setPhoneNumber(updates.get("phoneNumber"));
            if (updates.containsKey("address"))          user.setAddress(updates.get("address"));
            if (updates.containsKey("dateOfBirth"))      user.setDateOfBirth(updates.get("dateOfBirth"));
            if (updates.containsKey("gender"))           user.setGender(updates.get("gender"));
            if (updates.containsKey("maritalStatus"))    user.setMaritalStatus(updates.get("maritalStatus"));
            if (updates.containsKey("employmentType"))   user.setEmploymentType(updates.get("employmentType"));
            if (updates.containsKey("nationalId"))       user.setNationalId(updates.get("nationalId"));
            if (updates.containsKey("emergencyContact")) user.setEmergencyContact(updates.get("emergencyContact"));

            userRepository.save(user);
            logger.info("Employee {} updated their own profile", email);
            return ResponseEntity.ok("✅ Profile updated successfully");

        } catch (RuntimeException e) {
            logger.error("Error updating profile for {}: {}", email, e.getMessage());
            return ResponseEntity.status(400).body("❌ " + e.getMessage());
        }
    }

    // ============================================================================
    // MAIN ENDPOINT
    // URL: /employee/officers/department/{department}/exclude/{email}
    // ============================================================================
    @GetMapping("/officers/department/{department}/exclude/{email}")
    public ResponseEntity<?> getOfficersByDepartmentExcluding(
            @PathVariable String department,
            @PathVariable String email) {
        try {
            logger.info("Fetching officers for department: '{}' excluding: {}", department, email);

            List<User> deptUsers = userRepository.findByDepartment(department).stream()
                    .filter(user -> !user.getEmail().equalsIgnoreCase(email))
                    .collect(Collectors.toList());

            List<Map<String, Object>> actingOfficers = deptUsers.stream()
                    .map(this::mapUserToOfficerData)
                    .sorted(Comparator.comparing(m -> (String) m.get("name")))
                    .collect(Collectors.toList());

            List<Map<String, Object>> approvalOfficers = userRepository.findAll().stream()
                    .filter(user -> !user.getEmail().equalsIgnoreCase(email))
                    .filter(user -> {
                        String dept = user.getDepartment();
                        if (dept == null) return false;
                        return dept.equalsIgnoreCase("All") ||
                                dept.toUpperCase().contains("ALL") ||
                                dept.contains("Commissioner") ||
                                dept.equals("ALL (Commissioner, Assistant Commissioners)");
                    })
                    .map(this::mapUserToOfficerData)
                    .sorted(Comparator.comparing(m -> (String) m.get("name")))
                    .collect(Collectors.toList());

            logger.info("Returning {} acting officers and {} approval officers",
                    actingOfficers.size(), approvalOfficers.size());

            return ResponseEntity.ok(Map.of(
                    "acting", actingOfficers,
                    "approval", approvalOfficers
            ));

        } catch (Exception e) {
            logger.error("Error fetching officers for department: {}", department, e);
            return ResponseEntity.badRequest().body("❌ Failed to fetch officers for department");
        }
    }

    private Map<String, Object> mapUserToOfficerData(User user) {
        Map<String, Object> officerData = new HashMap<>();
        officerData.put("email", user.getEmail());
        officerData.put("name", user.getName() != null ? user.getName() : user.getFullName());
        officerData.put("fullName", user.getFullName());
        officerData.put("department", user.getDepartment());
        officerData.put("designation", user.getDesignation());
        officerData.put("gender", user.getGender());
        officerData.put("maritalStatus", user.getMaritalStatus());
        return officerData;
    }

    // ============================================================================
    // LEGACY ENDPOINTS
    // ============================================================================

    @GetMapping("/acting-officers/department/{department}")
    public ResponseEntity<?> getActingOfficersByDepartment(@PathVariable String department) {
        try {
            List<User> actingOfficers = leaveService.getActingOfficersByDepartment(department);
            return ResponseEntity.ok(actingOfficers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Failed to fetch acting officers for department");
        }
    }

    @GetMapping("/approval-officers/department/{department}")
    public ResponseEntity<?> getApprovalOfficersByDepartment(@PathVariable String department) {
        try {
            List<User> approvalOfficers = leaveService.getApprovalOfficersByDepartment(department);
            return ResponseEntity.ok(approvalOfficers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Failed to fetch approval officers for department");
        }
    }

    @GetMapping("/officers/department/{department}")
    public ResponseEntity<?> getOfficersByDepartment(@PathVariable String department) {
        try {
            Map<String, Object> officers = Map.of(
                    "acting", leaveService.getActingOfficersByDepartment(department),
                    "approval", leaveService.getApprovalOfficersByDepartment(department)
            );
            return ResponseEntity.ok(officers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Failed to fetch officers for department");
        }
    }

    @GetMapping("/departments")
    public ResponseEntity<?> getAllDepartments() {
        try {
            List<String> departments = leaveService.getAllDepartmentsWithOfficers();
            return ResponseEntity.ok(departments);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Failed to fetch departments");
        }
    }

    @GetMapping("/my-department-officers")
    public ResponseEntity<?> getMyDepartmentOfficers(@RequestHeader("Authorization") String token) {
        try {
            String email = jwtUtil.extractEmail(token.replace("Bearer ", ""));
            Map<String, Object> officers = leaveService.getOfficersForEmployee(email);
            return ResponseEntity.ok(officers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Failed to fetch department officers");
        }
    }

    @GetMapping("/approval-officers-by-dept/{department}")
    public ResponseEntity<List<User>> getApprovalOfficersByDept(
            @PathVariable String department,
            @RequestParam String excludeEmail) {
        List<User> officers = leaveService.getApprovalOfficersByDepartmentExcluding(department, excludeEmail);
        return ResponseEntity.ok(officers);
    }
}