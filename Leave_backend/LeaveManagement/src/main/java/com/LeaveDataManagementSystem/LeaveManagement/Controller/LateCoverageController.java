//package com.LeaveDataManagementSystem.LeaveManagement.Controller;
//
//import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.LateCoverageRecord;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.LeaveEntitlement;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LateCoverageRepository;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
//@RestController
//@RequestMapping("/admin/late-coverage")
//@CrossOrigin()
//public class LateCoverageController {
//
//    private static final Logger logger = LoggerFactory.getLogger(LateCoverageController.class);
//
//    @Autowired private LateCoverageRepository  lateCoverageRepository;
//    @Autowired private LeaveEntitlementRepository leaveEntitlementRepository;
//    @Autowired private JwtUtil                 jwtUtil;
//
//    // ── GET all records ───────────────────────────────────────────────────────
//    @GetMapping
//    public ResponseEntity<?> getAll() {
//        try {
//            List<LateCoverageRecord> records = lateCoverageRepository.findAllByOrderByCreatedAtDesc();
//            return ResponseEntity.ok(records);
//        } catch (Exception e) {
//            logger.error("Error fetching late coverage records: {}", e.getMessage());
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── POST save record + auto-deduct CASUAL ─────────────────────────────────
//    @PostMapping
//    public ResponseEntity<?> save(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, Object> body) {
//        try {
//            // ── Build record ────────────────────────────────────────────────
//            LateCoverageRecord rec = new LateCoverageRecord();
//            rec.setEmployeeEmail((String) body.get("employeeEmail"));
//            rec.setEmployeeName((String) body.get("employeeName"));
//            rec.setMonth(Integer.parseInt(body.get("month").toString()));
//            rec.setYear(Integer.parseInt(body.get("year").toString()));
//            rec.setAdminNote(body.getOrDefault("adminNote", "").toString());
//            rec.setCreatedAt(LocalDateTime.now());
//
//            // Parse uncoveredDates
//            @SuppressWarnings("unchecked")
//            List<String> dates = (List<String>) body.get("uncoveredDates");
//            rec.setUncoveredDates(dates != null ? dates : new ArrayList<>());
//
//            // Calculate
//            int    count      = rec.getUncoveredDates().size();
//            int    halfDays   = count / 3;
//            int    remainder  = count % 3;
//            double casualDays = halfDays * 0.5;
//
//            rec.setUncoveredCount(count);
//            rec.setHalfDaysDeducted(halfDays);
//            rec.setCasualDaysDeducted(casualDays);
//            rec.setRemainder(remainder);
//
//            // Save to MongoDB
//            lateCoverageRepository.save(rec);
//            logger.info("Late coverage saved: {} — {} dates, {} halfDays, {} casualDays",
//                    rec.getEmployeeEmail(), count, halfDays, casualDays);
//
//            // Auto-deduct CASUAL if needed
//            String deductMsg = "No deduction (need at least 3 uncovered dates).";
//            if (halfDays > 0) {
//                deductMsg = deductCasualLeave(
//                        rec.getEmployeeEmail(), rec.getYear(), halfDays, casualDays);
//            }
//
//            Map<String, Object> response = new LinkedHashMap<>();
//            response.put("success",          true);
//            response.put("id",               rec.getId());
//            response.put("uncoveredCount",   count);
//            response.put("halfDaysDeducted", halfDays);
//            response.put("casualDeducted",   casualDays);
//            response.put("remainder",        remainder);
//            response.put("deductMessage",    deductMsg);
//            response.put("message",
//                    count > 0
//                            ? String.format(" Saved. %d uncovered date(s) recorded. %s", count, deductMsg)
//                            : " Record saved.");
//
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            logger.error("Error saving late coverage: {}", e.getMessage(), e);
//            return ResponseEntity.status(500)
//                    .body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── DELETE record ─────────────────────────────────────────────────────────
//    @DeleteMapping("/{id}")
//    public ResponseEntity<?> delete(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String id) {
//        try {
//            if (!lateCoverageRepository.existsById(id)) {
//                return ResponseEntity.status(404)
//                        .body(Map.of("success", false, "message", "Record not found"));
//            }
//            lateCoverageRepository.deleteById(id);
//            return ResponseEntity.ok(Map.of("success", true, "message", "Record deleted"));
//        } catch (Exception e) {
//            logger.error("Error deleting record {}: {}", id, e.getMessage());
//            return ResponseEntity.status(500)
//                    .body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── POST apply-deductions (manual trigger if auto-deduct failed) ──────────
//    @PostMapping("/apply-deductions")
//    public ResponseEntity<?> applyDeductions(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, Object> body) {
//        try {
//            String email      = (String) body.get("employeeEmail");
//            int    halfDays   = Integer.parseInt(body.get("halfDaysToDeduct").toString());
//            int    year       = Integer.parseInt(body.get("year").toString());
//            double casualDays = halfDays * 0.5;
//
//            String result = deductCasualLeave(email, year, halfDays, casualDays);
//            return ResponseEntity.ok(Map.of("success", true, "message", result));
//        } catch (Exception e) {
//            logger.error("Error applying deductions: {}", e.getMessage(), e);
//            return ResponseEntity.status(500)
//                    .body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── PRIVATE: deduct days from CASUAL entitlement ─────────────────────────
//    private String deductCasualLeave(String email, int year, int halfDays, double casualDays) {
//        try {
//            Optional<LeaveEntitlement> entOpt = leaveEntitlementRepository
//                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
//
//            if (entOpt.isEmpty()) {
//                logger.warn("No CASUAL entitlement for {} year {}", email, year);
//                return String.format(
//                        "⚠️ No CASUAL entitlement found for %s in %d. Please deduct %.1f day(s) manually.",
//                        email, year, casualDays);
//            }
//
//            LeaveEntitlement ent = entOpt.get();
//            double remaining = ent.getRemainingDays();
//            double used      = ent.getUsedDays();
//
//            // Cap deduction at available balance
//            double actualDeduct = Math.min(casualDays, remaining);
//
//            ent.setUsedDays(used + actualDeduct);
//            ent.setRemainingDays(Math.max(0, remaining - actualDeduct));
//            leaveEntitlementRepository.save(ent);
//
//            logger.info("CASUAL deducted: {} from {} year {}. remaining: {}→{}",
//                    actualDeduct, email, year, remaining, ent.getRemainingDays());
//
//            if (actualDeduct < casualDays) {
//                return String.format(
//                        "⚠️ Partial deduction: only %.1f of %.1f day(s) deducted (insufficient balance). Remaining CASUAL: %.1f days.",
//                        actualDeduct, casualDays, ent.getRemainingDays());
//            }
//            return String.format(
//                    "✅ %.1f day(s) deducted from CASUAL leave. Remaining CASUAL: %.1f days.",
//                    actualDeduct, ent.getRemainingDays());
//
//        } catch (Exception e) {
//            logger.error("Error deducting CASUAL for {}: {}", email, e.getMessage(), e);
//            return "❌ Deduction failed: " + e.getMessage();
//        }
//    }
//}


