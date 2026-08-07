


package com.LeaveDataManagementSystem.LeaveManagement.Service;

import com.LeaveDataManagementSystem.LeaveManagement.Model.*;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
import com.LeaveDataManagementSystem.LeaveManagement.DTO.LeaveRequest;
import com.LeaveDataManagementSystem.LeaveManagement.DTO.LeaveApprovalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.LocalTime;

@Service
public class LeaveService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveService.class);

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LeaveEntitlementService leaveEntitlementService;

    @Autowired
    private WorkingDayCalculator workingDayCalculator;


    private void sendInitialNotification(Leave leave, LeaveRequest leaveRequest) {
        if (leaveRequest.hasActingOfficer()) {
            notificationService.notifyActingOfficer(leave);
        } else if (leaveRequest.hasSupervisingOfficer()) {
            notificationService.notifySupervisingOfficer(leave);
        } else {
            notificationService.notifyApprovalOfficer(leave);
        }
    }

    // ------------------- HELPER: Next status after supervising approval -------------------
    private LeaveStatus getNextStatusAfterSupervisingApproval(Leave leave) {
        if (leave.getApprovalOfficerEmail() != null &&
                !leave.getApprovalOfficerEmail().trim().isEmpty()) {
            return LeaveStatus.PENDING_APPROVAL_OFFICER;
        } else {
            return LeaveStatus.APPROVED;
        }
    }

    // ------------------- HELPER: Next status after acting approval -------------------
    private LeaveStatus getNextStatusAfterActingApproval(Leave leave) {
        if (leave.getSupervisingOfficerEmail() != null &&
                !leave.getSupervisingOfficerEmail().trim().isEmpty()) {
            return LeaveStatus.PENDING_SUPERVISING_OFFICER;
        } else if (leave.getApprovalOfficerEmail() != null &&
                !leave.getApprovalOfficerEmail().trim().isEmpty()) {
            return LeaveStatus.PENDING_APPROVAL_OFFICER;
        } else {
            return LeaveStatus.APPROVED;
        }
    }

    // ------------------- ACTING OFFICER ACTION -------------------
    public String processActingOfficerAction(String leaveId, String email, LeaveApprovalRequest request) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!leave.getActingOfficerEmail().equalsIgnoreCase(email))
            return "You are not authorized to review this leave request";

        if (!leave.getStatus().equals(LeaveStatus.PENDING_ACTING_OFFICER) &&
                !leave.getStatus().equals(LeaveStatus.REJECTED_BY_ACTING_OFFICER))
            return "Leave request has already been processed";

        LocalDateTime originalCreatedAt = leave.getCreatedAt();

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            String entitlementValidation;
            if ("MATERNITY".equals(leave.getLeaveType())) {
                entitlementValidation = validateMaternityLeaveForApproval(leave);
            } else {
                entitlementValidation = leaveEntitlementService.validateLeaveRequest(
                        leave.getEmployeeEmail(), leave.getLeaveType(),
                        leave.getStartDate(), leave.getEndDate());
            }

            if (!"VALID".equals(entitlementValidation)
                    && !"VALID_USE_VACATION".equals(entitlementValidation)) {
                return "Cannot approve: " + entitlementValidation;
            }

            leave.setActingOfficerStatus(ActingOfficerStatus.APPROVED);
            leave.setActingOfficerApprovedAt(LocalDateTime.now());
            leave.setActingOfficerComments(request.getComments());

            LeaveStatus nextStatus = getNextStatusAfterActingApproval(leave);
            leave.setStatus(nextStatus);

            if (originalCreatedAt != null) {
                leave.setCreatedAt(originalCreatedAt);
            }

            leaveRepository.save(leave);

            if (nextStatus == LeaveStatus.PENDING_SUPERVISING_OFFICER) {
                notificationService.notifySupervisingOfficer(leave);
                notificationService.notifyEmployee(leave, "APPROVED", "Acting Officer");
                return "approved";
            } else if (nextStatus == LeaveStatus.PENDING_APPROVAL_OFFICER) {
                notificationService.notifyApprovalOfficer(leave);
                notificationService.notifyEmployee(leave, "APPROVED", "Acting Officer");
                return "approved";
            } else if (nextStatus == LeaveStatus.APPROVED) {
                if ("MATERNITY".equals(leave.getLeaveType())) {
                    logger.info("Maternity leave fully approved. End date will be set by admin before entitlement update.");
                } else {
                    leaveEntitlementService.updateEntitlementOnLeaveApproval(
                            leave.getEmployeeEmail(), leave.getLeaveType(),
                            leave.getStartDate(), leave.getEndDate(),
                            leave.isShortLeave(), leave.isHalfDay(), leave.getWorkingDays());
                }
                notificationService.notifyEmployee(leave, "APPROVED", "Acting Officer");
                return "approved";
            }

        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            leave.setActingOfficerStatus(ActingOfficerStatus.REJECTED);
            leave.setStatus(LeaveStatus.REJECTED_BY_ACTING_OFFICER);
            leave.setActingOfficerComments(request.getComments());

            if (originalCreatedAt != null) {
                leave.setCreatedAt(originalCreatedAt);
            }

            leaveRepository.save(leave);
            notificationService.notifyEmployee(leave, "REJECTED", "Acting Officer");
            return "rejected";
        }

        return "Invalid action. Use APPROVE or REJECT";
    }

    // ------------------- SUPERVISING OFFICER ACTION -------------------
    public String processSupervisingOfficerAction(String leaveId, String email, LeaveApprovalRequest request) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!leave.getSupervisingOfficerEmail().equalsIgnoreCase(email))
            return "You are not authorized to review this leave request";

        if (!leave.getStatus().equals(LeaveStatus.PENDING_SUPERVISING_OFFICER) &&
                !leave.getStatus().equals(LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER))
            return "Leave request is not ready for your review";

        LocalDateTime originalCreatedAt = leave.getCreatedAt();
        LocalDateTime originalActingApprovedAt = leave.getActingOfficerApprovedAt();

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            String entitlementValidation;
            if ("MATERNITY".equals(leave.getLeaveType())) {
                entitlementValidation = validateMaternityLeaveForApproval(leave);
            } else {
                entitlementValidation = leaveEntitlementService.validateLeaveRequest(
                        leave.getEmployeeEmail(), leave.getLeaveType(),
                        leave.getStartDate(), leave.getEndDate());
            }

            if (!"VALID".equals(entitlementValidation)
                    && !"VALID_USE_VACATION".equals(entitlementValidation)) {
                return "Cannot approve: " + entitlementValidation;
            }

            leave.setSupervisingOfficerStatus(SupervisingOfficerStatus.APPROVED);
            leave.setSupervisingOfficerApprovedAt(LocalDateTime.now());
            leave.setSupervisingOfficerComments(request.getComments());

            LeaveStatus nextStatus = getNextStatusAfterSupervisingApproval(leave);
            leave.setStatus(nextStatus);

            if (originalCreatedAt != null) {
                leave.setCreatedAt(originalCreatedAt);
            }
            if (originalActingApprovedAt != null) {
                leave.setActingOfficerApprovedAt(originalActingApprovedAt);
            }

            leaveRepository.save(leave);

            if (nextStatus == LeaveStatus.PENDING_APPROVAL_OFFICER) {
                notificationService.notifyApprovalOfficer(leave);
                notificationService.notifyEmployee(leave, "APPROVED", "Supervising Officer");
                return "approved";
            } else if (nextStatus == LeaveStatus.APPROVED) {
                if ("MATERNITY".equals(leave.getLeaveType())) {
                    logger.info("Maternity leave fully approved. End date will be set by admin before entitlement update.");
                } else {
                    leaveEntitlementService.updateEntitlementOnLeaveApproval(
                            leave.getEmployeeEmail(), leave.getLeaveType(),
                            leave.getStartDate(), leave.getEndDate(),
                            leave.isShortLeave(), leave.isHalfDay(), leave.getWorkingDays());
                }
                notificationService.notifyEmployee(leave, "APPROVED", "Supervising Officer");
                return "approved";
            }

        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            leave.setSupervisingOfficerStatus(SupervisingOfficerStatus.REJECTED);
            leave.setStatus(LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER);
            leave.setSupervisingOfficerComments(request.getComments());

            if (originalCreatedAt != null) {
                leave.setCreatedAt(originalCreatedAt);
            }
            if (originalActingApprovedAt != null) {
                leave.setActingOfficerApprovedAt(originalActingApprovedAt);
            }

            leaveRepository.save(leave);
            notificationService.notifyEmployee(leave, "REJECTED", "Supervising Officer");
            return "rejected";
        }

        return "Invalid action. Use APPROVE or REJECT";
    }

    // ------------------- APPROVAL OFFICER ACTION -------------------
    public String processApprovalOfficerAction(String leaveId, String email, LeaveApprovalRequest request) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!leave.getApprovalOfficerEmail().equalsIgnoreCase(email))
            return "You are not authorized to review this leave request";

        if (!leave.getStatus().equals(LeaveStatus.PENDING_APPROVAL_OFFICER) &&
                !leave.getStatus().equals(LeaveStatus.REJECTED_BY_APPROVAL_OFFICER))
            return "Leave request is not ready for your review";

        LocalDateTime originalCreatedAt = leave.getCreatedAt();
        LocalDateTime originalActingApprovedAt = leave.getActingOfficerApprovedAt();
        LocalDateTime originalSupervisingApprovedAt = leave.getSupervisingOfficerApprovedAt();

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            String entitlementValidation;
            if ("MATERNITY".equals(leave.getLeaveType())) {
                entitlementValidation = validateMaternityLeaveForApproval(leave);
            } else {
                entitlementValidation = leaveEntitlementService.validateLeaveRequest(
                        leave.getEmployeeEmail(), leave.getLeaveType(),
                        leave.getStartDate(), leave.getEndDate());
            }

            if (!"VALID".equals(entitlementValidation)
                    && !"VALID_USE_VACATION".equals(entitlementValidation)) {
                return "Cannot approve: " + entitlementValidation;
            }

            leave.setApprovalOfficerStatus(ApprovalOfficerStatus.APPROVED);
            leave.setStatus(LeaveStatus.APPROVED);
            leave.setApprovalOfficerApprovedAt(LocalDateTime.now());
            leave.setApprovalOfficerComments(request.getComments());

            if (originalCreatedAt != null) {
                leave.setCreatedAt(originalCreatedAt);
            }
            if (originalActingApprovedAt != null) {
                leave.setActingOfficerApprovedAt(originalActingApprovedAt);
            }
            if (originalSupervisingApprovedAt != null) {
                leave.setSupervisingOfficerApprovedAt(originalSupervisingApprovedAt);
            }

            leaveRepository.save(leave);

            if ("MATERNITY".equals(leave.getLeaveType())) {
                logger.info("Maternity leave approved. End date will be set by admin before entitlement update.");
            } else {
                leaveEntitlementService.updateEntitlementOnLeaveApproval(
                        leave.getEmployeeEmail(),
                        leave.getLeaveType(),
                        leave.getStartDate(),
                        leave.getEndDate(),
                        leave.isShortLeave(),
                        leave.isHalfDay(),
                        leave.getWorkingDays());
            }

            notificationService.notifyEmployee(leave, "APPROVED", "Approval Officer");
            return "approved";

        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            leave.setApprovalOfficerStatus(ApprovalOfficerStatus.REJECTED);
            leave.setStatus(LeaveStatus.REJECTED_BY_APPROVAL_OFFICER);
            leave.setApprovalOfficerComments(request.getComments());

            if (originalCreatedAt != null) {
                leave.setCreatedAt(originalCreatedAt);
            }
            if (originalActingApprovedAt != null) {
                leave.setActingOfficerApprovedAt(originalActingApprovedAt);
            }
            if (originalSupervisingApprovedAt != null) {
                leave.setSupervisingOfficerApprovedAt(originalSupervisingApprovedAt);
            }

            leaveRepository.save(leave);
            notificationService.notifyEmployee(leave, "REJECTED", "Approval Officer");
            return "rejected";
        }

        return "Invalid action. Use APPROVE or REJECT";
    }

    // ------------------- EMPLOYEE LEAVES -------------------
    public List<Leave> getEmployeeLeaves(String employeeEmail) {
        return leaveRepository.findByEmployeeEmailOrderByCreatedAtDesc(employeeEmail);
    }

    // ------------------- PENDING LEAVES -------------------
    public List<Leave> getPendingLeavesForActingOfficer(String email) {
        return leaveRepository.findByActingOfficerEmailAndStatusOrderByCreatedAtAsc(
                email, LeaveStatus.PENDING_ACTING_OFFICER);
    }

    public List<Leave> getPendingLeavesForApprovalOfficer(String email) {
        return leaveRepository.findByApprovalOfficerEmailAndStatusOrderByCreatedAtAsc(
                email, LeaveStatus.PENDING_APPROVAL_OFFICER);
    }

    public List<Leave> getPendingLeavesForSupervisingOfficer(String email) {
        return leaveRepository.findBySupervisingOfficerEmailAndStatusOrderByCreatedAtAsc(
                email, LeaveStatus.PENDING_SUPERVISING_OFFICER);
    }

    // ------------------- DASHBOARD COUNTS -------------------
    public long getPendingCountForActingOfficer(String email) {
        return leaveRepository.countByActingOfficerEmailAndStatus(email, LeaveStatus.PENDING_ACTING_OFFICER);
    }

    public long getPendingCountForApprovalOfficer(String email) {
        return leaveRepository.countByApprovalOfficerEmailAndStatus(email, LeaveStatus.PENDING_APPROVAL_OFFICER);
    }

    public long getPendingCountForSupervisingOfficer(String email) {
        return leaveRepository.countBySupervisingOfficerEmailAndStatus(
                email, LeaveStatus.PENDING_SUPERVISING_OFFICER);
    }

    // ------------------- CANCEL LEAVE REQUEST -------------------
    public String cancelLeaveRequest(String leaveId, String employeeEmail, String cancellationReason) {
        try {
            Leave leave = leaveRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leave request not found"));

            if (!leave.getEmployeeEmail().equalsIgnoreCase(employeeEmail)) {
                return "You are not authorized to cancel this leave request";
            }

            if (!canCancelLeave(leave)) {
                if (leave.isCancelled()) {
                    return "Leave request has already been cancelled";
                }
                if (leave.getStatus().toString().contains("REJECTED")) {
                    return "Cannot cancel a rejected leave request";
                }
                if (!leave.getStartDate().isAfter(LocalDate.now())) {
                    return "Cannot cancel a leave request that has already started or is in the past";
                }
                return "This leave request cannot be cancelled";
            }

            boolean wasApproved = leave.getStatus() == LeaveStatus.APPROVED;

            LocalDateTime originalCreatedAt = leave.getCreatedAt();
            LocalDateTime originalActingApprovedAt = leave.getActingOfficerApprovedAt();
            LocalDateTime originalSupervisingApprovedAt = leave.getSupervisingOfficerApprovedAt();

            leave.setCancelled(true);
            leave.setStatus(LeaveStatus.CANCELLED_BY_EMPLOYEE);
            leave.setCancelledAt(LocalDateTime.now());
            leave.setCancelledBy(employeeEmail);
            leave.setCancellationReason(cancellationReason);

            if (originalCreatedAt != null) {
                leave.setCreatedAt(originalCreatedAt);
            }
            if (originalActingApprovedAt != null) {
                leave.setActingOfficerApprovedAt(originalActingApprovedAt);
            }
            if (originalSupervisingApprovedAt != null) {
                leave.setSupervisingOfficerApprovedAt(originalSupervisingApprovedAt);
            }

            leaveRepository.save(leave);

            if (wasApproved) {
                logger.info("Reverting entitlements for cancelled approved leave: {}", leaveId);
                leaveEntitlementService.revertEntitlementOnLeaveRejection(
                        leave.getEmployeeEmail(),
                        leave.getLeaveType(),
                        leave.getStartDate(),
                        leave.getEndDate(),
                        leave.isShortLeave(),
                        leave.isHalfDay(),
                        leave.getWorkingDays());
                logger.info("Entitlements reverted successfully for leave: {}", leaveId);
            } else {
                logger.info("Leave was not approved, no entitlement reversion needed for leave: {}", leaveId);
            }

            try {
                notificationService.notifyLeaveCancellation(leave, employeeEmail);
            } catch (Exception e) {
                logger.warn("Failed to send cancellation notification: {}", e.getMessage());
            }

            return "Leave request cancelled successfully";

        } catch (Exception e) {
            logger.error("Error cancelling leave request: {}", e.getMessage(), e);
            return "Failed to cancel leave request: " + e.getMessage();
        }
    }

    // ------------------- CAN CANCEL LEAVE (private) -------------------
    private boolean canCancelLeave(Leave leave) {
        if (leave.isCancelled()) {
            return false;
        }

        if (leave.getStatus() == LeaveStatus.REJECTED_BY_ACTING_OFFICER ||
                leave.getStatus() == LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER ||
                leave.getStatus() == LeaveStatus.REJECTED_BY_APPROVAL_OFFICER) {
            return false;
        }

        LocalDate today = LocalDate.now();
        return !leave.getStartDate().isBefore(today);
    }

    // ------------------- CAN CANCEL LEAVE (public) -------------------
    public boolean canCancelLeave(String leaveId, String employeeEmail) {
        try {
            Leave leave = leaveRepository.findById(leaveId).orElse(null);
            if (leave == null)
                return false;

            if (!leave.getEmployeeEmail().equalsIgnoreCase(employeeEmail))
                return false;

            return canCancelLeave(leave);
        } catch (Exception e) {
            logger.error("Error checking if leave can be cancelled: {}", e.getMessage());
            return false;
        }
    }

    // ------------------- GET CANCELLABLE LEAVES -------------------
    public List<Leave> getCancellableLeaves(String employeeEmail) {
        LocalDate currentDate = LocalDate.now();

        return leaveRepository.findByEmployeeEmailOrderByCreatedAtDesc(employeeEmail)
                .stream()
                .filter(leave -> {
                    return !leave.isCancelled() &&
                            !leave.getStatus().toString().contains("REJECTED") &&
                            !leave.getStartDate().isBefore(currentDate);
                })
                .toList();
    }

    // ------------------- ENTITLEMENT METHODS -------------------
    public List<LeaveEntitlement> getEmployeeEntitlements(String employeeEmail) {
        return leaveEntitlementService.getEmployeeEntitlements(employeeEmail);
    }

    public Map<String, Object> getEmployeeEntitlementSummary(String employeeEmail) {
        return leaveEntitlementService.getEntitlementSummary(employeeEmail);
    }

    public void recalculateEmployeeEntitlements(String employeeEmail) {
        leaveEntitlementService.recalculateEntitlements(employeeEmail);
    }

    // ------------------- ADMIN ENTITLEMENT METHODS -------------------
    public void adjustEmployeeEntitlement(String employeeEmail, String leaveType,
                                          int year, int newTotalEntitlement) {
        leaveEntitlementService.adjustEntitlement(employeeEmail, leaveType, year, newTotalEntitlement);
    }

    public void initializeEntitlementsForNewEmployee(String employeeEmail) {
        leaveEntitlementService.initializeEntitlementsForEmployee(employeeEmail);
    }

    public String validateLeaveRequest(String employeeEmail, String leaveType,
                                       LocalDate startDate, LocalDate endDate) {
        return leaveEntitlementService.validateLeaveRequest(employeeEmail, leaveType, startDate, endDate);
    }

    // ------------------- OFFICERS FOR EMPLOYEE -------------------
    // now includes users whose otherDepartments contains the employee's dept
    public Map<String, Object> getOfficersForEmployee(String employeeEmail) {
        User employee = userRepository.findByEmail(employeeEmail);
        if (employee == null || employee.getDepartment() == null)
            return Map.of(
                    "acting", List.of(),
                    "supervising", List.of(),
                    "approval", List.of(),
                    "department", "No Department");

        String dept = employee.getDepartment();

        List<User> departmentEmployees = userRepository
                .findByDepartmentOrOtherDepartmentsContaining(dept)
                .stream()
                .filter(u -> !u.getEmail().equalsIgnoreCase(employeeEmail))
                .toList();

        return Map.of(
                "acting", departmentEmployees,
                "supervising", departmentEmployees,
                "approval", departmentEmployees,
                "department", dept);
    }

    // uses findByDepartmentOrOtherDepartmentsContaining
    public List<User> getActingOfficersByDepartment(String department) {
        return userRepository.findByDepartmentOrOtherDepartmentsContaining(department);
    }

    // uses findByDepartmentOrOtherDepartmentsContaining
    public List<User> getApprovalOfficersByDepartment(String department) {
        return userRepository.findByDepartmentOrOtherDepartmentsContaining(department);
    }

    // uses findByDepartmentOrOtherDepartmentsContaining
    public List<User> getActingOfficersByDepartmentExcluding(String department, String excludeEmail) {
        return userRepository.findByDepartmentOrOtherDepartmentsContaining(department)
                .stream()
                .filter(user -> !user.getEmail().equalsIgnoreCase(excludeEmail))
                .toList();
    }

    // uses findByDepartmentOrOtherDepartmentsContaining + deduplication
    public List<User> getApprovalOfficersByDepartmentExcluding(String department, String excludeEmail) {
        List<User> deptUsers = userRepository.findByDepartmentOrOtherDepartmentsContaining(department);
        List<User> allDeptUsers = userRepository.findByDepartment("All");

        return Stream.concat(deptUsers.stream(), allDeptUsers.stream())
                .filter(user -> !user.getEmail().equalsIgnoreCase(excludeEmail))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(User::getEmail, u -> u, (a, b) -> a),
                        map -> new java.util.ArrayList<>(map.values())
                ));
    }

    public List<String> getAllDepartmentsWithOfficers() {
        return userRepository.findAll().stream()
                .map(User::getDepartment)
                .filter(dept -> dept != null && !dept.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    // ------------------- COMPREHENSIVE ENTITLEMENT SUMMARY -------------------
    public Map<String, Object> getComprehensiveEmployeeEntitlementSummary(String email) {
        List<LeaveEntitlement> entitlements = leaveEntitlementService.getEmployeeEntitlements(email);

        return entitlements.stream().collect(Collectors.toMap(
                LeaveEntitlement::getLeaveType,
                e -> Map.of(
                        "total", e.getTotalEntitlement(),
                        "used", e.getUsedDays(),
                        "remaining", e.getRemainingDays())));
    }

    // ------------------- HALF-DAY VALIDATION -------------------
    public String validateHalfDayLeaveRequest(String email, String leaveType, LocalDate date, String halfDayPeriod) {
        String validation = leaveEntitlementService.validateLeaveRequest(email, "CASUAL", date, date);

        if (!"VALID".equals(validation)) {
            return validation;
        }

        List<Leave> existing = leaveRepository.findByEmployeeEmailAndDateRange(email, date, date);
        boolean alreadyHalfDay = existing.stream()
                .anyMatch(l -> "HALF_DAY".equals(l.getLeaveType()) && l.getStartDate().equals(date));

        if (alreadyHalfDay) {
            return "You already have a half-day leave on this date";
        }

        return "VALID";
    }

    // ------------------- SHORT LEAVE VALIDATION -------------------
    public String validateShortLeaveRequest(String email, LocalDate date) {
        LocalDate startOfMonth = date.withDayOfMonth(1);
        LocalDate endOfMonth = date.withDayOfMonth(date.lengthOfMonth());

        long shortLeavesThisMonth = leaveRepository.findByEmployeeEmailAndDateRange(email, startOfMonth, endOfMonth)
                .stream()
                .filter(l -> "SHORT".equals(l.getLeaveType()))
                .count();

        if (shortLeavesThisMonth >= 3) {
            return "You have already taken the maximum number of short leaves this month";
        }

        return "VALID";
    }

    // ------------------- CREATE LEAVE FROM REQUEST -------------------
    private Leave createLeaveFromRequest(User employee, LeaveRequest leaveRequest,
                                         User actingOfficer, User supervisingOfficer, User approvalOfficer) {
        Leave leave;

        if ("SHORT".equals(leaveRequest.getLeaveType())) {
            leave = new Leave(
                    employee.getEmail(), employee.getName(),
                    leaveRequest.getStartDate(),
                    leaveRequest.getStartTime(), leaveRequest.getEndTime(),
                    leaveRequest.getReason(),
                    actingOfficer != null ? actingOfficer.getEmail() : null,
                    actingOfficer != null ? actingOfficer.getName() : null,
                    supervisingOfficer != null ? supervisingOfficer.getEmail() : null,
                    supervisingOfficer != null ? supervisingOfficer.getName() : null,
                    approvalOfficer.getEmail(), approvalOfficer.getName());
        } else if ("HALF_DAY".equals(leaveRequest.getLeaveType())) {
            leave = new Leave(
                    employee.getEmail(), employee.getName(),
                    "HALF_DAY",
                    leaveRequest.getStartDate(),
                    leaveRequest.getHalfDayPeriod(),
                    leaveRequest.getHalfDayStartTime(),   // ← NEW
                    leaveRequest.getHalfDayEndTime(),     // ← NEW
                    leaveRequest.getReason(),
                    actingOfficer != null ? actingOfficer.getEmail() : null,
                    actingOfficer != null ? actingOfficer.getName() : null,
                    supervisingOfficer != null ? supervisingOfficer.getEmail() : null,
                    supervisingOfficer != null ? supervisingOfficer.getName() : null,
                    approvalOfficer.getEmail(), approvalOfficer.getName());

        } else if ("MATERNITY".equals(leaveRequest.getLeaveType())) {
            leave = new Leave(
                    employee.getEmail(), employee.getName(),
                    leaveRequest.getStartDate(),
                    leaveRequest.getMaternityLeaveType(),
                    leaveRequest.getReason(),
                    actingOfficer != null ? actingOfficer.getEmail() : null,
                    actingOfficer != null ? actingOfficer.getName() : null,
                    supervisingOfficer != null ? supervisingOfficer.getEmail() : null,
                    supervisingOfficer != null ? supervisingOfficer.getName() : null,
                    approvalOfficer.getEmail(), approvalOfficer.getName());
        } else {
            leave = new Leave(
                    employee.getEmail(), employee.getName(),
                    leaveRequest.getLeaveType(),
                    leaveRequest.getStartDate(), leaveRequest.getEndDate(),
                    leaveRequest.getReason(),
                    actingOfficer != null ? actingOfficer.getEmail() : null,
                    actingOfficer != null ? actingOfficer.getName() : null,
                    supervisingOfficer != null ? supervisingOfficer.getEmail() : null,
                    supervisingOfficer != null ? supervisingOfficer.getName() : null,
                    approvalOfficer.getEmail(), approvalOfficer.getName());
        }

        // Stable identifier — set regardless of which branch created the Leave
        leave.setUserId(employee.getId());

        return leave;
    }

    // ------------------- SUBMIT LEAVE REQUEST -------------------
    public String submitLeaveRequest(String employeeEmail, LeaveRequest leaveRequest) {
        User employee = userRepository.findByEmail(employeeEmail);
        if (employee == null)
            return "Employee not found";

        if (!leaveRequest.hasApprovalOfficer()) {
            return "Approval officer is mandatory";
        }

        User actingOfficer = null;
        User supervisingOfficer = null;
        User approvalOfficer = null;

        // ── Validate acting officer ──
        if (leaveRequest.hasActingOfficer()) {
            actingOfficer = userRepository.findByEmail(leaveRequest.getActingOfficerEmail());
            if (actingOfficer == null)
                return "Acting officer not found";

            // UPDATED: allow if officer belongs to employee's dept via primary OR otherDepartments
            if (!actingOfficer.belongsToDepartment(employee.getDepartment()))
                return "Acting officer must be from the same department";

            if (employee.getEmail().equalsIgnoreCase(actingOfficer.getEmail()))
                return "You cannot select yourself as acting officer";
        }

        // ── Validate supervising officer ──
        if (leaveRequest.hasSupervisingOfficer()) {
            supervisingOfficer = userRepository.findByEmail(leaveRequest.getSupervisingOfficerEmail());
            if (supervisingOfficer == null)
                return "Supervising officer not found";

            // UPDATED: allow if officer belongs to employee's dept via primary OR otherDepartments
            if (!supervisingOfficer.belongsToDepartment(employee.getDepartment()))
                return "Supervising officer must be from the same department";

            if (employee.getEmail().equalsIgnoreCase(supervisingOfficer.getEmail()))
                return "You cannot select yourself as supervising officer";
        }

        // ── Validate approval officer (mandatory) ──
        approvalOfficer = userRepository.findByEmail(leaveRequest.getApprovalOfficerEmail());
        if (approvalOfficer == null)
            return "Approval officer not found";

        // allow if officer belongs to employee's dept via primary OR otherDepartments, or has "All" dept
        boolean approvalOfficerValid =
                approvalOfficer.belongsToDepartment(employee.getDepartment()) ||
                        "All".equalsIgnoreCase(approvalOfficer.getDepartment());

        if (!approvalOfficerValid)
            return "Approval officer must be from the same department or have 'All' department access";

        if (employee.getEmail().equalsIgnoreCase(approvalOfficer.getEmail()))
            return "You cannot select yourself as approval officer";

        // ── Cross-validation between officers ──

        // NOTE: Acting officer and supervising officer are now allowed to be the same person.
        if (leaveRequest.hasActingOfficer()) {
            if (actingOfficer.getEmail().equalsIgnoreCase(approvalOfficer.getEmail()))
                return "Acting officer and approval officer must be different";
        }

        if (leaveRequest.hasSupervisingOfficer()) {
            if (supervisingOfficer.getEmail().equalsIgnoreCase(approvalOfficer.getEmail()))
                return "Supervising officer and approval officer must be different";
        }

        // ── Calculate working days and validate entitlements ──
        String entitlementValidation;
        Map<String, Integer> workingDaysBreakdown = null;
        int actualWorkingDays = 0;

        if ("SHORT".equals(leaveRequest.getLeaveType())) {
            actualWorkingDays = 0;
            entitlementValidation = leaveEntitlementService.validateShortLeaveRequest(
                    employeeEmail, leaveRequest.getStartDate());
        } else if ("HALF_DAY".equals(leaveRequest.getLeaveType())) {
            if (!workingDayCalculator.isWorkingDay(leaveRequest.getStartDate())) {
                return "Half-day leave cannot be taken on weekends or public holidays";
            }
            actualWorkingDays = 0;
            entitlementValidation = leaveEntitlementService.validateLeaveRequest(
                    employeeEmail, "HALF_DAY",
                    leaveRequest.getStartDate(), leaveRequest.getEndDate(),
                    true, leaveRequest.getHalfDayPeriod());
        } else if ("MATERNITY".equals(leaveRequest.getLeaveType())) {
            entitlementValidation = validateMaternityLeaveRequest(employeeEmail, leaveRequest);
        } else {
            logger.info("Calculating working days for {} from {} to {}",
                    employeeEmail, leaveRequest.getStartDate(), leaveRequest.getEndDate());

            workingDaysBreakdown = workingDayCalculator.calculateWorkingDays(
                    leaveRequest.getStartDate(), leaveRequest.getEndDate());

            actualWorkingDays = workingDaysBreakdown.get("workingDays");

            logger.info("Working days calculation result: Total={}, Working={}, Weekends={}, Holidays={}",
                    workingDaysBreakdown.get("totalDays"),
                    actualWorkingDays,
                    workingDaysBreakdown.get("weekendDays"),
                    workingDaysBreakdown.get("publicHolidays"));

            if (actualWorkingDays == 0) {
                return "Selected leave period contains only weekends and public holidays. No working days to deduct.";
            }

            entitlementValidation = leaveEntitlementService.validateLeaveRequestWithWorkingDays(
                    employeeEmail, leaveRequest.getLeaveType(),
                    leaveRequest.getStartDate(), leaveRequest.getEndDate(), actualWorkingDays);
        }

        if (!"VALID".equals(entitlementValidation)
                && !"VALID_USE_VACATION".equals(entitlementValidation)) {
            return entitlementValidation;
        }

        // ── Overlapping leave check ──
        if (!"SHORT".equals(leaveRequest.getLeaveType()) && !"MATERNITY".equals(leaveRequest.getLeaveType())) {
            List<Leave> overlapping = leaveRepository.findOverlappingLeaves(
                            employeeEmail, leaveRequest.getStartDate(), leaveRequest.getEndDate()).stream()
                    .filter(l -> l.getStatus() != LeaveStatus.REJECTED_BY_ACTING_OFFICER &&
                            l.getStatus() != LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER &&
                            l.getStatus() != LeaveStatus.REJECTED_BY_APPROVAL_OFFICER &&
                            !l.isCancelled())
                    .toList();
            if (!overlapping.isEmpty())
                return "You already have overlapping leave requests";
        }

        // ── Create Leave object ──
        Leave leave = createLeaveFromRequest(employee, leaveRequest, actingOfficer, supervisingOfficer,
                approvalOfficer);

        // ── Save working days breakdown ──
        if (workingDaysBreakdown != null) {
            leave.setWorkingDays(workingDaysBreakdown.get("workingDays"));
            leave.setTotalDays(workingDaysBreakdown.get("totalDays"));
            leave.setWeekendDays(workingDaysBreakdown.get("weekendDays"));
            leave.setPublicHolidays(workingDaysBreakdown.get("publicHolidays"));

            logger.info("Saved working days breakdown: workingDays={}, totalDays={}, weekends={}, holidays={}",
                    leave.getWorkingDays(), leave.getTotalDays(), leave.getWeekendDays(), leave.getPublicHolidays());
        } else if ("HALF_DAY".equals(leaveRequest.getLeaveType())) {
            leave.setWorkingDays(0);
            leave.setTotalDays(1);
            leave.setWeekendDays(0);
            leave.setPublicHolidays(0);
        } else if ("SHORT".equals(leaveRequest.getLeaveType())) {
            leave.setWorkingDays(0);
            leave.setTotalDays(0);
            leave.setWeekendDays(0);
            leave.setPublicHolidays(0);
        }

        // ── Set workflow status ──
        leave.setStatus(leaveRequest.getInitialWorkflowStatus());

        if (leaveRequest.hasActingOfficer()) {
            leave.setActingOfficerStatus(ActingOfficerStatus.PENDING);
        } else {
            leave.setActingOfficerStatus(ActingOfficerStatus.NOT_REQUIRED);
        }

        if (leaveRequest.hasSupervisingOfficer()) {
            leave.setSupervisingOfficerStatus(SupervisingOfficerStatus.PENDING);
        } else {
            leave.setSupervisingOfficerStatus(SupervisingOfficerStatus.NOT_REQUIRED);
        }

        leave.setApprovalOfficerStatus(ApprovalOfficerStatus.PENDING);

        leaveRepository.save(leave);

        sendInitialNotification(leave, leaveRequest);

        if (actualWorkingDays > 0) {
            logger.info("Leave submitted for {}: {} working days (from {} to {}), Total calendar days: {}",
                    employeeEmail, actualWorkingDays, leaveRequest.getStartDate(),
                    leaveRequest.getEndDate(), leave.getTotalDays());
        }

        return "Leave request submitted successfully";
    }

    // ------------------- VALIDATE MATERNITY LEAVE REQUEST -------------------
    public String validateMaternityLeaveRequest(String employeeEmail, LeaveRequest leaveRequest) {
        if (leaveRequest.getMaternityLeaveType() == null || leaveRequest.getMaternityLeaveType().trim().isEmpty()) {
            return "Maternity leave type is required";
        }

        if (!Arrays.asList("FULL_PAY", "HALF_PAY", "NO_PAY").contains(leaveRequest.getMaternityLeaveType())) {
            return "Invalid maternity leave type. Must be FULL_PAY, HALF_PAY, or NO_PAY";
        }

        List<Leave> existingMaternityLeaves = leaveRepository.findByEmployeeEmailOrderByCreatedAtDesc(employeeEmail)
                .stream()
                .filter(leave -> "MATERNITY".equals(leave.getLeaveType()) &&
                        !leave.isCancelled() &&
                        leave.getStatus() != LeaveStatus.REJECTED_BY_ACTING_OFFICER &&
                        leave.getStatus() != LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER &&
                        leave.getStatus() != LeaveStatus.REJECTED_BY_APPROVAL_OFFICER)
                .collect(Collectors.toList());

        if (!existingMaternityLeaves.isEmpty()) {
            LocalDate newLeaveStartDate = leaveRequest.getStartDate();

            for (Leave existingLeave : existingMaternityLeaves) {
                if (existingLeave.getStatus() == LeaveStatus.PENDING_ACTING_OFFICER ||
                        existingLeave.getStatus() == LeaveStatus.PENDING_SUPERVISING_OFFICER ||
                        existingLeave.getStatus() == LeaveStatus.PENDING_APPROVAL_OFFICER) {
                    return "You already have a pending maternity leave request. Please wait for it to be processed or cancel it first.";
                }

                if (existingLeave.getStatus() == LeaveStatus.APPROVED) {
                    if (existingLeave.getEndDate() != null) {
                        if (newLeaveStartDate.isBefore(existingLeave.getEndDate().plusDays(1))) {
                            return String.format(
                                    "You have an existing maternity leave from %s to %s (%s). " +
                                            "New maternity leave must start after %s.",
                                    existingLeave.getStartDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                                    existingLeave.getEndDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                                    formatMaternityLeaveType(existingLeave.getMaternityLeaveType()),
                                    existingLeave.getEndDate().plusDays(1)
                                            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
                        }
                    } else {
                        if (isContinuationRequest(existingLeave, leaveRequest)) {
                            logger.info("Allowing maternity leave continuation request for employee: {}", employeeEmail);
                        } else {
                            return "You have an existing approved maternity leave without an end date set. " +
                                    "Please contact admin to set the end date first, or ensure your new request is a valid continuation.";
                        }
                    }
                }
            }
        }

        return "VALID";
    }


    public String editLeaveDates(String leaveId, String employeeEmail,
                                 LocalDate newStartDate, LocalDate newEndDate,
                                 LocalTime newStartTime, LocalTime newEndTime,
                                 String newHalfDayPeriod, String reason) {
        try {
            Leave leave = leaveRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leave request not found"));

            if (!leave.getEmployeeEmail().equalsIgnoreCase(employeeEmail))
                return "You are not authorized to edit this leave request";

            if (leave.isCancelled() || leave.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE)
                return "Cancelled leave cannot be edited";

            if (leave.getStatus() == LeaveStatus.REJECTED_BY_ACTING_OFFICER ||
                    leave.getStatus() == LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER ||
                    leave.getStatus() == LeaveStatus.REJECTED_BY_APPROVAL_OFFICER)
                return "Rejected leave cannot be edited";

            LocalDate today = LocalDate.now();
            if (leave.getStartDate().isBefore(today))
                return "Past leave dates cannot be edited";

            LocalDateTime originalCreatedAt = leave.getCreatedAt();

            // ───────────── SHORT LEAVE: time only, same day ─────────────
            if (leave.isShortLeave()) {
                if (newStartTime == null || newEndTime == null)
                    return "Start time and end time are required";
                if (!newEndTime.isAfter(newStartTime))
                    return "End time must be after start time";

                leave.setShortLeaveStartTime(newStartTime);
                leave.setShortLeaveEndTime(newEndTime);

                if (originalCreatedAt != null) leave.setCreatedAt(originalCreatedAt);
                leaveRepository.save(leave);

                logger.info("Short leave time updated for {}: {} - {} (reason: {})",
                        employeeEmail, newStartTime, newEndTime, reason);
                return "Leave time updated successfully";
            }

            // ───────────── HALF DAY: date + period + time ─────────────
            if (leave.isHalfDay()) {
                LocalDate targetDate = newStartDate != null ? newStartDate : leave.getStartDate();

                if (targetDate.isBefore(today))
                    return "New date cannot be in the past";
                if (!workingDayCalculator.isWorkingDay(targetDate))
                    return "Half-day leave cannot be on weekends or public holidays";

                boolean wasApproved = leave.getStatus() == LeaveStatus.APPROVED;

                // Overlap check if date is actually changing
                if (!targetDate.equals(leave.getStartDate())) {
                    List<Leave> overlapping = leaveRepository.findOverlappingLeaves(
                                    employeeEmail, targetDate, targetDate).stream()
                            .filter(l -> !l.getId().equals(leaveId) &&
                                    l.getStatus() != LeaveStatus.REJECTED_BY_ACTING_OFFICER &&
                                    l.getStatus() != LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER &&
                                    l.getStatus() != LeaveStatus.REJECTED_BY_APPROVAL_OFFICER &&
                                    !l.isCancelled())
                            .toList();
                    if (!overlapping.isEmpty())
                        return "New date overlaps with an existing leave request";
                }

                // Half-day entitlement is date-independent (always 0.5), so no need to
                // revert/reapply on date-only changes — only if status changes elsewhere.
                leave.setStartDate(targetDate);
                leave.setEndDate(targetDate);
                if (newHalfDayPeriod != null && !newHalfDayPeriod.isBlank())
                    leave.setHalfDayPeriod(newHalfDayPeriod);
                if (newStartTime != null) leave.setHalfDayStartTime(newStartTime);
                if (newEndTime != null)   leave.setHalfDayEndTime(newEndTime);

                if (originalCreatedAt != null) leave.setCreatedAt(originalCreatedAt);
                leaveRepository.save(leave);

                logger.info("Half-day leave updated for {}: date={}, period={}, {}-{} (reason: {})",
                        employeeEmail, targetDate, leave.getHalfDayPeriod(), newStartTime, newEndTime, reason);
                return "Leave updated successfully";
            }

            // ───────────── REGULAR / MATERNITY etc: existing date-only logic ─────────────
            if (newStartDate == null || newEndDate == null)
                return "Start date and end date are required";

            if (newStartDate.isBefore(today))
                return "New start date cannot be in the past";
            if (newEndDate.isBefore(newStartDate))
                return "End date must be on or after start date";

            boolean wasApproved = leave.getStatus() == LeaveStatus.APPROVED;

            if (wasApproved) {
                leaveEntitlementService.revertEntitlementOnLeaveRejection(
                        leave.getEmployeeEmail(), leave.getLeaveType(),
                        leave.getStartDate(), leave.getEndDate(),
                        leave.isShortLeave(), leave.isHalfDay(), leave.getWorkingDays());
            }

            Map<String, Integer> workingDaysBreakdown =
                    workingDayCalculator.calculateWorkingDays(newStartDate, newEndDate);
            int actualWorkingDays = workingDaysBreakdown.get("workingDays");

            if (actualWorkingDays == 0)
                return "Selected leave period contains only weekends and public holidays. No working days to deduct.";

            String entitlementValidation = leaveEntitlementService.validateLeaveRequestWithWorkingDays(
                    employeeEmail, leave.getLeaveType(), newStartDate, newEndDate, actualWorkingDays);
            if (!"VALID".equals(entitlementValidation) && !"VALID_USE_VACATION".equals(entitlementValidation))
                return "Cannot update dates: " + entitlementValidation;

            List<Leave> overlapping = leaveRepository.findOverlappingLeaves(
                            employeeEmail, newStartDate, newEndDate).stream()
                    .filter(l -> !l.getId().equals(leaveId) &&
                            l.getStatus() != LeaveStatus.REJECTED_BY_ACTING_OFFICER &&
                            l.getStatus() != LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER &&
                            l.getStatus() != LeaveStatus.REJECTED_BY_APPROVAL_OFFICER &&
                            !l.isCancelled())
                    .toList();
            if (!overlapping.isEmpty())
                return "New dates overlap with an existing leave request";

            LocalDateTime originalActingApprovedAt      = leave.getActingOfficerApprovedAt();
            LocalDateTime originalSupervisingApprovedAt = leave.getSupervisingOfficerApprovedAt();
            LocalDateTime originalApprovalApprovedAt    = leave.getApprovalOfficerApprovedAt();

            leave.setStartDate(newStartDate);
            leave.setEndDate(newEndDate);
            leave.setWorkingDays(workingDaysBreakdown.get("workingDays"));
            leave.setTotalDays(workingDaysBreakdown.get("totalDays"));
            leave.setWeekendDays(workingDaysBreakdown.get("weekendDays"));
            leave.setPublicHolidays(workingDaysBreakdown.get("publicHolidays"));

            if (originalCreatedAt != null)             leave.setCreatedAt(originalCreatedAt);
            if (originalActingApprovedAt != null)      leave.setActingOfficerApprovedAt(originalActingApprovedAt);
            if (originalSupervisingApprovedAt != null) leave.setSupervisingOfficerApprovedAt(originalSupervisingApprovedAt);
            if (originalApprovalApprovedAt != null)    leave.setApprovalOfficerApprovedAt(originalApprovalApprovedAt);

            leaveRepository.save(leave);

            if (wasApproved) {
                leaveEntitlementService.updateEntitlementOnLeaveApproval(
                        leave.getEmployeeEmail(), leave.getLeaveType(),
                        newStartDate, newEndDate, false, false, actualWorkingDays);
            }

            logger.info("Leave dates updated for {}: {} to {} (reason: {})",
                    employeeEmail, newStartDate, newEndDate, reason);
            return "Leave dates updated successfully";

        } catch (Exception e) {
            logger.error("Error editing leave dates: {}", e.getMessage(), e);
            return "Failed to edit leave dates: " + e.getMessage();
        }
    }

    public Map<String, Object> canEditLeaveDates(String leaveId, String employeeEmail) {
        try {
            Leave leave = leaveRepository.findById(leaveId).orElse(null);
            if (leave == null)
                return Map.of("canEdit", false, "reason", "Leave request not found");

            if (!leave.getEmployeeEmail().equalsIgnoreCase(employeeEmail))
                return Map.of("canEdit", false, "reason", "Not authorized");

            if (leave.isCancelled() || leave.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE)
                return Map.of("canEdit", false, "reason", "Cancelled leave cannot be edited");

            if (leave.getStatus() == LeaveStatus.REJECTED_BY_ACTING_OFFICER ||
                    leave.getStatus() == LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER ||
                    leave.getStatus() == LeaveStatus.REJECTED_BY_APPROVAL_OFFICER)
                return Map.of("canEdit", false, "reason", "Rejected leave cannot be edited");

            LocalDate today = LocalDate.now();
            if (leave.getStartDate().isBefore(today))
                return Map.of("canEdit", false, "reason", "Past leave dates cannot be edited");

            String editMode = leave.isShortLeave() ? "TIME_ONLY"
                    : leave.isHalfDay() ? "DATE_AND_TIME"
                    : "DATE_ONLY";

            return Map.of("canEdit", true, "reason", "", "editMode", editMode);
        } catch (Exception e) {
            logger.error("Error checking if leave dates can be edited: {}", e.getMessage());
            return Map.of("canEdit", false, "reason", "Error checking eligibility");
        }
    }
// ============================================================================
// ADD TO LeaveService.java — editLeaveRequest() method
// Handles: date edit, time edit (SHORT/HALF_DAY), leave type change
// ============================================================================

    // ── Edit Leave Request ────────────────────────────────────────────────────
    public Map<String, Object> editLeaveRequest(String leaveId, String employeeEmail,
                                                Map<String, Object> body) {
        // 1. Find leave
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found."));

        // 2. Ownership
        if (!leave.getEmployeeEmail().equals(employeeEmail)) {
            throw new IllegalArgumentException("You can only edit your own leave requests.");
        }

        // 3. Status check
        if (leave.getStatus() == LeaveStatus.CANCELLED_BY_EMPLOYEE ||
                leave.getStatus() == LeaveStatus.CANCELLED_ADMIN ||
                leave.getStatus() == LeaveStatus.REJECTED_BY_ACTING_OFFICER ||
                leave.getStatus() == LeaveStatus.REJECTED_BY_SUPERVISING_OFFICER ||
                leave.getStatus() == LeaveStatus.REJECTED_BY_APPROVAL_OFFICER) {
            throw new IllegalArgumentException(
                    "Cannot edit cancelled or rejected leave. Status: " + leave.getStatus());
        }

        // 4. Future check
        LocalDate today    = LocalDate.now();
        LocalDate oldStart = leave.getStartDate();
        if (oldStart != null && !oldStart.isAfter(today) && !oldStart.isEqual(today)) {
            throw new IllegalArgumentException("Cannot edit a leave that has already started.");
        }

        // ── Parse inputs ────────────────────────────────────────────────────
        String newLeaveType   = (String) body.getOrDefault("leaveType",   leave.getLeaveType());
        String newStartDateS  = (String) body.get("startDate");
        String newEndDateS    = (String) body.get("endDate");
        String newStartTimeS  = (String) body.get("shortLeaveStartTime");
        String newEndTimeS    = (String) body.get("shortLeaveEndTime");
        String halfDayPeriod  = (String) body.get("halfDayPeriod");   // "MORNING" / "AFTERNOON"
        String halfDayStartTimeS  = (String) body.get("halfDayStartTime");     // ← NEW
        String halfDayEndTimeS    = (String) body.get("halfDayEndTime");       // ← NEW
        String reason         = (String) body.getOrDefault("reason", "");

        LocalDate newStart = newStartDateS != null ? LocalDate.parse(newStartDateS) : oldStart;
        LocalDate newEnd   = newEndDateS   != null ? LocalDate.parse(newEndDateS)   : leave.getEndDate();
        if (newEnd == null) newEnd = newStart;

        if (newStart.isBefore(today)) {
            throw new IllegalArgumentException("Cannot change leave to a past date.");
        }
        if (newEnd.isBefore(newStart)) {
            throw new IllegalArgumentException("End date must be after start date.");
        }

        boolean oldIsHalfDay  = leave.isHalfDay();
        boolean oldIsShort    = leave.isShortLeave();
        boolean newIsHalfDay  = "HALF_DAY".equals(newLeaveType);
        boolean newIsShort    = "SHORT".equals(newLeaveType);
        boolean leaveTypeChanged = !newLeaveType.equals(leave.getLeaveType());

        // ── Calculate working days for non-time types ─────────────────────
        int oldWorkingDays = leave.getWorkingDays();
        int newWorkingDays = oldWorkingDays;

        if (!newIsHalfDay && !newIsShort) {
            newWorkingDays = calculateWorkingDaysBetween(newStart, newEnd);
            if (newWorkingDays == 0) {
                throw new IllegalArgumentException(
                        "Selected date range has no working days (weekends/holidays only).");
            }
        } else if (newIsHalfDay) {
            newWorkingDays = 0; // half day handled via accumulatedHalfDays
        } else {
            newWorkingDays = 0; // short leave = no day deduction
        }

        // ── Revert old entitlement if APPROVED ───────────────────────────
        if (leave.getStatus() == LeaveStatus.APPROVED) {
            if (oldIsHalfDay) {
                leaveEntitlementService.revertHalfDayEntitlement(employeeEmail, oldStart.getYear());
            } else if (!oldIsShort) {
                leaveEntitlementService.revertEntitlementForDays(
                        employeeEmail, leave.getLeaveType(), oldStart.getYear(), oldWorkingDays);
            }
        }

        // ── Apply changes to Leave ───────────────────────────────────────
        // ── Capture OLD time values before we overwrite them ──────────────
        LocalTime oldHalfDayStart = leave.getHalfDayStartTime();
        LocalTime oldHalfDayEnd   = leave.getHalfDayEndTime();
        LocalTime oldShortStart   = leave.getShortLeaveStartTime();
        LocalTime oldShortEnd     = leave.getShortLeaveEndTime();

// ── Build time-change description ──────────────────────────────────
        StringBuilder timeChangeDesc = new StringBuilder();

        if (newIsHalfDay) {
            LocalTime newHDStart = halfDayStartTimeS != null ? LocalTime.parse(halfDayStartTimeS) : oldHalfDayStart;
            LocalTime newHDEnd   = halfDayEndTimeS   != null ? LocalTime.parse(halfDayEndTimeS)   : oldHalfDayEnd;
            boolean timeActuallyChanged =
                    (oldHalfDayStart == null || !oldHalfDayStart.equals(newHDStart)) ||
                            (oldHalfDayEnd   == null || !oldHalfDayEnd.equals(newHDEnd));
            if (timeActuallyChanged && newHDStart != null && newHDEnd != null) {
                timeChangeDesc.append(String.format(", time %s-%s→%s-%s",
                        oldHalfDayStart != null ? oldHalfDayStart : "—",
                        oldHalfDayEnd   != null ? oldHalfDayEnd   : "—",
                        newHDStart, newHDEnd));
            }
        } else if (newIsShort) {
            LocalTime newSStart = newStartTimeS != null ? LocalTime.parse(newStartTimeS) : oldShortStart;
            LocalTime newSEnd    = newEndTimeS   != null ? LocalTime.parse(newEndTimeS)   : oldShortEnd;
            boolean timeActuallyChanged =
                    (oldShortStart == null || !oldShortStart.equals(newSStart)) ||
                            (oldShortEnd   == null || !oldShortEnd.equals(newSEnd));
            if (timeActuallyChanged && newSStart != null && newSEnd != null) {
                timeChangeDesc.append(String.format(", time %s-%s→%s-%s",
                        oldShortStart != null ? oldShortStart : "—",
                        oldShortEnd   != null ? oldShortEnd   : "—",
                        newSStart, newSEnd));
            }
        }

// ── Apply changes to Leave ───────────────────────────────────────
        String changeLog = String.format("[Edited %s: type %s→%s, dates %s→%s→%s→%s%s%s]",
                today,
                leave.getLeaveType(), newLeaveType,
                oldStart, leave.getEndDate(), newStart, newEnd,
                timeChangeDesc,
                reason.isBlank() ? "" : " Reason: " + reason);

        // Half day period
        // Half day period + times
        if (newIsHalfDay) {
            if (halfDayPeriod != null) {
                leave.setHalfDayPeriod(halfDayPeriod);
            }
            if (halfDayStartTimeS != null) {
                leave.setHalfDayStartTime(LocalTime.parse(halfDayStartTimeS));   // ← NEW
            }
            if (halfDayEndTimeS != null) {
                leave.setHalfDayEndTime(LocalTime.parse(halfDayEndTimeS));       // ← NEW
            }
            leave.setShortLeaveStartTime(null);
            leave.setShortLeaveEndTime(null);
        }
        // Short leave times
        // Short leave times
        if (newIsShort) {
            if (newStartTimeS != null) {
                leave.setShortLeaveStartTime(LocalTime.parse(newStartTimeS));
            }
            if (newEndTimeS != null) {
                leave.setShortLeaveEndTime(LocalTime.parse(newEndTimeS));
            }
            leave.setHalfDayPeriod(null);
            leave.setHalfDayStartTime(null);     // ← NEW
            leave.setHalfDayEndTime(null);       // ← NEW
        }


        // If changed to regular leave, clear time fields
        if (!newIsHalfDay && !newIsShort) {
            leave.setHalfDayPeriod(null);
            leave.setHalfDayStartTime(null);     // ← NEW
            leave.setHalfDayEndTime(null);       // ← NEW
            leave.setShortLeaveStartTime(null);
            leave.setShortLeaveEndTime(null);
        }

        // Append edit note to reason
        String existingReason = leave.getReason() != null ? leave.getReason() : "";
        leave.setReason(existingReason + "\n" + changeLog);
        leave.setUpdatedAt(java.time.LocalDateTime.now());

        // ── Deduct new entitlement if APPROVED ──────────────────────────
        if (leave.getStatus() == LeaveStatus.APPROVED) {
            if (newIsHalfDay) {
                leaveEntitlementService.applyHalfDayEntitlement(employeeEmail, newStart.getYear());
            } else if (!newIsShort && newWorkingDays > 0) {
                leaveEntitlementService.applyEntitlementForDays(
                        employeeEmail, newLeaveType, newStart.getYear(), newWorkingDays);
            }
        }

        leaveRepository.save(leave);

        // Update monthly usage
        leaveEntitlementService.updateMonthlyUsageForEmployee(employeeEmail, newStart.getYear());

        logger.info("[LeaveEdit] {} edited leave {}: type={}, dates={}→{}",
                employeeEmail, leaveId, newLeaveType, newStart, newEnd);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success",      true);
        result.put("leaveType",    newLeaveType);
        result.put("startDate",    newStart.toString());
        result.put("endDate",      leave.getEndDate().toString());
        result.put("workingDays",  newWorkingDays);
        result.put("message", String.format("✅ Leave updated: %s, %s → %s",
                newLeaveType, newStart, leave.getEndDate()));
        return result;
    }

    // ── Helper: working days using WorkingDayCalculator ─────────────────────
    private int calculateWorkingDaysBetween(LocalDate start, LocalDate end) {
        Map<String, Integer> breakdown = workingDayCalculator.calculateWorkingDays(start, end);
        return breakdown.getOrDefault("workingDays", 0);
    }
    // ------------------- MATERNITY HELPERS -------------------
    private boolean isContinuationRequest(Leave existingLeave, LeaveRequest newRequest) {
        String existingType = existingLeave.getMaternityLeaveType();
        String newType = newRequest.getMaternityLeaveType();

        if (existingType.equals(newType)) {
            return false;
        }

        if ("FULL_PAY".equals(existingType) && ("HALF_PAY".equals(newType) || "NO_PAY".equals(newType))) {
            return true;
        }

        if ("HALF_PAY".equals(existingType) && "NO_PAY".equals(newType)) {
            return true;
        }

        LocalDate newStartDate = newRequest.getStartDate();
        LocalDate existingStartDate = existingLeave.getStartDate();
        return !newStartDate.isBefore(existingStartDate);
    }

    private String formatMaternityLeaveType(String maternityLeaveType) {
        if (maternityLeaveType == null) return "Full Pay";
        switch (maternityLeaveType.toUpperCase()) {
            case "FULL_PAY": return "Full Pay";
            case "HALF_PAY": return "Half Pay";
            case "NO_PAY":   return "No Pay";
            default:         return maternityLeaveType.replace("_", " ");
        }
    }

    // ------------------- SET MATERNITY LEAVE END DATE -------------------
    public String setMaternityLeaveEndDate(String leaveId, String adminEmail, LocalDate endDate, String adminComments) {
        try {
            Leave leave = leaveRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leave request not found"));

            if (!leave.isMaternityLeave()) {
                return "This is not a maternity leave request";
            }

            if (leave.getStatus() != LeaveStatus.APPROVED) {
                return "Maternity leave must be approved before setting end date";
            }

            if (leave.isMaternityEndDateSet()) {
                return "End date has already been set for this maternity leave";
            }

            if (endDate.isBefore(leave.getStartDate()) || endDate.isEqual(leave.getStartDate())) {
                return "End date must be after the start date";
            }

            LocalDateTime originalCreatedAt = leave.getCreatedAt();
            LocalDateTime originalActingApprovedAt = leave.getActingOfficerApprovedAt();
            LocalDateTime originalSupervisingApprovedAt = leave.getSupervisingOfficerApprovedAt();
            LocalDateTime originalApprovalApprovedAt = leave.getApprovalOfficerApprovedAt();

            leave.setEndDate(endDate);
            leave.setMaternityEndDateSet(true);

            Map<String, Integer> workingDaysBreakdown = workingDayCalculator.calculateWorkingDays(
                    leave.getStartDate(), endDate);
            int actualWorkingDays = workingDaysBreakdown.get("workingDays");

            leave.setWorkingDays(actualWorkingDays);
            leave.setTotalDays(workingDaysBreakdown.get("totalDays"));
            leave.setWeekendDays(workingDaysBreakdown.get("weekendDays"));
            leave.setPublicHolidays(workingDaysBreakdown.get("publicHolidays"));

            StringBuilder additionalDetails = new StringBuilder();
            additionalDetails.append("End date set by admin: ").append(adminEmail);
            additionalDetails.append(" on ")
                    .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a")));
            if (adminComments != null && !adminComments.trim().isEmpty()) {
                additionalDetails.append(" - Comments: ").append(adminComments);
            }
            leave.setMaternityAdditionalDetails(additionalDetails.toString());

            if (originalCreatedAt != null) leave.setCreatedAt(originalCreatedAt);
            if (originalActingApprovedAt != null) leave.setActingOfficerApprovedAt(originalActingApprovedAt);
            if (originalSupervisingApprovedAt != null) leave.setSupervisingOfficerApprovedAt(originalSupervisingApprovedAt);
            if (originalApprovalApprovedAt != null) leave.setApprovalOfficerApprovedAt(originalApprovalApprovedAt);

            leaveRepository.save(leave);

            leaveEntitlementService.updateEntitlementOnLeaveApproval(
                    leave.getEmployeeEmail(),
                    leave.getLeaveType(),
                    leave.getStartDate(),
                    leave.getEndDate(),
                    false,
                    false,
                    actualWorkingDays);

            try {
                notificationService.notifyMaternityLeaveEndDateSet(leave, adminEmail);
                logger.info("Maternity leave end date notification sent to employee: {}", leave.getEmployeeEmail());
            } catch (Exception e) {
                logger.warn("Failed to send maternity leave end date notification: {}", e.getMessage(), e);
            }

            long totalDays = ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1;
            return String.format(
                    "Maternity leave end date set successfully. Duration: %d calendar days (%d working days). Employee has been notified via email.",
                    totalDays, actualWorkingDays);

        } catch (Exception e) {
            logger.error("Error setting maternity leave end date: {}", e.getMessage(), e);
            return "Failed to set maternity leave end date: " + e.getMessage();
        }
    }



    // ------------------- GET MATERNITY LEAVES NEEDING END DATE -------------------
    public List<Leave> getMaternityLeavesNeedingEndDate() {
        return leaveRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(leave -> leave.isMaternityLeave() &&
                        leave.getStatus() == LeaveStatus.APPROVED &&
                        !leave.isMaternityEndDateSet())
                .collect(Collectors.toList());
    }

    // ------------------- VALIDATE MATERNITY LEAVE FOR APPROVAL -------------------
    private String validateMaternityLeaveForApproval(Leave leave) {
        if (leave.getMaternityLeaveType() == null || leave.getMaternityLeaveType().trim().isEmpty()) {
            return "Maternity leave type is not set";
        }

        if (!Arrays.asList("FULL_PAY", "HALF_PAY", "NO_PAY").contains(leave.getMaternityLeaveType())) {
            return "Invalid maternity leave type";
        }

        List<Leave> existingMaternityLeaves = leaveRepository
                .findByEmployeeEmailOrderByCreatedAtDesc(leave.getEmployeeEmail())
                .stream()
                .filter(existingLeave -> "MATERNITY".equals(existingLeave.getLeaveType()) &&
                        !existingLeave.getId().equals(leave.getId()) &&
                        !existingLeave.isCancelled() &&
                        (existingLeave.getStatus() == LeaveStatus.APPROVED ||
                                existingLeave.getStatus() == LeaveStatus.PENDING_ACTING_OFFICER ||
                                existingLeave.getStatus() == LeaveStatus.PENDING_SUPERVISING_OFFICER ||
                                existingLeave.getStatus() == LeaveStatus.PENDING_APPROVAL_OFFICER))
                .collect(Collectors.toList());

        if (!existingMaternityLeaves.isEmpty()) {
            return "Employee already has an active maternity leave request";
        }

        return "VALID";
    }
}