//package com.LeaveDataManagementSystem.LeaveManagement.Controller;
//
//import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.*;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LateCoverageRepository;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveRepository;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
//import com.LeaveDataManagementSystem.LeaveManagement.Service.LeaveEntitlementService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.web.bind.annotation.*;
//import java.time.temporal.ChronoUnit;
//import java.util.*;
//import java.util.stream.Collectors;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//
//
//@RestController
//@RequestMapping("/admin")
//@CrossOrigin()
//public class AdminController {
//    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
//
//    @Autowired
//    private UserRepository userRepository;
//    @Autowired
//    private LeaveRepository leaveRepository;
//    @Autowired
//    private LeaveEntitlementService leaveEntitlementService;
//
//    @Autowired
//    private JwtUtil jwtUtil;
//
//    @Autowired
//    private PasswordEncoder passwordEncoder;
//
//    @Autowired
//    private LateCoverageRepository lateCoverageRepository;
//   @Autowired
//    private LeaveEntitlementRepository leaveEntitlementRepository;
//
//
//
//    //  View all users (EXCLUDING ADMINS)
//    @GetMapping("/users")
//    public ResponseEntity<?> getAllUsers() {
//        List<User> users = userRepository.findAll().stream()
//                .filter(user -> user.getRoles() == null || !user.getRoles().contains("ADMIN"))
//                .collect(Collectors.toList());
//        return ResponseEntity.ok(users);
//    }
//
//    //  View single user by email
//    @GetMapping("/users/{email}")
//    public ResponseEntity<?> getUserByEmail(@PathVariable String email) {
//        User user = userRepository.findByEmail(email);
//        if (user == null) {
//            return ResponseEntity.status(404).body("❌ User not found");
//        }
//        return ResponseEntity.ok(user);
//    }
//
//    //  Update user
//    @PutMapping("/users/{email}")
//    public ResponseEntity<?> updateUser(@PathVariable String email, @RequestBody User updatedUser) {
//        User user = userRepository.findByEmail(email);
//        if (user == null) {
//            return ResponseEntity.status(404).body("❌ User not found");
//        }
//        user.setName(updatedUser.getName());
//        user.setFullName(updatedUser.getFullName());
//        user.setDepartment(updatedUser.getDepartment());
//        user.setOtherDepartments(updatedUser.getOtherDepartments());
//        user.setDesignation(updatedUser.getDesignation());
//        user.setJoinDate(updatedUser.getJoinDate());
//        user.setPhoneNumber(updatedUser.getPhoneNumber());
//        user.setAddress(updatedUser.getAddress());
//        user.setDateOfBirth(updatedUser.getDateOfBirth());
//        user.setGender(updatedUser.getGender());
//        user.setMaritalStatus(updatedUser.getMaritalStatus());
//        user.setEmploymentType(updatedUser.getEmploymentType());
//        user.setNationalId(updatedUser.getNationalId());
//        user.setEmergencyContact(updatedUser.getEmergencyContact());
//        user.setRoles(updatedUser.getRoles());
//        user.setCanBeActingOfficer(updatedUser.getCanBeActingOfficer());
//        user.setCanBeApprovalOfficer(updatedUser.getCanBeApprovalOfficer());
//        userRepository.save(user);
//        return ResponseEntity.ok("✅ User updated successfully");
//    }
//
//    //  Delete user
//    @DeleteMapping("/users/{email}")
//    public ResponseEntity<?> deleteUser(@PathVariable String email) {
//        if (userRepository.existsByEmail(email)) {
//            userRepository.deleteByEmail(email);
//            return ResponseEntity.ok("🗑️ User deleted successfully");
//        }
//        return ResponseEntity.status(404).body("❌ User not found");
//    }
//
//    //  Change user email (admin only)
//    @PutMapping("/users/{currentEmail}/change-email")
//    public ResponseEntity<?> changeUserEmail(
//            @PathVariable String currentEmail,
//            @RequestBody Map<String, String> body) {
//        try {
//            String newEmail = body.get("newEmail");
//            if (newEmail == null || newEmail.trim().isEmpty()) {
//                return ResponseEntity.status(400).body("❌ New email is required");
//            }
//            newEmail = newEmail.trim().toLowerCase();
//
//            if (userRepository.existsByEmail(newEmail)) {
//                return ResponseEntity.status(409).body("❌ Email already in use by another user");
//            }
//
//            User user = userRepository.findByEmail(currentEmail);
//            if (user == null) {
//                return ResponseEntity.status(404).body("❌ User not found");
//            }
//
//            String oldEmail = user.getEmail();
//            user.setEmail(newEmail);
//            userRepository.save(user);
//
//            // Also update all Leave records referencing the old email
//            List<Leave> leaves = leaveRepository.findByEmployeeEmail(oldEmail);
//            for (Leave leave : leaves) {
//                leave.setEmployeeEmail(newEmail);
//                leaveRepository.save(leave);
//            }
//
//            logger.info("Admin changed email {} → {}", oldEmail, newEmail);
//            return ResponseEntity.ok(Map.of(
//                    "message", " Email updated successfully",
//                    "oldEmail", oldEmail,
//                    "newEmail", newEmail,
//                    "leavesUpdated", leaves.size()
//            ));
//        } catch (Exception e) {
//            logger.error("Error changing email: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body("❌ " + e.getMessage());
//        }
//    }
//
//    //  Get all leaves for admin
//    @GetMapping("/leaves")
//    public ResponseEntity<?> getAllLeaves() {
//        try {
//            List<Leave> allLeaves = leaveRepository.findAllByOrderByCreatedAtDesc();
//
//            List<Map<String, Object>> enhancedLeaves = allLeaves.stream().map(leave -> {
//                User employee = userRepository.findByEmail(leave.getEmployeeEmail());
//                Map<String, Object> leaveData = new HashMap<>();
//
//                leaveData.put("id", leave.getId());
//                leaveData.put("employeeEmail", leave.getEmployeeEmail());
//                leaveData.put("employeeName", leave.getEmployeeName());
//                leaveData.put("leaveType", leave.getLeaveType());
//                leaveData.put("startDate", leave.getStartDate());
//                leaveData.put("endDate", leave.getEndDate());
//                leaveData.put("status", leave.getStatus());
//                leaveData.put("reason", leave.getReason());
//                leaveData.put("createdAt", leave.getCreatedAt());
//                leaveData.put("isHalfDay", leave.isHalfDay());
//                leaveData.put("isShortLeave", leave.isShortLeave());
//                leaveData.put("isCancelled", leave.isCancelled());
//                leaveData.put("isMaternityLeave", leave.isMaternityLeave());
//                leaveData.put("maternityLeaveType", leave.getMaternityLeaveType());
//                leaveData.put("maternityLeaveDuration", leave.getMaternityLeaveDuration());
//                leaveData.put("isMaternityEndDateSet", leave.isMaternityEndDateSet());
//                leaveData.put("maternityAdditionalDetails", leave.getMaternityAdditionalDetails());
//
//                if (employee != null) {
//                    leaveData.put("department", employee.getDepartment());
//                    leaveData.put("otherDepartments", employee.getOtherDepartments());
//                    leaveData.put("employeeDesignation", employee.getDesignation());
//                    leaveData.put("employeeFullName", employee.getFullName());
//                } else {
//                    leaveData.put("department", "Unknown");
//                    leaveData.put("otherDepartments", List.of());
//                    leaveData.put("employeeDesignation", "Unknown");
//                    leaveData.put("employeeFullName", leave.getEmployeeName());
//                }
//
//                leaveData.put("actingOfficerName", leave.getActingOfficerName());
//                leaveData.put("actingOfficerStatus", leave.getActingOfficerStatus());
//                leaveData.put("supervisingOfficerName", leave.getSupervisingOfficerName());
//                leaveData.put("supervisingOfficerStatus", leave.getSupervisingOfficerStatus());
//                leaveData.put("approvalOfficerName", leave.getApprovalOfficerName());
//                leaveData.put("approvalOfficerStatus", leave.getApprovalOfficerStatus());
//
//                leaveData.put("workingDays", leave.getWorkingDays());
//                leaveData.put("totalDays", leave.getTotalDays());
//                leaveData.put("weekendDays", leave.getWeekendDays());
//                leaveData.put("publicHolidays", leave.getPublicHolidays());
//
//                String leaveDuration;
//                if (leave.isShortLeave()) {
//                    leaveDuration = "Short Leave";
//                } else if (leave.isHalfDay()) {
//                    leaveDuration = "0.5 days";
//                } else if (leave.getWorkingDays() > 0) {
//                    leaveDuration = leave.getWorkingDays() + " working day" + (leave.getWorkingDays() != 1 ? "s" : "");
//                } else if (leave.getStartDate() != null && leave.getEndDate() != null) {
//                    long daysBetween = ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1;
//                    leaveDuration = daysBetween + " day" + (daysBetween != 1 ? "s" : "");
//                } else {
//                    leaveDuration = "N/A";
//                }
//                leaveData.put("leaveDuration", leaveDuration);
//
//                return leaveData;
//            }).collect(Collectors.toList());
//
//            return ResponseEntity.ok(enhancedLeaves);
//
//        } catch (Exception e) {
//            logger.error("Error fetching all leaves for admin: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body("❌ " + e.getMessage());
//        }
//    }
//
//    //  Get employee entitlements summary for admin
//    @GetMapping("/entitlements/{employeeEmail}")
//    public ResponseEntity<?> getEmployeeEntitlements(@PathVariable String employeeEmail) {
//        try {
//            User employee = userRepository.findByEmail(employeeEmail);
//            if (employee == null) {
//                return ResponseEntity.status(404).body("❌ Employee not found");
//            }
//
//            Map<String, Object> summary = leaveEntitlementService.getComprehensiveEntitlementSummary(employeeEmail);
//
//            Map<String, Object> employeeDetails = new HashMap<>();
//            employeeDetails.put("email", employee.getEmail());
//            employeeDetails.put("name", employee.getName());
//            employeeDetails.put("fullName", employee.getFullName());
//            employeeDetails.put("department", employee.getDepartment() != null ? employee.getDepartment() : "N/A");
//            employeeDetails.put("otherDepartments", employee.getOtherDepartments() != null ? employee.getOtherDepartments() : List.of());
//            employeeDetails.put("designation", employee.getDesignation() != null ? employee.getDesignation() : "N/A");
//
//            summary.put("employeeDetails", employeeDetails);
//            return ResponseEntity.ok(summary);
//
//        } catch (Exception e) {
//            logger.error("Error fetching employee entitlements: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body("❌ " + e.getMessage());
//        }
//    }
//
//    //  Get all employee entitlements (EXCLUDING ADMINS)
//    @GetMapping("/entitlements")
//    public ResponseEntity<?> getAllEmployeeEntitlements() {
//        try {
//            List<User> allUsers = userRepository.findAll().stream()
//                    .filter(user -> user.getRoles() == null || !user.getRoles().contains("ADMIN"))
//                    .collect(Collectors.toList());
//
//            logger.info("Total non-admin users found: {}", allUsers.size());
//
//            List<Map<String, Object>> entitlementSummaries = new ArrayList<>();
//            int processedCount = 0;
//            int errorCount = 0;
//
//            for (User user : allUsers) {
//                try {
//                    Map<String, Object> summary = leaveEntitlementService.getEntitlementSummary(user.getEmail());
//
//                    Map<String, Object> employeeDetails = new HashMap<>();
//                    employeeDetails.put("email", user.getEmail());
//                    employeeDetails.put("name", user.getName());
//                    employeeDetails.put("fullName", user.getFullName());
//                    employeeDetails.put("department", user.getDepartment() != null ? user.getDepartment() : "N/A");
//                    employeeDetails.put("otherDepartments", user.getOtherDepartments() != null ? user.getOtherDepartments() : List.of());
//                    employeeDetails.put("designation", user.getDesignation() != null ? user.getDesignation() : "N/A");
//
//                    summary.put("employeeDetails", employeeDetails);
//
//                    try {
//                        Map<String, Object> monthlyShortLeaveData = leaveEntitlementService.getEmployeeShortLeaveMonthlyBreakdown(user.getEmail());
//                        summary.put("shortLeaveMonthlyDetails", monthlyShortLeaveData);
//                    } catch (Exception shortLeaveError) {
//                        logger.warn("Error getting short leave monthly data for user {}: {}", user.getEmail(), shortLeaveError.getMessage());
//                        summary.put("shortLeaveMonthlyDetails", new HashMap<>());
//                    }
//
//                    entitlementSummaries.add(summary);
//                    processedCount++;
//
//                } catch (Exception e) {
//                    errorCount++;
//                    logger.error("Error getting entitlements for user {}: {}", user.getEmail(), e.getMessage(), e);
//
//                    Map<String, Object> fallbackSummary = new HashMap<>();
//                    Map<String, Object> employeeDetails = new HashMap<>();
//                    employeeDetails.put("email", user.getEmail());
//                    employeeDetails.put("name", user.getName());
//                    employeeDetails.put("fullName", user.getFullName());
//                    employeeDetails.put("department", user.getDepartment() != null ? user.getDepartment() : "N/A");
//                    employeeDetails.put("otherDepartments", user.getOtherDepartments() != null ? user.getOtherDepartments() : List.of());
//                    employeeDetails.put("designation", user.getDesignation() != null ? user.getDesignation() : "N/A");
//
//                    fallbackSummary.put("employeeDetails", employeeDetails);
//                    fallbackSummary.put("entitlements", new ArrayList<>());
//                    fallbackSummary.put("shortLeaveMonthlyDetails", new HashMap<>());
//                    fallbackSummary.put("error", "Failed to load entitlements");
//
//                    entitlementSummaries.add(fallbackSummary);
//                }
//            }
//
//            logger.info("Successfully processed: {}, Errors: {}, Total returned: {}",
//                    processedCount, errorCount, entitlementSummaries.size());
//
//            return ResponseEntity.ok(entitlementSummaries);
//
//        } catch (Exception e) {
//            logger.error("Error fetching all employee entitlements: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body("❌ " + e.getMessage());
//        }
//    }
//
//    @PutMapping("/change-password")
//    public ResponseEntity<?> changePassword(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, String> body) {
//        try {
//            String jwt = token.replace("Bearer ", "");
//            String email = jwtUtil.extractEmail(jwt);
//
//            User user = userRepository.findByEmail(email);
//            if (user == null) return ResponseEntity.status(404).body("❌ User not found");
//
//            String currentPassword = body.get("currentPassword");
//            String newPassword = body.get("newPassword");
//
//            if (currentPassword == null || newPassword == null)
//                return ResponseEntity.badRequest().body("❌ Both passwords are required");
//
//            if (newPassword.length() < 6)
//                return ResponseEntity.badRequest().body("❌ Password must be at least 6 characters");
//
//            if (!passwordEncoder.matches(currentPassword, user.getPassword()))
//                return ResponseEntity.status(401).body("❌ Current password is incorrect");
//
//            user.setPassword(passwordEncoder.encode(newPassword));
//            userRepository.save(user);
//
//            return ResponseEntity.ok("✅ Password changed successfully");
//        } catch (Exception e) {
//            logger.error("Error changing password: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body("❌ " + e.getMessage());
//        }
//    }
//
//
//
//// ============================================================================
//// ADD this endpoint inside AdminController class
//// GET /admin/daily-report?date=2026-03-09
//// Returns all employees on leave for the given date
//// ============================================================================
//
////    /**
////     * Daily Leave Report — who is on leave on a given date?
////     *
////     * Returns:
////     *  - employees on APPROVED full-day leave whose range covers the date
////     *  - employees on APPROVED half-day leave on that exact date
////     *  - employees on APPROVED short leave on that exact date
////     *
////     * Query param: date (ISO format: yyyy-MM-dd), defaults to today
////     */
////    @GetMapping("/daily-report")
////    public ResponseEntity<?> getDailyLeaveReport(
////            @RequestParam(required = false) String date) {
////
////        LocalDate reportDate;
////        try {
////            reportDate = (date != null && !date.isBlank())
////                    ? LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
////                    : LocalDate.now();
////        } catch (Exception e) {
////            return ResponseEntity.badRequest().body("❌ Invalid date format. Use yyyy-MM-dd");
////        }
////
////        // Fetch all APPROVED leaves
////        List<Leave> approvedLeaves = leaveRepository.findAll().stream()
////                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
////                .collect(Collectors.toList());
////
////        List<Map<String, Object>> onLeave = new ArrayList<>();
////        List<Map<String, Object>> shortLeaveList = new ArrayList<>();
////
////        for (Leave leave : approvedLeaves) {
////            if (leave.getStartDate() == null) continue;
////
////            boolean coversDate = false;
////
////            if (leave.isShortLeave()) {
////                // Short leave — exact date match only
////                if (reportDate.equals(leave.getStartDate())) {
////                    Map<String, Object> entry = buildLeaveEntry(leave, "SHORT");
////                    shortLeaveList.add(entry);
////                }
////                continue;
////            }
////
////            LocalDate end = (leave.getEndDate() != null) ? leave.getEndDate() : leave.getStartDate();
////            if (!reportDate.isBefore(leave.getStartDate()) && !reportDate.isAfter(end)) {
////                coversDate = true;
////            }
////
////            if (coversDate) {
////                String category = leave.isHalfDay() ? "HALF_DAY"
////                        : leave.isMaternityLeave() ? "MATERNITY"
////                        : "FULL_DAY";
////                onLeave.add(buildLeaveEntry(leave, category));
////            }
////        }
////
////        // Summary counts
////        long fullDayCount     = onLeave.stream().filter(e -> "FULL_DAY".equals(e.get("category"))).count();
////        long halfDayCount     = onLeave.stream().filter(e -> "HALF_DAY".equals(e.get("category"))).count();
////        long maternityCount   = onLeave.stream().filter(e -> "MATERNITY".equals(e.get("category"))).count();
////        long shortLeaveCount  = shortLeaveList.size();
////
////        Map<String, Object> response = new LinkedHashMap<>();
////        response.put("reportDate", reportDate.toString());
////        response.put("totalOnLeave", onLeave.size());
////        response.put("fullDayCount", fullDayCount);
////        response.put("halfDayCount", halfDayCount);
////        response.put("maternityCount", maternityCount);
////        response.put("shortLeaveCount", shortLeaveCount);
////        response.put("onLeave", onLeave);
////        response.put("shortLeaves", shortLeaveList);
////
////        return ResponseEntity.ok(response);
////    }
////
////    /** Helper: build a leave entry map for the report */
////    private Map<String, Object> buildLeaveEntry(Leave leave, String category) {
////        Map<String, Object> entry = new LinkedHashMap<>();
////        entry.put("employeeEmail",  leave.getEmployeeEmail());
////        entry.put("employeeName",   leave.getEmployeeName());
////        entry.put("leaveType",      leave.getLeaveType());
////        entry.put("category",       category);
////        entry.put("startDate",      leave.getStartDate() != null ? leave.getStartDate().toString() : null);
////        entry.put("endDate",        leave.getEndDate()   != null ? leave.getEndDate().toString()   : null);
////        entry.put("workingDays",    leave.getWorkingDays());
////        entry.put("reason",         leave.getReason());
////
////        // Half-day specific
////        if (leave.isHalfDay()) {
////            entry.put("halfDayPeriod",    leave.getHalfDayPeriod());
////            entry.put("halfDayStartTime", leave.getHalfDayStartTime() != null ? leave.getHalfDayStartTime().toString() : null);
////            entry.put("halfDayEndTime",   leave.getHalfDayEndTime()   != null ? leave.getHalfDayEndTime().toString()   : null);
////        }
////
////        // Short leave specific
////        if (leave.isShortLeave()) {
////            entry.put("shortLeaveStart", leave.getShortLeaveStartTime() != null ? leave.getShortLeaveStartTime().toString() : null);
////            entry.put("shortLeaveEnd",   leave.getShortLeaveEndTime()   != null ? leave.getShortLeaveEndTime().toString()   : null);
////        }
////
////        // Acting officer
////        entry.put("actingOfficerName",  leave.getActingOfficerName());
////        entry.put("actingOfficerEmail", leave.getActingOfficerEmail());
////
////        return entry;
////    }
////
////// ============================================================================
////// Also ADD to LeaveRepository.java (if not already):
//////   List<Leave> findByStatus(LeaveStatus status);
////// ============================================================================
//
//
//    /**
//     * Daily Leave Report — who is on leave on a given date?
//     *
//     * Returns:
//     *  - employees on APPROVED full-day leave whose range covers the date
//     *  - employees on APPROVED half-day leave on that exact date
//     *  - employees on APPROVED short leave on that exact date
//     *
//     * Query param: date (ISO format: yyyy-MM-dd), defaults to today
//     */
//    @GetMapping("/daily-report")
//    public ResponseEntity<?> getDailyLeaveReport(
//            @RequestParam(required = false) String date) {
//
//        LocalDate reportDate;
//        try {
//            reportDate = (date != null && !date.isBlank())
//                    ? LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
//                    : LocalDate.now();
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body("❌ Invalid date format. Use yyyy-MM-dd");
//        }
//
//        // Fetch all APPROVED leaves
//        List<Leave> approvedLeaves = leaveRepository.findAll().stream()
//                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
//                .collect(Collectors.toList());
//
//        List<Map<String, Object>> onLeave = new ArrayList<>();
//        List<Map<String, Object>> shortLeaveList = new ArrayList<>();
//
//        for (Leave leave : approvedLeaves) {
//            if (leave.getStartDate() == null) continue;
//
//            boolean coversDate = false;
//
//            if (leave.isShortLeave()) {
//                // Short leave — exact date match only
//                if (reportDate.equals(leave.getStartDate())) {
//                    Map<String, Object> entry = buildLeaveEntry(leave, "SHORT");
//                    shortLeaveList.add(entry);
//                }
//                continue;
//            }
//
//            LocalDate end = (leave.getEndDate() != null) ? leave.getEndDate() : leave.getStartDate();
//            if (!reportDate.isBefore(leave.getStartDate()) && !reportDate.isAfter(end)) {
//                coversDate = true;
//            }
//
//            if (coversDate) {
//                String category = leave.isHalfDay() ? "HALF_DAY"
//                        : leave.isMaternityLeave() ? "MATERNITY"
//                        : "FULL_DAY";
//                onLeave.add(buildLeaveEntry(leave, category));
//            }
//        }
//
//        // Summary counts
//        long fullDayCount     = onLeave.stream().filter(e -> "FULL_DAY".equals(e.get("category"))).count();
//        long halfDayCount     = onLeave.stream().filter(e -> "HALF_DAY".equals(e.get("category"))).count();
//        long maternityCount   = onLeave.stream().filter(e -> "MATERNITY".equals(e.get("category"))).count();
//        long shortLeaveCount  = shortLeaveList.size();
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("reportDate", reportDate.toString());
//        response.put("totalOnLeave", onLeave.size());
//        response.put("fullDayCount", fullDayCount);
//        response.put("halfDayCount", halfDayCount);
//        response.put("maternityCount", maternityCount);
//        response.put("shortLeaveCount", shortLeaveCount);
//        response.put("onLeave", onLeave);
//        response.put("shortLeaves", shortLeaveList);
//
//        return ResponseEntity.ok(response);
//    }
//
//    /** Helper: build a leave entry map for the report */
//    private Map<String, Object> buildLeaveEntry(Leave leave, String category) {
//        Map<String, Object> entry = new LinkedHashMap<>();
//        entry.put("employeeEmail",  leave.getEmployeeEmail());
//        entry.put("employeeName",   leave.getEmployeeName());
//
//        // ── Fetch department from User (not stored in Leave model) ────────────
//        String department = "";
//        try {
//            User user = userRepository.findByEmail(leave.getEmployeeEmail());
//            if (user != null && user.getDepartment() != null) {
//                department = user.getDepartment();
//            }
//        } catch (Exception ignored) {}
//        entry.put("department", department);
//
//        entry.put("leaveType",      leave.getLeaveType());
//        entry.put("category",       category);
//        entry.put("startDate",      leave.getStartDate() != null ? leave.getStartDate().toString() : null);
//        entry.put("endDate",        leave.getEndDate()   != null ? leave.getEndDate().toString()   : null);
//        entry.put("workingDays",    leave.getWorkingDays());
//        entry.put("reason",         leave.getReason());
//
//        // Half-day specific
//        if (leave.isHalfDay()) {
//            entry.put("halfDayPeriod",    leave.getHalfDayPeriod());
//            entry.put("halfDayStartTime", leave.getHalfDayStartTime() != null ? leave.getHalfDayStartTime().toString() : null);
//            entry.put("halfDayEndTime",   leave.getHalfDayEndTime()   != null ? leave.getHalfDayEndTime().toString()   : null);
//        }
//
//        // Short leave specific
//        if (leave.isShortLeave()) {
//            entry.put("shortLeaveStart", leave.getShortLeaveStartTime() != null ? leave.getShortLeaveStartTime().toString() : null);
//            entry.put("shortLeaveEnd",   leave.getShortLeaveEndTime()   != null ? leave.getShortLeaveEndTime().toString()   : null);
//        }
//
//        // Acting officer
//        entry.put("actingOfficerName",  leave.getActingOfficerName());
//        entry.put("actingOfficerEmail", leave.getActingOfficerEmail());
//
//        return entry;
//    }
//
//
//    // ============================================================================
//// ADD THIS TO AdminController.java — Fix incorrect SICK entitlement
//// ============================================================================
//
//
//
//    // ── Fix one employee's entitlement for a specific year ───────────────────────
//// POST /admin/fix-sick-entitlement?email=x@y.com&year=2026
//    @PostMapping("/fix-sick-entitlement")
//    public ResponseEntity<?> fixSickEntitlement(
//            @RequestHeader("Authorization") String token,
//            @RequestParam String email,
//            @RequestParam int year) {
//        try {
//            Map<String, Object> result = leaveEntitlementService.correctSickEntitlement(email, year);
//            return ResponseEntity.ok(result);
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
//        }
//    }
//
//    // ── Fix ALL employees' entitlement for a specific year ────────────────────────
//// POST /admin/fix-sick-entitlement-all?year=2026
//    @PostMapping("/fix-sick-entitlement-all")
//    public ResponseEntity<?> fixSickEntitlementAll(
//            @RequestHeader("Authorization") String token,
//            @RequestParam(defaultValue = "0") int year) {
//        try {
//            int targetYear = year > 0 ? year : LocalDate.now().getYear();
//            List<User> allUsers = userRepository.findAll();
//
//            List<Map<String, Object>> results = new ArrayList<>();
//            int fixed = 0, skipped = 0, errors = 0;
//
//            for (User user : allUsers) {
//                if (user.getEmail() == null) continue;
//                try {
//                    Map<String, Object> r = leaveEntitlementService.correctSickEntitlement(user.getEmail(), targetYear);
//                    if (Boolean.TRUE.equals(r.get("success"))) {
//                        results.add(r);
//                        fixed++;
//                    } else {
//                        skipped++;
//                    }
//                } catch (Exception e) {
//                    errors++;
//                    results.add(Map.of("email", user.getEmail(), "error", e.getMessage()));
//                }
//            }
//
//            return ResponseEntity.ok(Map.of(
//                    "success", true,
//                    "year", targetYear,
//                    "totalUsers", allUsers.size(),
//                    "fixed", fixed,
//                    "skipped", skipped,
//                    "errors", errors,
//                    "details", results
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
//        }
//    }
//
//}