//
//
//package com.LeaveDataManagementSystem.LeaveManagement.Controller;
//
//import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.LateCoverageRecord;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.LeaveEntitlement;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LateCoverageRepository;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
//@RestController
//@RequestMapping("/admin/late-coverage")
//@CrossOrigin()
//public class LateCoverageController {
//
//    private static final Logger logger = LoggerFactory.getLogger(LateCoverageController.class);
//
//    @Autowired private LateCoverageRepository     lateCoverageRepository;
//    @Autowired private LeaveEntitlementRepository leaveEntitlementRepository;
//    @Autowired private JwtUtil                    jwtUtil;
//
//    // ── GET all records ───────────────────────────────────────────────────────
//    @GetMapping
//    public ResponseEntity<?> getAll() {
//        try {
//            return ResponseEntity.ok(lateCoverageRepository.findAllByOrderByCreatedAtDesc());
//        } catch (Exception e) {
//            logger.error("Error fetching late coverage: {}", e.getMessage());
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── POST save + auto deduct CASUAL ────────────────────────────────────────
//    @PostMapping
//    public ResponseEntity<?> save(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, Object> body) {
//        try {
//            LateCoverageRecord rec = new LateCoverageRecord();
//            rec.setEmployeeEmail((String) body.get("employeeEmail"));
//            rec.setEmployeeName((String)  body.get("employeeName"));
//            rec.setMonth(Integer.parseInt(body.get("month").toString()));
//            rec.setYear(Integer.parseInt( body.get("year").toString()));
//            rec.setAdminNote(body.getOrDefault("adminNote", "").toString());
//            rec.setCreatedAt(LocalDateTime.now());
//
//            @SuppressWarnings("unchecked")
//            List<String> dates = (List<String>) body.get("uncoveredDates");
//            rec.setUncoveredDates(dates != null ? dates : new ArrayList<>());
//
//            int    count      = rec.getUncoveredDates().size();
//            int    halfDays   = count / 3;             // e.g. 6 → 2
//            int    remainder  = count % 3;
//            double casualDays = halfDays * 0.5;        // e.g. 2 half days = 1.0 day
//
//            rec.setUncoveredCount(count);
//            rec.setHalfDaysDeducted(halfDays);
//            rec.setCasualDaysDeducted(casualDays);
//            rec.setRemainder(remainder);
//
//            lateCoverageRepository.save(rec);
//            logger.info("Late coverage saved: {} — {}dates, {}halfDays(={}casualDays)",
//                    rec.getEmployeeEmail(), count, halfDays, casualDays);
//
//            // Auto-deduct CASUAL
//            String deductMsg = "No deduction needed (need at least 3 uncovered dates).";
//            if (halfDays > 0) {
//                deductMsg = deductCasualLeave(rec.getEmployeeEmail(), rec.getYear(), casualDays);
//            }
//
//            Map<String, Object> resp = new LinkedHashMap<>();
//            resp.put("success",          true);
//            resp.put("id",               rec.getId());
//            resp.put("uncoveredCount",   count);
//            resp.put("halfDaysDeducted", halfDays);
//            resp.put("casualDeducted",   casualDays);
//            resp.put("remainder",        remainder);
//            resp.put("deductMessage",    deductMsg);
//            resp.put("message",
//                    halfDays > 0
//                            ? String.format("✅ Saved. %d uncovered date(s). %d×0.5=%.1fd deducted from CASUAL.", count, halfDays, casualDays)
//                            : String.format("✅ Saved. %d uncovered date(s). Need %d more for deduction.", count, 3-count));
//            return ResponseEntity.ok(resp);
//
//        } catch (Exception e) {
//            logger.error("Error saving late coverage: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── DELETE record + RESTORE CASUAL ───────────────────────────────────────
//    @DeleteMapping("/{id}")
//    public ResponseEntity<?> delete(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String id) {
//        try {
//            Optional<LateCoverageRecord> opt = lateCoverageRepository.findById(id);
//            if (opt.isEmpty()) {
//                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Record not found"));
//            }
//
//            LateCoverageRecord rec = opt.get();
//            lateCoverageRepository.deleteById(id);
//
//            // Restore CASUAL leave that was deducted
//            String restoreMsg = "No leave to restore.";
//            if (rec.getCasualDaysDeducted() > 0) {
//                restoreMsg = restoreCasualLeave(rec.getEmployeeEmail(), rec.getYear(), rec.getCasualDaysDeducted());
//            }
//
//            logger.info("Late coverage deleted: {} — restored {}d", id, rec.getCasualDaysDeducted());
//            return ResponseEntity.ok(Map.of(
//                    "success",      true,
//                    "message",      "Record deleted. " + restoreMsg,
//                    "restored",     rec.getCasualDaysDeducted() > 0,
//                    "restoredDays", rec.getCasualDaysDeducted()
//            ));
//        } catch (Exception e) {
//            logger.error("Error deleting {}: {}", id, e.getMessage());
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── POST apply-deductions manually ────────────────────────────────────────
//    @PostMapping("/apply-deductions")
//    public ResponseEntity<?> applyDeductions(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, Object> body) {
//        try {
//            String email      = (String) body.get("employeeEmail");
//            int    halfDays   = Integer.parseInt(body.get("halfDaysToDeduct").toString());
//            int    year       = Integer.parseInt(body.get("year").toString());
//            double casualDays = halfDays * 0.5;
//
//            String result = deductCasualLeave(email, year, casualDays);
//            return ResponseEntity.ok(Map.of("success", true, "message", result));
//        } catch (Exception e) {
//            logger.error("Error applying deductions: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── PRIVATE: deduct casualDays from CASUAL entitlement ───────────────────
//    // Direct usedDays/remainingDays update — no addHalfDay() needed
//    private String deductCasualLeave(String email, int year, double casualDays) {
//        try {
//            Optional<LeaveEntitlement> entOpt = leaveEntitlementRepository
//                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
//
//            if (entOpt.isEmpty()) {
//                logger.warn("No CASUAL entitlement for {} year {}", email, year);
//                return String.format("⚠️ No CASUAL entitlement for %s year %d. Deduct %.1f day(s) manually.", email, year, casualDays);
//            }
//
//            LeaveEntitlement ent     = entOpt.get();
//            double           before  = ent.getRemainingDays();
//            double           used    = ent.getUsedDays();
//            double           actual  = Math.min(casualDays, Math.max(0, before));
//
//            // Direct update: usedDays += actual, remainingDays -= actual
//            ent.setUsedDays(used + actual);
//            ent.setRemainingDays(Math.max(0, before - actual));
//            leaveEntitlementRepository.save(ent);
//
//            logger.info("CASUAL deducted: -{} from {} year {}. Remaining: {}→{}",
//                    actual, email, year, before, ent.getRemainingDays());
//
//            if (actual < casualDays) {
//                return String.format("⚠️ Partial: only %.1f of %.1f day(s) deducted (low balance). Remaining CASUAL: %.1f",
//                        actual, casualDays, ent.getRemainingDays());
//            }
//            return String.format("✅ %.1f day(s) deducted from CASUAL. Remaining CASUAL: %.1f days.",
//                    actual, ent.getRemainingDays());
//
//        } catch (Exception e) {
//            logger.error("Error deducting CASUAL for {}: {}", email, e.getMessage(), e);
//            return "❌ Deduction failed: " + e.getMessage();
//        }
//    }
//
//    // ── PRIVATE: restore CASUAL when record deleted ───────────────────────────
//    private String restoreCasualLeave(String email, int year, double casualDays) {
//        try {
//            Optional<LeaveEntitlement> entOpt = leaveEntitlementRepository
//                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
//
//            if (entOpt.isEmpty()) {
//                logger.warn("No CASUAL entitlement to restore for {} year {}", email, year);
//                return "⚠️ No CASUAL entitlement found to restore.";
//            }
//
//            LeaveEntitlement ent    = entOpt.get();
//            double           before = ent.getRemainingDays();
//            double           used   = ent.getUsedDays();
//            int              total  = ent.getTotalEntitlement();
//
//            // Restore: usedDays -= casualDays, remainingDays += casualDays (capped at total)
//            double newUsed      = Math.max(0, used - casualDays);
//            double newRemaining = Math.min(total, before + casualDays);
//
//            ent.setUsedDays(newUsed);
//            ent.setRemainingDays(newRemaining);
//            leaveEntitlementRepository.save(ent);
//
//            logger.info("CASUAL restored: +{} for {} year {}. Remaining: {}→{}",
//                    casualDays, email, year, before, newRemaining);
//
//            return String.format("✅ %.1f day(s) restored to CASUAL. Remaining CASUAL: %.1f days.",
//                    casualDays, newRemaining);
//
//        } catch (Exception e) {
//            logger.error("Error restoring CASUAL for {}: {}", email, e.getMessage(), e);
//            return "❌ Restore failed: " + e.getMessage();
//        }
//    }
//}




