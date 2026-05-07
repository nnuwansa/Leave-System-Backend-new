package com.LeaveDataManagementSystem.LeaveManagement.Controller;

import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
import com.LeaveDataManagementSystem.LeaveManagement.Model.EmergencyLeaveRequest;
import com.LeaveDataManagementSystem.LeaveManagement.Service.EmergencyLeaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/emergency-leave")
@CrossOrigin()
public class EmergencyLeaveController {

    private static final Logger logger = LoggerFactory.getLogger(EmergencyLeaveController.class);

    @Autowired private EmergencyLeaveService emergencyLeaveService;
    @Autowired private JwtUtil               jwtUtil;

    private String getEmail(String authHeader) {
        return jwtUtil.extractEmail(authHeader.replace("Bearer ", ""));
    }

    // ── Admin: create single request with multiple year grants ────────────────
    @PostMapping("/admin/create")
    public ResponseEntity<?> createRequest(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> body) {
        try {
            String adminEmail           = getEmail(token);
            String employeeEmail        = (String) body.get("employeeEmail");
            String reason               = (String) body.get("reason");
            String approvalOfficerEmail = (String) body.get("approvalOfficerEmail");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> yearGrants =
                    (List<Map<String, Object>>) body.get("yearGrants");

            if (employeeEmail == null || reason == null || approvalOfficerEmail == null) {
                return ResponseEntity.badRequest().body("❌ employeeEmail, yearGrants, reason, and approvalOfficerEmail are required");
            }

            Map<String, Object> result = emergencyLeaveService.createRequest(
                    adminEmail, employeeEmail, yearGrants, reason, approvalOfficerEmail);

            return Boolean.TRUE.equals(result.get("success"))
                    ? ResponseEntity.ok(result)
                    : ResponseEntity.badRequest().body(result);

        } catch (Exception e) {
            logger.error("Error creating emergency leave request: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ Error: " + e.getMessage());
        }
    }

    // ── Admin: get all requests ───────────────────────────────────────────────
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllRequests(@RequestHeader("Authorization") String token) {
        try {
            return ResponseEntity.ok(emergencyLeaveService.getAllRequests());
        } catch (Exception e) {
            logger.error("Error fetching all emergency leave requests: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    // ── Admin: check one year remaining ──────────────────────────────────────
    // GET /emergency-leave/admin/check-remaining?email=x@y.com&year=2025
    @GetMapping("/admin/check-remaining")
    public ResponseEntity<?> checkRemaining(
            @RequestHeader("Authorization") String token,
            @RequestParam String email,
            @RequestParam int year) {
        try {
            double remaining = emergencyLeaveService.getPreviousYearRemaining(email, year);
            return ResponseEntity.ok(Map.of(
                    "employeeEmail", email,
                    "year",          year,
                    "remainingDays", remaining,
                    "hasData",       remaining >= 0));
        } catch (Exception e) {
            logger.error("Error checking remaining days: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    // ── Admin: check ALL years remaining ─────────────────────────────────────
        @GetMapping("/admin/check-all-years")
    public ResponseEntity<?> checkAllYearsRemaining(
            @RequestHeader("Authorization") String token,
            @RequestParam String email) {
        try {
            return ResponseEntity.ok(emergencyLeaveService.getAllPreviousYearsRemaining(email));
        } catch (Exception e) {
            logger.error("Error checking all years remaining for {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    // ── Approval Officer: pending ─────────────────────────────────────────────
    // GET /emergency-leave/officer/pending
    @GetMapping("/officer/pending")
    public ResponseEntity<?> getPending(@RequestHeader("Authorization") String token) {
        try {
            String officerEmail = getEmail(token);
            return ResponseEntity.ok(emergencyLeaveService.getPendingForOfficer(officerEmail));
        } catch (Exception e) {
            logger.error("Error fetching pending emergency leaves: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    // ── Approval Officer: approve ─────────────────────────────────────────────
    // POST /emergency-leave/officer/approve/{requestId}
    @PostMapping("/officer/approve/{requestId}")
    public ResponseEntity<?> approve(
            @RequestHeader("Authorization") String token,
            @PathVariable String requestId,
            @RequestBody Map<String, String> body) {
        try {
            String officerEmail = getEmail(token);
            String comments     = body.getOrDefault("comments", "");
            Map<String, Object> result = emergencyLeaveService.approveRequest(requestId, officerEmail, comments);
            return Boolean.TRUE.equals(result.get("success"))
                    ? ResponseEntity.ok(result)
                    : ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            logger.error("Error approving emergency leave {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }

    // ── Approval Officer: reject ──────────────────────────────────────────────
    // POST /emergency-leave/officer/reject/{requestId}
    @PostMapping("/officer/reject/{requestId}")
    public ResponseEntity<?> reject(
            @RequestHeader("Authorization") String token,
            @PathVariable String requestId,
            @RequestBody Map<String, String> body) {
        try {
            String officerEmail = getEmail(token);
            String comments     = body.getOrDefault("comments", "");
            Map<String, Object> result = emergencyLeaveService.rejectRequest(requestId, officerEmail, comments);
            return Boolean.TRUE.equals(result.get("success"))
                    ? ResponseEntity.ok(result)
                    : ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            logger.error("Error rejecting emergency leave {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }


    // GET /emergency-leave/my
    @GetMapping("/my")
    public ResponseEntity<?> myRequests(@RequestHeader("Authorization") String token) {
        try {
            String email = getEmail(token);
            return ResponseEntity.ok(emergencyLeaveService.getByEmployee(email));
        } catch (Exception e) {
            logger.error("Error fetching employee emergency leaves: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ " + e.getMessage());
        }
    }
}