package com.LeaveDataManagementSystem.LeaveManagement.Controller;

import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
import com.LeaveDataManagementSystem.LeaveManagement.Model.*;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LateCoverageRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.ShortLeaveEntitlementRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.HistoricalLeaveSummaryRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.EmergencyLeaveRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Service.LeaveEntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@RestController
@RequestMapping("/admin")
@CrossOrigin()
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LeaveRepository leaveRepository;
    @Autowired
    private LeaveEntitlementService leaveEntitlementService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LateCoverageRepository lateCoverageRepository;
    @Autowired
    private LeaveEntitlementRepository leaveEntitlementRepository;
    @Autowired
    private ShortLeaveEntitlementRepository shortLeaveEntitlementRepository;
    @Autowired
    private HistoricalLeaveSummaryRepository historicalLeaveSummaryRepository;
    @Autowired
    private EmergencyLeaveRepository emergencyLeaveRepository;



    //  View all users (EXCLUDING ADMINS)
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userRepository.findAll().stream()
                .filter(user -> user.getRoles() == null || !user.getRoles().contains("ADMIN"))
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    //  View single user by email
    @GetMapping("/users/{email}")
    public ResponseEntity<?> getUserByEmail(@PathVariable String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(404).body("❌ User not found");
        }
        return ResponseEntity.ok(user);
    }

    //  Update user
    @PutMapping("/users/{email}")
    public ResponseEntity<?> updateUser(@PathVariable String email, @RequestBody User updatedUser) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(404).body("❌ User not found");
        }
        user.setName(updatedUser.getName());
        user.setFullName(updatedUser.getFullName());
        user.setDepartment(updatedUser.getDepartment());
        user.setOtherDepartments(updatedUser.getOtherDepartments());
        user.setDesignation(updatedUser.getDesignation());
        user.setJoinDate(updatedUser.getJoinDate());
        user.setPhoneNumber(updatedUser.getPhoneNumber());
        user.setAddress(updatedUser.getAddress());
        user.setDateOfBirth(updatedUser.getDateOfBirth());
        user.setGender(updatedUser.getGender());
        user.setMaritalStatus(updatedUser.getMaritalStatus());
        user.setEmploymentType(updatedUser.getEmploymentType());
        user.setNationalId(updatedUser.getNationalId());
        user.setEmergencyContact(updatedUser.getEmergencyContact());
        user.setRoles(updatedUser.getRoles());
        user.setCanBeActingOfficer(updatedUser.getCanBeActingOfficer());
        user.setCanBeApprovalOfficer(updatedUser.getCanBeApprovalOfficer());
        userRepository.save(user);
        return ResponseEntity.ok("✅ User updated successfully");
    }

    //  Delete user
    @DeleteMapping("/users/{email}")
    public ResponseEntity<?> deleteUser(@PathVariable String email) {
        if (userRepository.existsByEmail(email)) {
            userRepository.deleteByEmail(email);
            return ResponseEntity.ok("🗑️ User deleted successfully");
        }
        return ResponseEntity.status(404).body("❌ User not found");
    }

    //  Change user email (admin only)
    @PutMapping("/users/{currentEmail}/change-email")
    public ResponseEntity<?> changeUserEmail(
            @PathVariable String currentEmail,
            @RequestBody Map<String, String> body) {
        try {
            String newEmail = body.get("newEmail");
            if (newEmail == null || newEmail.trim().isEmpty()) {
                return ResponseEntity.status(400).body("❌ New email is required");
            }
            newEmail = newEmail.trim().toLowerCase();

            if (userRepository.existsByEmail(newEmail)) {
                return ResponseEntity.status(409).body("❌ Email already in use by another user");
            }

            User user = userRepository.findByEmail(currentEmail);
            if (user == null) {
                return ResponseEntity.status(404).body("❌ User not found");
            }

            String oldEmail = user.getEmail();
            String userId = user.getId();
            user.setEmail(newEmail);
            userRepository.save(user);

            // ── Cascade the email change onto every collection that stores a
            //    display-only employeeEmail alongside the stable userId.
            //    userId is what these records are actually looked up by /
            //    linked through — employeeEmail here is kept in sync purely
            //    so it displays correctly and legacy email-based queries
            //    (during the transition) still resolve correctly too.
            int leavesUpdated = 0, entitlementsUpdated = 0, shortLeaveUpdated = 0,
                    historicalUpdated = 0, lateCoverageUpdated = 0, emergencyUpdated = 0;

            List<Leave> leaves = leaveRepository.findByUserId(userId);
            for (Leave leave : leaves) {
                leave.setEmployeeEmail(newEmail);
                leaveRepository.save(leave);
            }
            leavesUpdated = leaves.size();

            List<LeaveEntitlement> entitlements = leaveEntitlementRepository.findByUserIdOrderByLeaveType(userId);
            for (LeaveEntitlement ent : entitlements) {
                ent.setEmployeeEmail(newEmail);
                leaveEntitlementRepository.save(ent);
            }
            entitlementsUpdated = entitlements.size();

            List<ShortLeaveEntitlement> shortLeaves = shortLeaveEntitlementRepository.findByUserIdOrderByYearDescMonthDesc(userId);
            for (ShortLeaveEntitlement sl : shortLeaves) {
                sl.setEmployeeEmail(newEmail);
                shortLeaveEntitlementRepository.save(sl);
            }
            shortLeaveUpdated = shortLeaves.size();

            List<HistoricalLeaveSummary> historicalSummaries = historicalLeaveSummaryRepository.findByUserIdOrderByYearDesc(userId);
            for (HistoricalLeaveSummary hs : historicalSummaries) {
                hs.setEmployeeEmail(newEmail);
                historicalLeaveSummaryRepository.save(hs);
            }
            historicalUpdated = historicalSummaries.size();

            List<LateCoverageRecord> lateCoverageRecords = lateCoverageRepository.findByUserIdOrderByCreatedAtDesc(userId);
            for (LateCoverageRecord lc : lateCoverageRecords) {
                lc.setEmployeeEmail(newEmail);
                lateCoverageRepository.save(lc);
            }
            lateCoverageUpdated = lateCoverageRecords.size();

            List<EmergencyLeaveRequest> emergencyRequests = emergencyLeaveRepository.findByUserId(userId);
            for (EmergencyLeaveRequest er : emergencyRequests) {
                er.setEmployeeEmail(newEmail);
                emergencyLeaveRepository.save(er);
            }
            emergencyUpdated = emergencyRequests.size();

            logger.info("Admin changed email {} → {} (userId={}) — cascaded to {} leaves, {} entitlements, {} short-leave, {} historical, {} late-coverage, {} emergency-leave records",
                    oldEmail, newEmail, userId, leavesUpdated, entitlementsUpdated, shortLeaveUpdated, historicalUpdated, lateCoverageUpdated, emergencyUpdated);

            return ResponseEntity.ok(Map.of(
                    "message", " Email updated successfully",
                    "oldEmail", oldEmail,
                    "newEmail", newEmail,
                    "leavesUpdated", leavesUpdated,
                    "entitlementsUpdated", entitlementsUpdated,
                    "shortLeaveUpdated", shortLeaveUpdated,
                    "historicalUpdated", historicalUpdated,
                    "lateCoverageUpdated", lateCoverageUpdated,
                    "emergencyUpdated", emergencyUpdated
            ));
        } catch (Exception e) {
            logger.error("Error changing email: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    //  Get all leaves for admin
    @GetMapping("/leaves")
    public ResponseEntity<?> getAllLeaves() {
        try {
            List<Leave> allLeaves = leaveRepository.findAllByOrderByCreatedAtDesc();

            List<Map<String, Object>> enhancedLeaves = allLeaves.stream().map(leave -> {
                User employee = userRepository.findByEmail(leave.getEmployeeEmail());
                Map<String, Object> leaveData = new HashMap<>();

                leaveData.put("id", leave.getId());
                leaveData.put("employeeEmail", leave.getEmployeeEmail());
                leaveData.put("employeeName", leave.getEmployeeName());
                leaveData.put("leaveType", leave.getLeaveType());
                leaveData.put("startDate", leave.getStartDate());
                leaveData.put("endDate", leave.getEndDate());
                leaveData.put("status", leave.getStatus());
                leaveData.put("reason", leave.getReason());
                leaveData.put("createdAt", leave.getCreatedAt());
                leaveData.put("isHalfDay", leave.isHalfDay());
                leaveData.put("isShortLeave", leave.isShortLeave());
                leaveData.put("isCancelled", leave.isCancelled());
                leaveData.put("isMaternityLeave", leave.isMaternityLeave());
                leaveData.put("maternityLeaveType", leave.getMaternityLeaveType());
                leaveData.put("maternityLeaveDuration", leave.getMaternityLeaveDuration());
                leaveData.put("isMaternityEndDateSet", leave.isMaternityEndDateSet());
                leaveData.put("maternityAdditionalDetails", leave.getMaternityAdditionalDetails());

                if (employee != null) {
                    leaveData.put("department", employee.getDepartment());
                    leaveData.put("otherDepartments", employee.getOtherDepartments());
                    leaveData.put("employeeDesignation", employee.getDesignation());
                    leaveData.put("employeeFullName", employee.getFullName());
                } else {
                    leaveData.put("department", "Unknown");
                    leaveData.put("otherDepartments", List.of());
                    leaveData.put("employeeDesignation", "Unknown");
                    leaveData.put("employeeFullName", leave.getEmployeeName());
                }

                leaveData.put("actingOfficerName", leave.getActingOfficerName());
                leaveData.put("actingOfficerStatus", leave.getActingOfficerStatus());
                leaveData.put("supervisingOfficerName", leave.getSupervisingOfficerName());
                leaveData.put("supervisingOfficerStatus", leave.getSupervisingOfficerStatus());
                leaveData.put("approvalOfficerName", leave.getApprovalOfficerName());
                leaveData.put("approvalOfficerStatus", leave.getApprovalOfficerStatus());

                leaveData.put("workingDays", leave.getWorkingDays());
                leaveData.put("totalDays", leave.getTotalDays());
                leaveData.put("weekendDays", leave.getWeekendDays());
                leaveData.put("publicHolidays", leave.getPublicHolidays());

                String leaveDuration;
                if (leave.isShortLeave()) {
                    leaveDuration = "Short Leave";
                } else if (leave.isHalfDay()) {
                    leaveDuration = "0.5 days";
                } else if (leave.getWorkingDays() > 0) {
                    leaveDuration = leave.getWorkingDays() + " working day" + (leave.getWorkingDays() != 1 ? "s" : "");
                } else if (leave.getStartDate() != null && leave.getEndDate() != null) {
                    long daysBetween = ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1;
                    leaveDuration = daysBetween + " day" + (daysBetween != 1 ? "s" : "");
                } else {
                    leaveDuration = "N/A";
                }
                leaveData.put("leaveDuration", leaveDuration);

                return leaveData;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(enhancedLeaves);

        } catch (Exception e) {
            logger.error("Error fetching all leaves for admin: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    //  Get employee entitlements summary for admin
    @GetMapping("/entitlements/{employeeEmail}")
    public ResponseEntity<?> getEmployeeEntitlements(@PathVariable String employeeEmail) {
        try {
            User employee = userRepository.findByEmail(employeeEmail);
            if (employee == null) {
                return ResponseEntity.status(404).body("❌ Employee not found");
            }

            Map<String, Object> summary = leaveEntitlementService.getComprehensiveEntitlementSummary(employeeEmail);

            Map<String, Object> employeeDetails = new HashMap<>();
            employeeDetails.put("email", employee.getEmail());
            employeeDetails.put("name", employee.getName());
            employeeDetails.put("fullName", employee.getFullName());
            employeeDetails.put("department", employee.getDepartment() != null ? employee.getDepartment() : "N/A");
            employeeDetails.put("otherDepartments", employee.getOtherDepartments() != null ? employee.getOtherDepartments() : List.of());
            employeeDetails.put("designation", employee.getDesignation() != null ? employee.getDesignation() : "N/A");

            summary.put("employeeDetails", employeeDetails);
            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            logger.error("Error fetching employee entitlements: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    //  Get all employee entitlements (EXCLUDING ADMINS)
    @GetMapping("/entitlements")
    public ResponseEntity<?> getAllEmployeeEntitlements() {
        try {
            List<User> allUsers = userRepository.findAll().stream()
                    .filter(user -> user.getRoles() == null || !user.getRoles().contains("ADMIN"))
                    .collect(Collectors.toList());

            logger.info("Total non-admin users found: {}", allUsers.size());

            List<Map<String, Object>> entitlementSummaries = new ArrayList<>();
            int processedCount = 0;
            int errorCount = 0;

            for (User user : allUsers) {
                try {
                    Map<String, Object> summary = leaveEntitlementService.getEntitlementSummary(user.getEmail());

                    Map<String, Object> employeeDetails = new HashMap<>();
                    employeeDetails.put("email", user.getEmail());
                    employeeDetails.put("name", user.getName());
                    employeeDetails.put("fullName", user.getFullName());
                    employeeDetails.put("department", user.getDepartment() != null ? user.getDepartment() : "N/A");
                    employeeDetails.put("otherDepartments", user.getOtherDepartments() != null ? user.getOtherDepartments() : List.of());
                    employeeDetails.put("designation", user.getDesignation() != null ? user.getDesignation() : "N/A");

                    summary.put("employeeDetails", employeeDetails);

                    try {
                        Map<String, Object> monthlyShortLeaveData = leaveEntitlementService.getEmployeeShortLeaveMonthlyBreakdown(user.getEmail());
                        summary.put("shortLeaveMonthlyDetails", monthlyShortLeaveData);
                    } catch (Exception shortLeaveError) {
                        logger.warn("Error getting short leave monthly data for user {}: {}", user.getEmail(), shortLeaveError.getMessage());
                        summary.put("shortLeaveMonthlyDetails", new HashMap<>());
                    }

                    entitlementSummaries.add(summary);
                    processedCount++;

                } catch (Exception e) {
                    errorCount++;
                    logger.error("Error getting entitlements for user {}: {}", user.getEmail(), e.getMessage(), e);

                    Map<String, Object> fallbackSummary = new HashMap<>();
                    Map<String, Object> employeeDetails = new HashMap<>();
                    employeeDetails.put("email", user.getEmail());
                    employeeDetails.put("name", user.getName());
                    employeeDetails.put("fullName", user.getFullName());
                    employeeDetails.put("department", user.getDepartment() != null ? user.getDepartment() : "N/A");
                    employeeDetails.put("otherDepartments", user.getOtherDepartments() != null ? user.getOtherDepartments() : List.of());
                    employeeDetails.put("designation", user.getDesignation() != null ? user.getDesignation() : "N/A");

                    fallbackSummary.put("employeeDetails", employeeDetails);
                    fallbackSummary.put("entitlements", new ArrayList<>());
                    fallbackSummary.put("shortLeaveMonthlyDetails", new HashMap<>());
                    fallbackSummary.put("error", "Failed to load entitlements");

                    entitlementSummaries.add(fallbackSummary);
                }
            }

            logger.info("Successfully processed: {}, Errors: {}, Total returned: {}",
                    processedCount, errorCount, entitlementSummaries.size());

            return ResponseEntity.ok(entitlementSummaries);

        } catch (Exception e) {
            logger.error("Error fetching all employee entitlements: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        try {
            String jwt = token.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(jwt);

            User user = userRepository.findByEmail(email);
            if (user == null) return ResponseEntity.status(404).body("❌ User not found");

            String currentPassword = body.get("currentPassword");
            String newPassword = body.get("newPassword");

            if (currentPassword == null || newPassword == null)
                return ResponseEntity.badRequest().body("❌ Both passwords are required");

            if (newPassword.length() < 6)
                return ResponseEntity.badRequest().body("❌ Password must be at least 6 characters");

            if (!passwordEncoder.matches(currentPassword, user.getPassword()))
                return ResponseEntity.status(401).body("❌ Current password is incorrect");

            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            return ResponseEntity.ok("✅ Password changed successfully");
        } catch (Exception e) {
            logger.error("Error changing password: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }



// ============================================================================
// ADD this endpoint inside AdminController class
// GET /admin/daily-report?date=2026-03-09
// Returns all employees on leave for the given date
// ============================================================================

//    /**
//     * Daily Leave Report — who is on leave on a given date?
//     *
//     * Returns:
//     *  - employees on APPROVED full-day leave whose range covers the date
//     *  - employees on APPROVED half-day leave on that exact date
//     *  - employees on APPROVED short leave on that exact date
//     *
//     * Query param: date (ISO format: yyyy-MM-dd), defaults to today
//     */
//    @GetMapping("/daily-report")
//    public ResponseEntity<?> getDailyLeaveReport(
//            @RequestParam(required = false) String date) {
//
//        LocalDate reportDate;
//        try {
//            reportDate = (date != null && !date.isBlank())
//                    ? LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
//                    : LocalDate.now();
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body("❌ Invalid date format. Use yyyy-MM-dd");
//        }
//
//        // Fetch all APPROVED leaves
//        List<Leave> approvedLeaves = leaveRepository.findAll().stream()
//                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
//                .collect(Collectors.toList());
//
//        List<Map<String, Object>> onLeave = new ArrayList<>();
//        List<Map<String, Object>> shortLeaveList = new ArrayList<>();
//
//        for (Leave leave : approvedLeaves) {
//            if (leave.getStartDate() == null) continue;
//
//            boolean coversDate = false;
//
//            if (leave.isShortLeave()) {
//                // Short leave — exact date match only
//                if (reportDate.equals(leave.getStartDate())) {
//                    Map<String, Object> entry = buildLeaveEntry(leave, "SHORT");
//                    shortLeaveList.add(entry);
//                }
//                continue;
//            }
//
//            LocalDate end = (leave.getEndDate() != null) ? leave.getEndDate() : leave.getStartDate();
//            if (!reportDate.isBefore(leave.getStartDate()) && !reportDate.isAfter(end)) {
//                coversDate = true;
//            }
//
//            if (coversDate) {
//                String category = leave.isHalfDay() ? "HALF_DAY"
//                        : leave.isMaternityLeave() ? "MATERNITY"
//                        : "FULL_DAY";
//                onLeave.add(buildLeaveEntry(leave, category));
//            }
//        }
//
//        // Summary counts
//        long fullDayCount     = onLeave.stream().filter(e -> "FULL_DAY".equals(e.get("category"))).count();
//        long halfDayCount     = onLeave.stream().filter(e -> "HALF_DAY".equals(e.get("category"))).count();
//        long maternityCount   = onLeave.stream().filter(e -> "MATERNITY".equals(e.get("category"))).count();
//        long shortLeaveCount  = shortLeaveList.size();
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("reportDate", reportDate.toString());
//        response.put("totalOnLeave", onLeave.size());
//        response.put("fullDayCount", fullDayCount);
//        response.put("halfDayCount", halfDayCount);
//        response.put("maternityCount", maternityCount);
//        response.put("shortLeaveCount", shortLeaveCount);
//        response.put("onLeave", onLeave);
//        response.put("shortLeaves", shortLeaveList);
//
//        return ResponseEntity.ok(response);
//    }
//
//    /** Helper: build a leave entry map for the report */
//    private Map<String, Object> buildLeaveEntry(Leave leave, String category) {
//        Map<String, Object> entry = new LinkedHashMap<>();
//        entry.put("employeeEmail",  leave.getEmployeeEmail());
//        entry.put("employeeName",   leave.getEmployeeName());
//        entry.put("leaveType",      leave.getLeaveType());
//        entry.put("category",       category);
//        entry.put("startDate",      leave.getStartDate() != null ? leave.getStartDate().toString() : null);
//        entry.put("endDate",        leave.getEndDate()   != null ? leave.getEndDate().toString()   : null);
//        entry.put("workingDays",    leave.getWorkingDays());
//        entry.put("reason",         leave.getReason());
//
//        // Half-day specific
//        if (leave.isHalfDay()) {
//            entry.put("halfDayPeriod",    leave.getHalfDayPeriod());
//            entry.put("halfDayStartTime", leave.getHalfDayStartTime() != null ? leave.getHalfDayStartTime().toString() : null);
//            entry.put("halfDayEndTime",   leave.getHalfDayEndTime()   != null ? leave.getHalfDayEndTime().toString()   : null);
//        }
//
//        // Short leave specific
//        if (leave.isShortLeave()) {
//            entry.put("shortLeaveStart", leave.getShortLeaveStartTime() != null ? leave.getShortLeaveStartTime().toString() : null);
//            entry.put("shortLeaveEnd",   leave.getShortLeaveEndTime()   != null ? leave.getShortLeaveEndTime().toString()   : null);
//        }
//
//        // Acting officer
//        entry.put("actingOfficerName",  leave.getActingOfficerName());
//        entry.put("actingOfficerEmail", leave.getActingOfficerEmail());
//
//        return entry;
//    }
//
//// ============================================================================
//// Also ADD to LeaveRepository.java (if not already):
////   List<Leave> findByStatus(LeaveStatus status);
//// ============================================================================


    /**
     * Daily Leave Report — who is on leave on a given date?
     *
     * Returns:
     *  - employees on APPROVED full-day leave whose range covers the date
     *  - employees on APPROVED half-day leave on that exact date
     *  - employees on APPROVED short leave on that exact date
     *
     * Query param: date (ISO format: yyyy-MM-dd), defaults to today
     */
    @GetMapping("/daily-report")
    public ResponseEntity<?> getDailyLeaveReport(
            @RequestParam(required = false) String date) {

        LocalDate reportDate;
        try {
            reportDate = (date != null && !date.isBlank())
                    ? LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                    : LocalDate.now();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ Invalid date format. Use yyyy-MM-dd");
        }

        // Fetch all APPROVED leaves
        List<Leave> approvedLeaves = leaveRepository.findAll().stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .collect(Collectors.toList());

        List<Map<String, Object>> onLeave = new ArrayList<>();
        List<Map<String, Object>> shortLeaveList = new ArrayList<>();

        for (Leave leave : approvedLeaves) {
            if (leave.getStartDate() == null) continue;

            boolean coversDate = false;

            if (leave.isShortLeave()) {
                // Short leave — exact date match only
                if (reportDate.equals(leave.getStartDate())) {
                    Map<String, Object> entry = buildLeaveEntry(leave, "SHORT");
                    shortLeaveList.add(entry);
                }
                continue;
            }

            LocalDate end = (leave.getEndDate() != null) ? leave.getEndDate() : leave.getStartDate();
            if (!reportDate.isBefore(leave.getStartDate()) && !reportDate.isAfter(end)) {
                coversDate = true;
            }

            if (coversDate) {
                String category = leave.isHalfDay() ? "HALF_DAY"
                        : leave.isMaternityLeave() ? "MATERNITY"
                        : "FULL_DAY";
                onLeave.add(buildLeaveEntry(leave, category));
            }
        }

        // Summary counts
        long fullDayCount     = onLeave.stream().filter(e -> "FULL_DAY".equals(e.get("category"))).count();
        long halfDayCount     = onLeave.stream().filter(e -> "HALF_DAY".equals(e.get("category"))).count();
        long maternityCount   = onLeave.stream().filter(e -> "MATERNITY".equals(e.get("category"))).count();
        long shortLeaveCount  = shortLeaveList.size();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reportDate", reportDate.toString());
        response.put("totalOnLeave", onLeave.size());
        response.put("fullDayCount", fullDayCount);
        response.put("halfDayCount", halfDayCount);
        response.put("maternityCount", maternityCount);
        response.put("shortLeaveCount", shortLeaveCount);
        response.put("onLeave", onLeave);
        response.put("shortLeaves", shortLeaveList);

        return ResponseEntity.ok(response);
    }

    /** Helper: build a leave entry map for the report */
    private Map<String, Object> buildLeaveEntry(Leave leave, String category) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("employeeEmail",  leave.getEmployeeEmail());
        entry.put("employeeName",   leave.getEmployeeName());

        // ── Fetch department from User (not stored in Leave model) ────────────
        String department = "";
        try {
            User user = userRepository.findByEmail(leave.getEmployeeEmail());
            if (user != null && user.getDepartment() != null) {
                department = user.getDepartment();
            }
        } catch (Exception ignored) {}
        entry.put("department", department);

        entry.put("leaveType",      leave.getLeaveType());
        entry.put("category",       category);
        entry.put("startDate",      leave.getStartDate() != null ? leave.getStartDate().toString() : null);
        entry.put("endDate",        leave.getEndDate()   != null ? leave.getEndDate().toString()   : null);
        entry.put("workingDays",    leave.getWorkingDays());
        entry.put("reason",         leave.getReason());

        // Half-day specific
        if (leave.isHalfDay()) {
            entry.put("halfDayPeriod",    leave.getHalfDayPeriod());
            entry.put("halfDayStartTime", leave.getHalfDayStartTime() != null ? leave.getHalfDayStartTime().toString() : null);
            entry.put("halfDayEndTime",   leave.getHalfDayEndTime()   != null ? leave.getHalfDayEndTime().toString()   : null);
        }

        // Short leave specific
        if (leave.isShortLeave()) {
            entry.put("shortLeaveStart", leave.getShortLeaveStartTime() != null ? leave.getShortLeaveStartTime().toString() : null);
            entry.put("shortLeaveEnd",   leave.getShortLeaveEndTime()   != null ? leave.getShortLeaveEndTime().toString()   : null);
        }

        // Acting officer
        entry.put("actingOfficerName",  leave.getActingOfficerName());
        entry.put("actingOfficerEmail", leave.getActingOfficerEmail());

        return entry;
    }


    // ============================================================================
// ADD THIS TO AdminController.java — Fix incorrect SICK entitlement
// ============================================================================



    // ── Fix one employee's entitlement for a specific year ───────────────────────
// POST /admin/fix-sick-entitlement?email=x@y.com&year=2026
    @PostMapping("/fix-sick-entitlement")
    public ResponseEntity<?> fixSickEntitlement(
            @RequestHeader("Authorization") String token,
            @RequestParam String email,
            @RequestParam int year) {
        try {
            Map<String, Object> result = leaveEntitlementService.correctSickEntitlement(email, year);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Fix ALL employees' entitlement for a specific year ────────────────────────
// POST /admin/fix-sick-entitlement-all?year=2026
    @PostMapping("/fix-sick-entitlement-all")
    public ResponseEntity<?> fixSickEntitlementAll(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int year) {
        try {
            int targetYear = year > 0 ? year : LocalDate.now().getYear();
            List<User> allUsers = userRepository.findAll();

            List<Map<String, Object>> results = new ArrayList<>();
            int fixed = 0, skipped = 0, errors = 0;

            for (User user : allUsers) {
                if (user.getEmail() == null) continue;
                try {
                    Map<String, Object> r = leaveEntitlementService.correctSickEntitlement(user.getEmail(), targetYear);
                    if (Boolean.TRUE.equals(r.get("success"))) {
                        results.add(r);
                        fixed++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    errors++;
                    results.add(Map.of("email", user.getEmail(), "error", e.getMessage()));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "year", targetYear,
                    "totalUsers", allUsers.size(),
                    "fixed", fixed,
                    "skipped", skipped,
                    "errors", errors,
                    "details", results
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

}