//package com.LeaveDataManagementSystem.LeaveManagement.Controller;
//
//import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.LateCoverageRecord;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.LeaveEntitlement;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LateCoverageRepository;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.data.mongodb.core.query.Update;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
//@RestController
//@RequestMapping("/admin/late-coverage")
//@CrossOrigin()
//public class LateCoverageController {
//
//    private static final Logger logger = LoggerFactory.getLogger(LateCoverageController.class);
//
//    @Autowired private LateCoverageRepository     lateCoverageRepository;
//    @Autowired private LeaveEntitlementRepository leaveEntitlementRepository;
//    @Autowired private MongoTemplate              mongoTemplate;
//    @Autowired private JwtUtil                    jwtUtil;
//
//    // ── GET all records ───────────────────────────────────────────────────────
//    @GetMapping
//    public ResponseEntity<?> getAll() {
//        try {
//            return ResponseEntity.ok(lateCoverageRepository.findAllByOrderByCreatedAtDesc());
//        } catch (Exception e) {
//            logger.error("Error fetching late coverage: {}", e.getMessage());
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── POST save + auto deduct CASUAL ────────────────────────────────────────
//    @PostMapping
//    public ResponseEntity<?> save(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, Object> body) {
//        try {
//            LateCoverageRecord rec = new LateCoverageRecord();
//            rec.setEmployeeEmail((String) body.get("employeeEmail"));
//            rec.setEmployeeName((String)  body.get("employeeName"));
//            rec.setMonth(Integer.parseInt(body.get("month").toString()));
//            rec.setYear(Integer.parseInt( body.get("year").toString()));
//            rec.setAdminNote(body.getOrDefault("adminNote", "").toString());
//            rec.setCreatedAt(LocalDateTime.now());
//
//            @SuppressWarnings("unchecked")
//            List<String> dates = (List<String>) body.get("uncoveredDates");
//            rec.setUncoveredDates(dates != null ? dates : new ArrayList<>());
//
//            // ── RULE: 3 uncovered dates = 1 half day = 0.5 CASUAL day ──────
//            // uncoveredCount=3 → halfDays=1 → casualDays=0.5 (NOT 1.0)
//            int    count      = rec.getUncoveredDates().size();
//            int    halfDays   = count / 3;           // integer division: 3→1, 6→2
//            int    remainder  = count % 3;
//            double casualDays = halfDays * 0.5;      // ALWAYS: half-days × 0.5
//
//            rec.setUncoveredCount(count);
//            rec.setHalfDaysDeducted(halfDays);
//            rec.setCasualDaysDeducted(casualDays);   // stored as 0.5, 1.0, 1.5...
//            rec.setRemainder(remainder);
//
//            lateCoverageRepository.save(rec);
//
//            logger.info("[LateCoverage] Saved: email={} count={} halfDays={} casualDays={}",
//                    rec.getEmployeeEmail(), count, halfDays, casualDays);
//
//            // Auto-deduct only if there is something to deduct
//            String deductMsg = String.format(
//                    "No deduction yet (%d/%d dates — need %d more).", count, 3, 3 - count);
//
//            if (halfDays > 0) {
//                // casualDays = 0.5 per half-day — pass this exact value
//                deductMsg = deductFromCasual(rec.getEmployeeEmail(), rec.getYear(), casualDays);
//            }
//
//            Map<String, Object> resp = new LinkedHashMap<>();
//            resp.put("success",          true);
//            resp.put("id",               rec.getId());
//            resp.put("uncoveredCount",   count);
//            resp.put("halfDaysDeducted", halfDays);
//            resp.put("casualDeducted",   casualDays);
//            resp.put("remainder",        remainder);
//            resp.put("deductMessage",    deductMsg);
//            resp.put("message",
//                    halfDays > 0
//                            ? String.format("✅ %d dates ÷ 3 = %d half-day(s) → −%.1f day(s) from CASUAL.", count, halfDays, casualDays)
//                            : String.format("✅ Recorded %d date(s). Need %d more for deduction.", count, 3 - count));
//
//            return ResponseEntity.ok(resp);
//
//        } catch (Exception e) {
//            logger.error("Error saving late coverage: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── DELETE record + RESTORE CASUAL ───────────────────────────────────────
//    @DeleteMapping("/{id}")
//    public ResponseEntity<?> delete(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String id) {
//        try {
//            Optional<LateCoverageRecord> opt = lateCoverageRepository.findById(id);
//            if (opt.isEmpty()) {
//                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Record not found"));
//            }
//
//            LateCoverageRecord rec = opt.get();
//            double toRestore = rec.getCasualDaysDeducted(); // e.g. 0.5
//            lateCoverageRepository.deleteById(id);
//
//            String restoreMsg = "No leave to restore.";
//            if (toRestore > 0) {
//                restoreMsg = restoreToCasual(rec.getEmployeeEmail(), rec.getYear(), toRestore);
//            }
//
//            logger.info("[LateCoverage] Deleted id={} restored={}d for {}", id, toRestore, rec.getEmployeeEmail());
//            return ResponseEntity.ok(Map.of(
//                    "success",      true,
//                    "message",      "Deleted. " + restoreMsg,
//                    "restoredDays", toRestore
//            ));
//        } catch (Exception e) {
//            logger.error("Error deleting {}: {}", id, e.getMessage());
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ── POST apply-deductions manually ────────────────────────────────────────
//    @PostMapping("/apply-deductions")
//    public ResponseEntity<?> applyDeductions(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, Object> body) {
//        try {
//            String email    = (String) body.get("employeeEmail");
//            int    halfDays = Integer.parseInt(body.get("halfDaysToDeduct").toString());
//            int    year     = Integer.parseInt(body.get("year").toString());
//            double casual   = halfDays * 0.5;  // always × 0.5
//
//            String result = deductFromCasual(email, year, casual);
//            return ResponseEntity.ok(Map.of("success", true, "message", result));
//        } catch (Exception e) {
//            logger.error("Error applying deductions: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    // PRIVATE HELPER: Deduct casualDays from CASUAL entitlement
//    // Uses MongoTemplate $inc to bypass Java setter side-effects
//    // casualDays is already the final value (e.g. 0.5, 1.0) — no extra × 0.5 here
//    // ══════════════════════════════════════════════════════════════════════════
//    private String deductFromCasual(String email, int year, double casualDays) {
//        try {
//            Optional<LeaveEntitlement> entOpt = leaveEntitlementRepository
//                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
//
//            if (entOpt.isEmpty()) {
//                logger.warn("[LateCoverage] No CASUAL entitlement for {} year {}", email, year);
//                return String.format("⚠️ No CASUAL entitlement for %s yr %d. Deduct %.1f day(s) manually.",
//                        email, year, casualDays);
//            }
//
//            LeaveEntitlement ent = entOpt.get();
//            double remaining     = ent.getRemainingDays();
//            double used          = ent.getUsedDays();
//
//            // Cap at available balance
//            double actual = Math.min(casualDays, Math.max(0.0, remaining));
//
//            if (actual <= 0) {
//                return String.format("⚠️ No CASUAL balance left for %s. Cannot deduct %.1f day(s).", email, casualDays);
//            }
//
//            // Use MongoTemplate atomic update to avoid Java setter side-effects
//            // $inc usedDays by +actual, remainingDays by -actual
//            Query q = new Query(
//                    Criteria.where("employeeEmail").is(email)
//                            .and("leaveType").is("CASUAL")
//                            .and("year").is(year)
//            );
//            Update u = new Update()
//                    .inc("usedDays",       actual)
//                    .inc("remainingDays", -actual);
//
//            mongoTemplate.updateFirst(q, u, LeaveEntitlement.class);
//
//            logger.info("[LateCoverage] CASUAL deducted: −{} for {} yr{}. was={} remaining, now={}",
//                    actual, email, year, remaining, remaining - actual);
//
//            return String.format("✅ −%.1f day(s) deducted from CASUAL. Remaining: %.1f days.",
//                    actual, remaining - actual);
//
//        } catch (Exception e) {
//            logger.error("[LateCoverage] Deduct failed for {}: {}", email, e.getMessage(), e);
//            return "❌ Deduction failed: " + e.getMessage();
//        }
//    }
//
//    // ══════════════════════════════════════════════════════════════════════════
//    // PRIVATE HELPER: Restore casualDays to CASUAL entitlement on delete
//    // Uses MongoTemplate $inc to bypass Java setter side-effects
//    // ══════════════════════════════════════════════════════════════════════════
//    private String restoreToCasual(String email, int year, double casualDays) {
//        try {
//            Optional<LeaveEntitlement> entOpt = leaveEntitlementRepository
//                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
//
//            if (entOpt.isEmpty()) {
//                return "⚠️ No CASUAL entitlement found to restore.";
//            }
//
//            LeaveEntitlement ent   = entOpt.get();
//            double remaining       = ent.getRemainingDays();
//            double used            = ent.getUsedDays();
//            int    total           = ent.getTotalEntitlement();
//
//            // Don't restore more than what was used, don't exceed total
//            double actualRestore   = Math.min(casualDays, Math.max(0.0, used));
//            double newRemaining    = Math.min(total, remaining + actualRestore);
//            double restoreUsed     = remaining + actualRestore > total
//                    ? used - (total - remaining)
//                    : actualRestore;
//
//            Query q = new Query(
//                    Criteria.where("employeeEmail").is(email)
//                            .and("leaveType").is("CASUAL")
//                            .and("year").is(year)
//            );
//            Update u = new Update()
//                    .inc("usedDays",      -actualRestore)
//                    .inc("remainingDays",  actualRestore);
//
//            mongoTemplate.updateFirst(q, u, LeaveEntitlement.class);
//
//            logger.info("[LateCoverage] CASUAL restored: +{} for {} yr{}. was={} remaining, now={}",
//                    actualRestore, email, year, remaining, remaining + actualRestore);
//
//            return String.format("✅ +%.1f day(s) restored to CASUAL. Remaining: %.1f days.",
//                    actualRestore, remaining + actualRestore);
//
//        } catch (Exception e) {
//            logger.error("[LateCoverage] Restore failed for {}: {}", email, e.getMessage(), e);
//            return "❌ Restore failed: " + e.getMessage();
//        }
//    }
//}

//package com.LeaveDataManagementSystem.LeaveManagement.Controller;
//
//import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.LateCoverageRecord;
//import com.LeaveDataManagementSystem.LeaveManagement.Model.LeaveEntitlement;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LateCoverageRepository;
//import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
//@RestController
//@RequestMapping("/admin/late-coverage")
//@CrossOrigin()
//public class LateCoverageController {
//
//    private static final Logger logger = LoggerFactory.getLogger(LateCoverageController.class);
//
//    @Autowired private LateCoverageRepository     lateCoverageRepository;
//    @Autowired private LeaveEntitlementRepository leaveEntitlementRepository;
//    @Autowired private JwtUtil                    jwtUtil;
//
//    @GetMapping
//    public ResponseEntity<?> getAll() {
//        try {
//            return ResponseEntity.ok(lateCoverageRepository.findAllByOrderByCreatedAtDesc());
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
//        }
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // POST /admin/late-coverage
//    // RULE: 3 uncovered dates = 1 half day = 0.5 CASUAL day ONLY
//    // ─────────────────────────────────────────────────────────────────────────
//    @PostMapping
//    public ResponseEntity<?> save(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, Object> body) {
//        try {
//            LateCoverageRecord rec = new LateCoverageRecord();
//            rec.setEmployeeEmail((String) body.get("employeeEmail"));
//            rec.setEmployeeName((String)  body.get("employeeName"));
//            rec.setMonth(Integer.parseInt(body.get("month").toString()));
//            rec.setYear(Integer.parseInt( body.get("year").toString()));
//            rec.setAdminNote(body.getOrDefault("adminNote", "").toString());
//            rec.setCreatedAt(LocalDateTime.now());
//
//            @SuppressWarnings("unchecked")
//            List<String> dates = (List<String>) body.get("uncoveredDates");
//            rec.setUncoveredDates(dates != null ? dates : new ArrayList<>());
//
//            int    count      = rec.getUncoveredDates().size();
//            int    halfDays   = count / 3;
//            int    remainder  = count % 3;
//            // CRITICAL: casualDays = halfDays × 0.5  (3 dates → 0.5, NOT 1.0)
//            double casualDays = halfDays * 0.5;
//
//            rec.setUncoveredCount(count);
//            rec.setHalfDaysDeducted(halfDays);
//            rec.setCasualDaysDeducted(casualDays);
//            rec.setRemainder(remainder);
//            lateCoverageRepository.save(rec);
//
//            logger.info("[LateCoverage] count={} halfDays={} casualDays={}", count, halfDays, casualDays);
//
//            String deductMsg = "No deduction needed.";
//            if (halfDays > 0) {
//                deductMsg = deductCasualDirect(rec.getEmployeeEmail(), rec.getYear(), casualDays);
//            }
//
//            return ResponseEntity.ok(Map.of(
//                    "success", true, "id", rec.getId(),
//                    "uncoveredCount", count, "halfDaysDeducted", halfDays,
//                    "casualDeducted", casualDays, "remainder", remainder,
//                    "deductMessage", deductMsg
//            ));
//        } catch (Exception e) {
//            logger.error("[LateCoverage] save error: {}", e.getMessage(), e);
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<?> delete(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String id) {
//        try {
//            Optional<LateCoverageRecord> opt = lateCoverageRepository.findById(id);
//            if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Not found"));
//
//            LateCoverageRecord rec = opt.get();
//            double toRestore = rec.getCasualDaysDeducted();
//            lateCoverageRepository.deleteById(id);
//
//            String msg = "Nothing to restore.";
//            if (toRestore > 0) msg = restoreCasualDirect(rec.getEmployeeEmail(), rec.getYear(), toRestore);
//
//            return ResponseEntity.ok(Map.of("success", true, "message", "Deleted. " + msg, "restoredDays", toRestore));
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    @PostMapping("/apply-deductions")
//    public ResponseEntity<?> applyDeductions(
//            @RequestHeader("Authorization") String token,
//            @RequestBody Map<String, Object> body) {
//        try {
//            String email  = (String) body.get("employeeEmail");
//            int    hd     = Integer.parseInt(body.get("halfDaysToDeduct").toString());
//            int    year   = Integer.parseInt(body.get("year").toString());
//            double casual = hd * 0.5;
//            return ResponseEntity.ok(Map.of("success", true, "message", deductCasualDirect(email, year, casual)));
//        } catch (Exception e) {
//            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
//        }
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // deductCasualDirect — NO MongoTemplate, NO addHalfDay()
//    // Reads entitlement, calculates new values, saves with direct field set
//    // casualDays is the EXACT amount (e.g. 0.5 for 3 dates, 1.0 for 6 dates)
//    // ─────────────────────────────────────────────────────────────────────────
//    private String deductCasualDirect(String email, int year, double casualDays) {
//        try {
//            Optional<LeaveEntitlement> opt = leaveEntitlementRepository
//                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
//
//            if (opt.isEmpty()) {
//                return String.format("⚠️ No CASUAL entitlement for %s yr%d. Deduct %.1fd manually.", email, year, casualDays);
//            }
//
//            LeaveEntitlement ent = opt.get();
//
//            // Read current values before any change
//            double currentUsed      = ent.getUsedDays();
//            double currentRemaining = ent.getRemainingDays();
//            int    total            = ent.getTotalEntitlement();
//
//            // Cap deduction at available balance
//            double toDeduct = Math.min(casualDays, Math.max(0.0, currentRemaining));
//            if (toDeduct <= 0) {
//                return String.format("⚠️ No CASUAL balance for %s.", email);
//            }
//
//            // Calculate new values
//            double newUsed      = currentUsed      + toDeduct;
//            double newRemaining = currentRemaining - toDeduct;
//
//            logger.info("[LateCoverage] DEDUCT: {} CASUAL yr{}: used {}→{}, remaining {}→{} (toDeduct={})",
//                    email, year, currentUsed, newUsed, currentRemaining, newRemaining, toDeduct);
//
//            // setUsedDays no longer auto-recalculates remainingDays (fixed in model)
//            // Set both fields explicitly
//            ent.setUsedDays(newUsed);
//            ent.setRemainingDays(newRemaining);
//            leaveEntitlementRepository.save(ent);
//
//            // Verify the saved value
//            Optional<LeaveEntitlement> verify = leaveEntitlementRepository
//                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
//            if (verify.isPresent()) {
//                logger.info("[LateCoverage] VERIFY after save: used={} remaining={}",
//                        verify.get().getUsedDays(), verify.get().getRemainingDays());
//            }
//
//            return String.format("✅ −%.1f day from CASUAL. Remaining: %.1f days.", toDeduct, newRemaining);
//
//        } catch (Exception e) {
//            logger.error("[LateCoverage] deductCasualDirect error: {}", e.getMessage(), e);
//            return "❌ " + e.getMessage();
//        }
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // restoreCasualDirect — reverses deduction on record delete
//    // ─────────────────────────────────────────────────────────────────────────
//    private String restoreCasualDirect(String email, int year, double casualDays) {
//        try {
//            Optional<LeaveEntitlement> opt = leaveEntitlementRepository
//                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
//
//            if (opt.isEmpty()) return "⚠️ No CASUAL entitlement to restore.";
//
//            LeaveEntitlement ent = opt.get();
//            double currentUsed      = ent.getUsedDays();
//            double currentRemaining = ent.getRemainingDays();
//            int    total            = ent.getTotalEntitlement();
//
//            double toRestore    = Math.min(casualDays, Math.max(0.0, currentUsed));
//            double newUsed      = currentUsed      - toRestore;
//            double newRemaining = Math.min(total, currentRemaining + toRestore);
//
//            logger.info("[LateCoverage] RESTORE: {} CASUAL yr{}: used {}→{}, remaining {}→{}",
//                    email, year, currentUsed, newUsed, currentRemaining, newRemaining);
//
//            ent.setUsedDays(newUsed);
//            ent.setRemainingDays(newRemaining);
//            leaveEntitlementRepository.save(ent);
//
//            return String.format("✅ +%.1f day restored to CASUAL. Remaining: %.1f days.", toRestore, newRemaining);
//
//        } catch (Exception e) {
//            logger.error("[LateCoverage] restoreCasualDirect error: {}", e.getMessage(), e);
//            return "❌ " + e.getMessage();
//        }
//    }
//}


package com.LeaveDataManagementSystem.LeaveManagement.Controller;

import com.LeaveDataManagementSystem.LeaveManagement.Config.JwtUtil;
import com.LeaveDataManagementSystem.LeaveManagement.Model.LateCoverageRecord;
import com.LeaveDataManagementSystem.LeaveManagement.Model.LeaveEntitlement;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LateCoverageRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin/late-coverage")
@CrossOrigin()
public class LateCoverageController {

    private static final Logger logger = LoggerFactory.getLogger(LateCoverageController.class);

    @Autowired private LateCoverageRepository     lateCoverageRepository;
    @Autowired private LeaveEntitlementRepository leaveEntitlementRepository;
    @Autowired private JwtUtil                    jwtUtil;

    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(lateCoverageRepository.findAllByOrderByCreatedAtDesc());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> save(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> body) {
        try {
            LateCoverageRecord rec = new LateCoverageRecord();
            rec.setEmployeeEmail((String) body.get("employeeEmail"));
            rec.setEmployeeName((String)  body.get("employeeName"));
            rec.setMonth(Integer.parseInt(body.get("month").toString()));
            rec.setYear(Integer.parseInt( body.get("year").toString()));
            rec.setAdminNote(body.getOrDefault("adminNote", "").toString());
            rec.setCreatedAt(LocalDateTime.now());

            @SuppressWarnings("unchecked")
            List<String> dates = (List<String>) body.get("uncoveredDates");
            rec.setUncoveredDates(dates != null ? dates : new ArrayList<>());

            int    count      = rec.getUncoveredDates().size();
            int    halfDays   = count / 3;
            int    remainder  = count % 3;
            double casualDays = halfDays * 0.5;

            rec.setUncoveredCount(count);
            rec.setHalfDaysDeducted(halfDays);
            rec.setCasualDaysDeducted(casualDays);
            rec.setRemainder(remainder);
            lateCoverageRepository.save(rec);

            logger.info("╔══ LATE COVERAGE SAVE ══════════════════════════════");
            logger.info("║ email        = {}", rec.getEmployeeEmail());
            logger.info("║ dates count  = {}", count);
            logger.info("║ halfDays     = {} (= {} ÷ 3)", halfDays, count);
            logger.info("║ casualDays   = {} (= {} × 0.5)  ← should be 0.5 for 3 dates", casualDays, halfDays);
            logger.info("╚════════════════════════════════════════════════════");

            String deductMsg = "No deduction needed.";
            if (halfDays > 0) {
                deductMsg = deductCasual(rec.getEmployeeEmail(), rec.getYear(), casualDays);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true, "id", rec.getId(),
                    "uncoveredCount", count, "halfDaysDeducted", halfDays,
                    "casualDeducted", casualDays, "remainder", remainder,
                    "deductMessage", deductMsg
            ));
        } catch (Exception e) {
            logger.error("╔══ LATE COVERAGE ERROR ══════════════════════════════");
            logger.error("║ {}", e.getMessage(), e);
            logger.error("╚════════════════════════════════════════════════════");
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String token,
            @PathVariable String id) {
        try {
            Optional<LateCoverageRecord> opt = lateCoverageRepository.findById(id);
            if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Not found"));

            LateCoverageRecord rec = opt.get();
            double toRestore = rec.getCasualDaysDeducted();
            lateCoverageRepository.deleteById(id);

            String msg = "Nothing to restore.";
            if (toRestore > 0) msg = restoreCasual(rec.getEmployeeEmail(), rec.getYear(), toRestore);

            return ResponseEntity.ok(Map.of("success", true, "message", "Deleted. " + msg, "restoredDays", toRestore));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
        }
    }

    @PostMapping("/apply-deductions")
    public ResponseEntity<?> applyDeductions(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> body) {
        try {
            String email  = (String) body.get("employeeEmail");
            int    hd     = Integer.parseInt(body.get("halfDaysToDeduct").toString());
            int    year   = Integer.parseInt(body.get("year").toString());
            double casual = hd * 0.5;
            return ResponseEntity.ok(Map.of("success", true, "message", deductCasual(email, year, casual)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "❌ " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deductCasual — full trace logging to find where 0.5 becomes 1.0
    // ─────────────────────────────────────────────────────────────────────────
    private String deductCasual(String email, int year, double casualDays) {
        logger.info("╔══ deductCasual CALLED ═════════════════════════════");
        logger.info("║ email      = {}", email);
        logger.info("║ year       = {}", year);
        logger.info("║ casualDays = {}  ← THIS should be 0.5 for 3 dates", casualDays);

        try {
            Optional<LeaveEntitlement> opt = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);

            if (opt.isEmpty()) {
                logger.warn("║ ⚠ No CASUAL entitlement found");
                logger.info("╚════════════════════════════════════════════════");
                return String.format("⚠️ No CASUAL entitlement for %s yr%d.", email, year);
            }

            LeaveEntitlement ent = opt.get();
            logger.info("║ BEFORE — id={}", ent.getId());
            logger.info("║ BEFORE — totalEntitlement = {}", ent.getTotalEntitlement());
            logger.info("║ BEFORE — usedDays         = {}", ent.getUsedDays());
            logger.info("║ BEFORE — remainingDays    = {}", ent.getRemainingDays());
            logger.info("║ BEFORE — accumulatedHalf  = {}", ent.getAccumulatedHalfDays());

            double before_used      = ent.getUsedDays();
            double before_remaining = ent.getRemainingDays();
            int    total            = ent.getTotalEntitlement();

            if (before_remaining < casualDays) {
                logger.warn("║ ⚠ Insufficient balance: remaining={} < casualDays={}", before_remaining, casualDays);
                if (before_remaining <= 0) {
                    logger.info("╚════════════════════════════════════════════════");
                    return "⚠️ No CASUAL balance.";
                }
                casualDays = before_remaining;
                logger.warn("║ Capped casualDays to {}", casualDays);
            }

            double new_used      = before_used      + casualDays;
            double new_remaining = before_remaining - casualDays;

            logger.info("║ CALCULATED — casualDays to deduct = {}", casualDays);
            logger.info("║ CALCULATED — new usedDays         = {} + {} = {}", before_used, casualDays, new_used);
            logger.info("║ CALCULATED — new remainingDays    = {} - {} = {}", before_remaining, casualDays, new_remaining);

            // Set fields directly
            ent.setUsedDays(new_used);
            ent.setRemainingDays(new_remaining);

            logger.info("║ AFTER setters (before save) — usedDays={} remainingDays={}",
                    ent.getUsedDays(), ent.getRemainingDays());

            leaveEntitlementRepository.save(ent);

            // Read back from DB to confirm what was actually saved
            Optional<LeaveEntitlement> readback = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
            if (readback.isPresent()) {
                logger.info("║ READBACK (from DB after save):");
                logger.info("║   usedDays      = {}  (expected: {})", readback.get().getUsedDays(), new_used);
                logger.info("║   remainingDays = {}  (expected: {})", readback.get().getRemainingDays(), new_remaining);
                boolean usedOk      = Math.abs(readback.get().getUsedDays()      - new_used)      < 0.001;
                boolean remainingOk = Math.abs(readback.get().getRemainingDays() - new_remaining) < 0.001;
                logger.info("║   usedDays correct?      {}", usedOk      ? "✅ YES" : "❌ NO — BUG HERE");
                logger.info("║   remainingDays correct? {}", remainingOk ? "✅ YES" : "❌ NO — BUG HERE");
            }

            logger.info("╚════════════════════════════════════════════════");

            return String.format("✅ −%.1f day from CASUAL. Remaining: %.1f days.", casualDays, new_remaining);

        } catch (Exception e) {
            logger.error("╚══ deductCasual EXCEPTION: {}", e.getMessage(), e);
            return "❌ " + e.getMessage();
        }
    }

    private String restoreCasual(String email, int year, double casualDays) {
        logger.info("╔══ restoreCasual CALLED ════════════════════════════");
        logger.info("║ email={} year={} casualDays={}", email, year, casualDays);
        try {
            Optional<LeaveEntitlement> opt = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
            if (opt.isEmpty()) return "⚠️ No CASUAL entitlement to restore.";

            LeaveEntitlement ent = opt.get();
            double currentUsed      = ent.getUsedDays();
            double currentRemaining = ent.getRemainingDays();
            int    total            = ent.getTotalEntitlement();

            logger.info("║ BEFORE — used={} remaining={} total={}", currentUsed, currentRemaining, total);

            double toRestore    = Math.min(casualDays, Math.max(0.0, currentUsed));
            double newUsed      = currentUsed      - toRestore;
            double newRemaining = Math.min(total, currentRemaining + toRestore);

            logger.info("║ toRestore={} newUsed={} newRemaining={}", toRestore, newUsed, newRemaining);

            ent.setUsedDays(newUsed);
            ent.setRemainingDays(newRemaining);
            leaveEntitlementRepository.save(ent);

            Optional<LeaveEntitlement> rb = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "CASUAL", year);
            if (rb.isPresent()) {
                logger.info("║ READBACK — used={} remaining={}", rb.get().getUsedDays(), rb.get().getRemainingDays());
            }
            logger.info("╚════════════════════════════════════════════════");

            return String.format("✅ +%.1f restored to CASUAL. Remaining: %.1f days.", toRestore, newRemaining);
        } catch (Exception e) {
            logger.error("╚══ restoreCasual EXCEPTION: {}", e.getMessage(), e);
            return "❌ " + e.getMessage();
        }
    }
}