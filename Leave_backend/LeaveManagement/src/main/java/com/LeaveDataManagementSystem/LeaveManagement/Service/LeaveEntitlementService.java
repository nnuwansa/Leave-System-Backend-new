package com.LeaveDataManagementSystem.LeaveManagement.Service;

import com.LeaveDataManagementSystem.LeaveManagement.Model.*;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.ShortLeaveEntitlementRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.LeaveDataManagementSystem.LeaveManagement.Model.HistoricalLeaveSummary;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.HistoricalLeaveSummaryRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaveEntitlementService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveEntitlementService.class);

    @Autowired
    private LeaveEntitlementRepository leaveEntitlementRepository;

    @Autowired
    private ShortLeaveEntitlementRepository shortLeaveEntitlementRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HistoricalLeaveSummaryRepository historicalLeaveSummaryRepository;

    // Standard leave entitlements (-1 means unlimited for DUTY)
    private static final Map<String, Integer> STANDARD_ENTITLEMENTS = Map.of(
            "CASUAL", 21,
            "SICK", 24,
            "DUTY", -1
    );

    // Desired order of leave types
    private static final List<String> LEAVE_ORDER = Arrays.asList("CASUAL", "SICK", "DUTY");

    // ═══════════════════════════════════════════════════════════════════
    // INITIALIZE ENTITLEMENTS — with vacation carry-over from previous year ONLY
    // RULE: carry-over = previous year (currentYear-1) SICK remaining ONLY
    //       Calculation: prevYear.usedDays subtracted from prevYear BASE (24)
    //       NOT from totalEntitlement (which may include emergency leave additions)
    //       This ensures only natural remaining is carried, not emergency grants
    // ═══════════════════════════════════════════════════════════════════
    public void initializeEntitlementsForEmployee(String employeeEmail) {
        int currentYear = LocalDate.now().getYear();
        int previousYear = currentYear - 1;

        // ── Step 1: Calculate carry-over from previousYear SICK only ─────
        // Priority 1: HistoricalLeaveSummaries (manually entered by admin — most accurate)
        //             carryOver = sickTotal - sickUsed
        // Priority 2: LeaveEntitlement table (base 24 - usedDays)
        double vacationCarryOver = 0.0;

        // Try HistoricalLeaveSummaries first (admin-entered historical data)
        List<HistoricalLeaveSummary> histSummaries =
                historicalLeaveSummaryRepository.findByEmployeeEmail(employeeEmail);
        java.util.Optional<HistoricalLeaveSummary> prevHist = histSummaries.stream()
                .filter(h -> h.getYear() == previousYear)
                .findFirst();

        if (prevHist.isPresent()) {
            HistoricalLeaveSummary hist = prevHist.get();
            double sickTotal = hist.getSickTotal() > 0 ? hist.getSickTotal() : 24.0;
            double sickUsed  = hist.getSickUsed();
            vacationCarryOver = Math.max(0, sickTotal - sickUsed);
            logger.info("Carry-over from HistoricalLeaveSummary for {}: {} days (sickTotal={} - sickUsed={}) from year {}",
                    employeeEmail, vacationCarryOver, sickTotal, sickUsed, previousYear);
        } else {
            // Fallback: LeaveEntitlement table (base 24 - usedDays)
            Optional<LeaveEntitlement> prevYearSick = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "SICK", previousYear);
            if (prevYearSick.isPresent()) {
                LeaveEntitlement prev = prevYearSick.get();
                if (!prev.isUnlimited()) {
                    double prevYearBase = 24.0;
                    double prevYearUsed = prev.getUsedDays();
                    vacationCarryOver = Math.max(0, prevYearBase - prevYearUsed);
                    logger.info("Carry-over from LeaveEntitlement for {}: {} days (24 - used={}) from year {}",
                            employeeEmail, vacationCarryOver, prevYearUsed, previousYear);
                }
            }
        }

        // ── Step 2: Initialize each leave type ───────────────────────────
        for (Map.Entry<String, Integer> entry : STANDARD_ENTITLEMENTS.entrySet()) {
            String leaveType = entry.getKey();
            int baseEntitlement = entry.getValue();

            if (!leaveEntitlementRepository.existsByEmployeeEmailAndLeaveTypeAndYear(
                    employeeEmail, leaveType, currentYear)) {

                int totalEntitlement = baseEntitlement;

                // For SICK (Vacation): add carry-over from PREVIOUS YEAR ONLY (not older years)
                if ("SICK".equals(leaveType) && vacationCarryOver > 0) {
                    totalEntitlement = (int) (baseEntitlement + Math.floor(vacationCarryOver));
                    logger.info("Vacation for {}: base={} + carry-over(from {})={} = total={}",
                            employeeEmail, baseEntitlement, previousYear, vacationCarryOver, totalEntitlement);
                }

                LeaveEntitlement newEntitlement = new LeaveEntitlement(
                        employeeEmail, leaveType, totalEntitlement, currentYear);

                if ("SICK".equals(leaveType) && vacationCarryOver > 0) {
                    newEntitlement.setCarryOverDays(vacationCarryOver);
                }

                leaveEntitlementRepository.save(newEntitlement);
                logger.info("Initialized {} entitlement for {}: {} days",
                        leaveType, employeeEmail, totalEntitlement == -1 ? "Unlimited" : totalEntitlement);
            }
        }

        // ── Step 3: Initialize short leave for current month ─────────────
        int currentMonth = LocalDate.now().getMonthValue();
        initializeShortLeaveEntitlementForMonth(employeeEmail, currentYear, currentMonth);
    }

    // ═══════════════════════════════════════════════════════════════════
    // CORRECT ENTITLEMENT — fix an existing year's SICK entitlement
    // Sets totalEntitlement = 24 (base) + previousYear natural carry-over
    // Adjusts remainingDays = totalEntitlement - usedDays
    // Call this to fix records that were incorrectly calculated
    // ═══════════════════════════════════════════════════════════════════
    public Map<String, Object> correctSickEntitlement(String employeeEmail, int year) {
        Map<String, Object> result = new LinkedHashMap<>();
        int previousYear = year - 1;

        // Calculate correct carry-over — HistoricalLeaveSummaries FIRST, then LeaveEntitlement fallback
        double vacationCarryOver = 0.0;

        List<HistoricalLeaveSummary> histList =
                historicalLeaveSummaryRepository.findByEmployeeEmail(employeeEmail);
        java.util.Optional<HistoricalLeaveSummary> prevHist = histList.stream()
                .filter(h -> h.getYear() == previousYear).findFirst();

        if (prevHist.isPresent()) {
            HistoricalLeaveSummary h = prevHist.get();
            double st = h.getSickTotal() > 0 ? h.getSickTotal() : 24.0;
            vacationCarryOver = Math.max(0, st - h.getSickUsed());
            logger.info("correctSickEntitlement: carry-over from HistoricalSummary yr={} sickTotal={} sickUsed={} carryOver={}",
                    previousYear, st, h.getSickUsed(), vacationCarryOver);
        } else {
            Optional<LeaveEntitlement> prevYearSick = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "SICK", previousYear);
            if (prevYearSick.isPresent()) {
                LeaveEntitlement prev = prevYearSick.get();
                double prevBase = 24.0;
                double prevUsed = prev.getUsedDays();
                vacationCarryOver = Math.max(0, prevBase - prevUsed);
                logger.info("correctSickEntitlement: carry-over from LeaveEntitlement yr={} used={} carryOver={}",
                        previousYear, prevUsed, vacationCarryOver);
            }
        }

        double correctTotal = 24.0 + vacationCarryOver;

        Optional<LeaveEntitlement> currentSick = leaveEntitlementRepository
                .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "SICK", year);

        if (currentSick.isPresent()) {
            LeaveEntitlement ent = currentSick.get();
            double usedDays = ent.getUsedDays();
            double correctRemaining = Math.max(0, correctTotal - usedDays);

            double oldTotal     = ent.getTotalEntitlement();
            double oldRemaining = ent.getRemainingDays();
            double oldCarryOver = ent.getCarryOverDays();

            ent.setTotalEntitlement((int) correctTotal);
            ent.setRemainingDays(correctRemaining);
            ent.setCarryOverDays(vacationCarryOver);
            leaveEntitlementRepository.save(ent);

            result.put("success", true);
            result.put("employeeEmail", employeeEmail);
            result.put("year", year);
            result.put("previousYear", previousYear);
            result.put("carryOver", vacationCarryOver);
            result.put("oldTotal", oldTotal);
            result.put("newTotal", correctTotal);
            result.put("oldRemaining", oldRemaining);
            result.put("newRemaining", correctRemaining);
            result.put("usedDays", usedDays);
            result.put("message", "✅ Corrected SICK entitlement for " + employeeEmail + " year " + year);
            logger.info("Corrected SICK entitlement: {} year {} | total: {}→{} | remaining: {}→{} | carryOver: {}→{}",
                    employeeEmail, year, oldTotal, correctTotal, oldRemaining, correctRemaining, oldCarryOver, vacationCarryOver);
        } else {
            result.put("success", false);
            result.put("message", "No SICK entitlement found for " + employeeEmail + " year " + year);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // VALIDATE LEAVE REQUEST
    // Half-day: try CASUAL first, fall back to SICK (Vacation) if exhausted
    // ═══════════════════════════════════════════════════════════════════
    public String validateLeaveRequest(String employeeEmail, String leaveType,
                                       LocalDate startDate, LocalDate endDate,
                                       boolean isHalfDay, String halfDayPeriod) {

        int currentYear = LocalDate.now().getYear();

        if ("SHORT".equals(leaveType) || "SHORT_LEAVE".equals(leaveType)) {
            return validateShortLeaveRequest(employeeEmail, startDate);
        }

        double requestedDays;
        if (isHalfDay || "HALF_DAY".equals(leaveType)) {
            requestedDays = 0.5;
        } else {
            requestedDays = calculateDays(startDate, endDate);
        }

        initializeEntitlementsForEmployee(employeeEmail);

        // ── Half-day: CASUAL first, then SICK (Vacation) fallback ────────
        if ("HALF_DAY".equals(leaveType) || isHalfDay) {
            Optional<LeaveEntitlement> casualOpt = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "CASUAL", currentYear);

            if (casualOpt.isPresent() && casualOpt.get().canTakeHalfDay()) {
                return "VALID"; // Casual has balance
            }

            // Casual exhausted — check Vacation (SICK)
            Optional<LeaveEntitlement> sickOpt = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "SICK", currentYear);

            if (sickOpt.isPresent() && sickOpt.get().canTakeHalfDay()) {
                logger.info("Casual exhausted for {}. Half-day will deduct from Vacation.", employeeEmail);
                return "VALID_USE_VACATION";
            }

            double casualRemaining = casualOpt.map(LeaveEntitlement::getEffectiveRemainingDays).orElse(0.0);
            double sickRemaining = sickOpt.map(LeaveEntitlement::getEffectiveRemainingDays).orElse(0.0);
            return String.format(
                    "Insufficient leave balance for half-day. Casual: %.1f days, Vacation: %.1f days remaining.",
                    casualRemaining, sickRemaining);
        }

        // ── Regular leave validation ──────────────────────────────────────
        String actualLeaveType = "HALF_DAY".equals(leaveType) ? "CASUAL" : leaveType;

        Optional<LeaveEntitlement> entitlementOpt = leaveEntitlementRepository
                .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, actualLeaveType, currentYear);

        if (entitlementOpt.isEmpty()) {
            return "Leave entitlement not found for leave type: " + actualLeaveType;
        }

        LeaveEntitlement entitlement = entitlementOpt.get();

        if ("DUTY".equals(actualLeaveType) && entitlement.isUnlimited()) {
            return "VALID";
        }

        if (!entitlement.hasSufficientLeave(requestedDays)) {
            if (entitlement.isUnlimited()) return "VALID";
            return String.format(
                    "Insufficient %s leave balance. Requested: %.1f days, Available: %.1f days",
                    actualLeaveType.replace("_", " "), requestedDays, entitlement.getRemainingDays());
        }

        return "VALID";
    }

    // Overloaded — backward compatibility
    public String validateLeaveRequest(String employeeEmail, String leaveType,
                                       LocalDate startDate, LocalDate endDate) {
        boolean isHalfDay = "HALF_DAY".equals(leaveType);
        return validateLeaveRequest(employeeEmail, leaveType, startDate, endDate, isHalfDay, null);
    }

    // ═══════════════════════════════════════════════════════════════════
    // UPDATE ENTITLEMENTS ON APPROVAL
    // Half-day: CASUAL first, Vacation fallback
    // Stores which leave type was deducted so revert is correct
    // ═══════════════════════════════════════════════════════════════════
    public void updateEntitlementOnLeaveApproval(String employeeEmail, String leaveType,
                                                 LocalDate startDate, LocalDate endDate,
                                                 boolean isShortLeave, boolean isHalfDay,
                                                 int workingDays) {
        int currentYear = LocalDate.now().getYear();

        if (isShortLeave || "SHORT".equals(leaveType) || "SHORT_LEAVE".equals(leaveType)) {
            updateShortLeaveEntitlementOnApproval(employeeEmail, startDate);
            return;
        }

        // ── Half-day: deduct from CASUAL first, fallback to SICK (Vacation) ─
        if ("HALF_DAY".equals(leaveType) || isHalfDay) {
            Optional<LeaveEntitlement> casualOpt = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "CASUAL", currentYear);

            if (casualOpt.isPresent() && casualOpt.get().canTakeHalfDay()) {
                // Deduct from CASUAL
                LeaveEntitlement casual = casualOpt.get();
                casual.addHalfDay();
                casual.setHalfDayDeductedFrom("CASUAL");
                leaveEntitlementRepository.save(casual);
                logger.info("Half-day deducted from CASUAL for {}. AccumulatedHalfDays={}, UsedDays={}",
                        employeeEmail, casual.getAccumulatedHalfDays(), casual.getUsedDays());
            } else {
                // Casual exhausted — deduct from SICK (Vacation)
                Optional<LeaveEntitlement> sickOpt = leaveEntitlementRepository
                        .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "SICK", currentYear);

                if (sickOpt.isPresent()) {
                    LeaveEntitlement sick = sickOpt.get();
                    sick.addHalfDay();
                    sick.setHalfDayDeductedFrom("SICK");
                    leaveEntitlementRepository.save(sick);
                    logger.info("Casual exhausted — half-day deducted from VACATION for {}. " +
                                    "AccumulatedHalfDays={}, UsedDays={}",
                            employeeEmail, sick.getAccumulatedHalfDays(), sick.getUsedDays());
                } else {
                    logger.warn("No SICK entitlement found for half-day fallback for {}", employeeEmail);
                }
            }
            return;
        }

        // ── Regular leave ─────────────────────────────────────────────────
        String actualLeaveType = leaveType;
        double leaveDays = workingDays;

        Optional<LeaveEntitlement> entitlementOpt = leaveEntitlementRepository
                .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, actualLeaveType, currentYear);

        if (entitlementOpt.isPresent()) {
            LeaveEntitlement entitlement = entitlementOpt.get();
            entitlement.updateUsedDays(leaveDays);
            leaveEntitlementRepository.save(entitlement);
            logger.info("Updated entitlement for {} - Type: {}, Used: {}, Remaining: {}, Unlimited: {}",
                    employeeEmail, actualLeaveType, entitlement.getUsedDays(),
                    entitlement.getRemainingDaysDisplay(), entitlement.isUnlimited());
        } else {
            logger.warn("No entitlement found for type: {} for employee: {}", actualLeaveType, employeeEmail);
        }
    }

    // Overloaded — backward compatibility
    public void updateEntitlementOnLeaveApproval(String employeeEmail, String leaveType,
                                                 LocalDate startDate, LocalDate endDate) {
        boolean isHalfDay = "HALF_DAY".equals(leaveType);
        boolean isShortLeave = "SHORT".equals(leaveType) || "SHORT_LEAVE".equals(leaveType);
        int workingDays = calculateDays(startDate, endDate);
        updateEntitlementOnLeaveApproval(employeeEmail, leaveType, startDate, endDate,
                isShortLeave, isHalfDay, workingDays);
    }

    // ═══════════════════════════════════════════════════════════════════
    // REVERT ENTITLEMENTS ON REJECTION/CANCELLATION
    // Half-day: checks halfDayDeductedFrom to revert from correct leave type
    // ═══════════════════════════════════════════════════════════════════
    public void revertEntitlementOnLeaveRejection(String employeeEmail, String leaveType,
                                                  LocalDate startDate, LocalDate endDate,
                                                  boolean isShortLeave, boolean isHalfDay,
                                                  int workingDays) {
        logger.info("Reverting entitlement for employee: {}, leaveType: {}, isHalfDay: {}, workingDays: {}",
                employeeEmail, leaveType, isHalfDay, workingDays);

        int currentYear = LocalDate.now().getYear();

        if (isShortLeave || "SHORT".equals(leaveType) || "SHORT_LEAVE".equals(leaveType)) {
            revertShortLeaveEntitlementOnRejection(employeeEmail, startDate);
            return;
        }

        // ── Half-day reversion: find which leave type was actually deducted ─
        if ("HALF_DAY".equals(leaveType) || isHalfDay) {
            // Check CASUAL — if it has an odd accumulatedHalfDay, deduction was from CASUAL
            Optional<LeaveEntitlement> casualOpt = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "CASUAL", currentYear);
            Optional<LeaveEntitlement> sickOpt = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "SICK", currentYear);

            // Determine where the half-day was deducted from:
            // If CASUAL has accumulatedHalfDays > 0 OR used days > what sick shows, revert CASUAL
            // Simple approach: check halfDayDeductedFrom field on the entitlement that has it set
            boolean revertedFromCasual = false;

            if (casualOpt.isPresent()) {
                LeaveEntitlement casual = casualOpt.get();
                // If casual has any half-day usage (accumulatedHalfDays > 0 or usedDays > 0),
                // try reverting from casual first (FIFO: first deducted from casual first)
                if (casual.getAccumulatedHalfDays() > 0 || casual.getUsedDays() > 0) {
                    // Check if casual was the source by seeing if it has remaining capacity
                    // that indicates it was being used (i.e., not fully remaining from start)
                    double casualInitial = STANDARD_ENTITLEMENTS.get("CASUAL");
                    boolean casualWasUsed = casual.getUsedDays() > 0 ||
                            casual.getAccumulatedHalfDays() > 0;

                    if (casualWasUsed) {
                        casual.removeHalfDay();
                        leaveEntitlementRepository.save(casual);
                        logger.info("Half-day reverted from CASUAL for {}. AccumulatedHalfDays={}, UsedDays={}",
                                employeeEmail, casual.getAccumulatedHalfDays(), casual.getUsedDays());
                        revertedFromCasual = true;
                    }
                }
            }

            if (!revertedFromCasual && sickOpt.isPresent()) {
                // Casual wasn't used — must have been from SICK (Vacation)
                LeaveEntitlement sick = sickOpt.get();
                if (sick.getAccumulatedHalfDays() > 0 || sick.getUsedDays() > 0) {
                    sick.removeHalfDay();
                    leaveEntitlementRepository.save(sick);
                    logger.info("Half-day reverted from VACATION for {}. AccumulatedHalfDays={}, UsedDays={}",
                            employeeEmail, sick.getAccumulatedHalfDays(), sick.getUsedDays());
                }
            }
            return;
        }

        // ── Regular leave reversion ───────────────────────────────────────
        String actualLeaveType = leaveType;
        double leaveDays = workingDays;

        Optional<LeaveEntitlement> entitlementOpt = leaveEntitlementRepository
                .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, actualLeaveType, currentYear);

        if (entitlementOpt.isPresent()) {
            LeaveEntitlement entitlement = entitlementOpt.get();
            double oldUsedDays = entitlement.getUsedDays();

            entitlement.setUsedDaysAndRecalculate(Math.max(0, entitlement.getUsedDays() - leaveDays));

            leaveEntitlementRepository.save(entitlement);
            logger.info("Reverted {} leave. Used: {} -> {}, Remaining: {}, Unlimited: {}",
                    actualLeaveType, oldUsedDays, entitlement.getUsedDays(),
                    entitlement.getRemainingDaysDisplay(), entitlement.isUnlimited());
        } else {
            logger.warn("No entitlement found for employee: {}, leaveType: {}", employeeEmail, actualLeaveType);
        }
    }

    // Overloaded — backward compatibility
    public void revertEntitlementOnLeaveRejection(String employeeEmail, String leaveType,
                                                  LocalDate startDate, LocalDate endDate) {
        boolean isHalfDay = "HALF_DAY".equals(leaveType);
        boolean isShortLeave = "SHORT".equals(leaveType) || "SHORT_LEAVE".equals(leaveType);
        int workingDays = calculateDays(startDate, endDate);
        revertEntitlementOnLeaveRejection(employeeEmail, leaveType, startDate, endDate,
                isShortLeave, isHalfDay, workingDays);
    }

    // ═══════════════════════════════════════════════════════════════════
    // RECALCULATE ENTITLEMENTS
    // Handles half-day fallback during recalculation too
    // ═══════════════════════════════════════════════════════════════════
    public void recalculateEntitlements(String employeeEmail) {
        int currentYear = LocalDate.now().getYear();
        logger.info("Starting recalculation for employee: {}, year: {}", employeeEmail, currentYear);

        initializeEntitlementsForEmployee(employeeEmail);

        // ── Also correct the SICK entitlement totalEntitlement/carryOverDays ──
        // In case existing record has inflated values from emergency leave additions
        correctSickEntitlement(employeeEmail, currentYear);

        List<Leave> approvedLeaves = leaveRepository.findByEmployeeEmailOrderByCreatedAtDesc(employeeEmail)
                .stream()
                .filter(l -> l.getStartDate().getYear() == currentYear
                        && l.getStatus() == LeaveStatus.APPROVED
                        && !l.isCancelled())
                .toList();

        logger.info("Found {} approved leaves for recalculation", approvedLeaves.size());

        // Reset all entitlements
        List<LeaveEntitlement> entitlements = leaveEntitlementRepository
                .findByEmployeeEmailAndYear(employeeEmail, currentYear);

        for (LeaveEntitlement ent : entitlements) {
            ent.setUsedDays(0);
            ent.setAccumulatedHalfDays(0);
            if (ent.isUnlimited()) {
                ent.setRemainingDays(-1.0);
            } else {
                ent.setRemainingDays(ent.getTotalEntitlement()); // includes carry-over since totalEntitlement was set at init
            }
            leaveEntitlementRepository.save(ent);
        }

        // Reset short leave entitlements
        List<ShortLeaveEntitlement> shortLeaveEntitlements =
                shortLeaveEntitlementRepository.findByEmployeeEmailAndYear(employeeEmail, currentYear);
        for (ShortLeaveEntitlement sl : shortLeaveEntitlements) {
            sl.setUsedShortLeaves(0);
            sl.setRemainingShortLeaves(sl.getTotalShortLeaves());
            shortLeaveEntitlementRepository.save(sl);
        }

        // Replay approved leaves in chronological order (important for half-day fallback)
        List<Leave> sortedLeaves = approvedLeaves.stream()
                .sorted(Comparator.comparing(Leave::getStartDate))
                .collect(Collectors.toList());

        for (Leave leave : sortedLeaves) {
            if (leave.isShortLeave() || "SHORT".equals(leave.getLeaveType()) || "SHORT_LEAVE".equals(leave.getLeaveType())) {
                updateShortLeaveEntitlementOnApproval(employeeEmail, leave.getStartDate());
                continue;
            }

            if (leave.isHalfDay() || "HALF_DAY".equals(leave.getLeaveType())) {
                // Use the same fallback logic as approval
                updateEntitlementOnLeaveApproval(employeeEmail, "HALF_DAY",
                        leave.getStartDate(), leave.getEndDate(), false, true, 0);
                continue;
            }

            String actualLeaveType = leave.getLeaveType();
            int workingDays = leave.getWorkingDays() > 0
                    ? leave.getWorkingDays()
                    : calculateDays(leave.getStartDate(), leave.getEndDate());

            Optional<LeaveEntitlement> entOpt = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, actualLeaveType, currentYear);

            if (entOpt.isPresent()) {
                LeaveEntitlement ent = entOpt.get();
                ent.updateUsedDays(workingDays);
                leaveEntitlementRepository.save(ent);
                logger.info("Recalc: added {} days to {} for {}", workingDays, actualLeaveType, employeeEmail);
            }
        }

        logger.info("Recalculation completed for employee: {}", employeeEmail);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHORT LEAVE METHODS (unchanged)
    // ═══════════════════════════════════════════════════════════════════
    public String validateShortLeaveRequest(String employeeEmail, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        initializeShortLeaveEntitlementForMonth(employeeEmail, year, month);

        Optional<ShortLeaveEntitlement> opt =
                shortLeaveEntitlementRepository.findByEmployeeEmailAndYearAndMonth(employeeEmail, year, month);

        if (opt.isEmpty()) return "Short leave entitlement not found for the month";

        ShortLeaveEntitlement sl = opt.get();
        if (!sl.hasShortLeaveAvailable()) {
            return String.format(
                    "You have already taken the maximum number of short leaves (%d) this month. Remaining: %d",
                    sl.getTotalShortLeaves(), sl.getRemainingShortLeaves());
        }
        return "VALID";
    }

    public void initializeShortLeaveEntitlementForMonth(String employeeEmail, int year, int month) {
        if (!shortLeaveEntitlementRepository.existsByEmployeeEmailAndYearAndMonth(employeeEmail, year, month)) {
            ShortLeaveEntitlement sl = new ShortLeaveEntitlement(employeeEmail, year, month);
            shortLeaveEntitlementRepository.save(sl);
        }
    }

    private void updateShortLeaveEntitlementOnApproval(String employeeEmail, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        initializeShortLeaveEntitlementForMonth(employeeEmail, year, month);

        Optional<ShortLeaveEntitlement> opt =
                shortLeaveEntitlementRepository.findByEmployeeEmailAndYearAndMonth(employeeEmail, year, month);
        if (opt.isPresent()) {
            ShortLeaveEntitlement sl = opt.get();
            sl.useShortLeave();
            shortLeaveEntitlementRepository.save(sl);
        }
    }

    private void revertShortLeaveEntitlementOnRejection(String employeeEmail, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        logger.info("Reverting short leave for employee: {}, year: {}, month: {}", employeeEmail, year, month);

        Optional<ShortLeaveEntitlement> opt =
                shortLeaveEntitlementRepository.findByEmployeeEmailAndYearAndMonth(employeeEmail, year, month);
        if (opt.isPresent()) {
            ShortLeaveEntitlement sl = opt.get();
            sl.revertShortLeave();
            shortLeaveEntitlementRepository.save(sl);
            logger.info("Short leave reverted. used={}, remaining={}", sl.getUsedShortLeaves(), sl.getRemainingShortLeaves());
        }
    }

    public List<ShortLeaveEntitlement> getEmployeeShortLeaveEntitlements(String employeeEmail) {
        return shortLeaveEntitlementRepository.findByEmployeeEmailOrderByYearDescMonthDesc(employeeEmail);
    }

    public ShortLeaveEntitlement getEmployeeShortLeaveEntitlement(String employeeEmail, int year, int month) {
        initializeShortLeaveEntitlementForMonth(employeeEmail, year, month);
        return shortLeaveEntitlementRepository.findByEmployeeEmailAndYearAndMonth(employeeEmail, year, month)
                .orElse(null);
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET ENTITLEMENTS
    // ═══════════════════════════════════════════════════════════════════
    public List<LeaveEntitlement> getEmployeeEntitlements(String employeeEmail) {
        int currentYear = LocalDate.now().getYear();
        initializeEntitlementsForEmployee(employeeEmail);
        List<LeaveEntitlement> entitlements =
                leaveEntitlementRepository.findByEmployeeEmailAndYear(employeeEmail, currentYear);
        entitlements.sort(Comparator.comparingInt(e -> LEAVE_ORDER.indexOf(e.getLeaveType())));
        return entitlements;
    }

    public List<LeaveEntitlement> getEmployeeEntitlementsByYear(String employeeEmail, int year) {
        List<LeaveEntitlement> entitlements =
                leaveEntitlementRepository.findByEmployeeEmailAndYear(employeeEmail, year);
        entitlements.sort(Comparator.comparingInt(e -> LEAVE_ORDER.indexOf(e.getLeaveType())));
        return entitlements;
    }

    public void initializeEntitlementsForNewYear(int year) {
        List<User> allUsers = userRepository.findAll();
        int previousYear = year - 1;

        for (User user : allUsers) {
            String email = user.getEmail();

            // Get previous year vacation remaining for carry-over
            double vacationCarryOver = 0.0;
            Optional<LeaveEntitlement> prevSick = leaveEntitlementRepository
                    .findByEmployeeEmailAndLeaveTypeAndYear(email, "SICK", previousYear);
            if (prevSick.isPresent() && !prevSick.get().isUnlimited()
                    && prevSick.get().getRemainingDays() > 0) {
                vacationCarryOver = prevSick.get().getRemainingDays();
            }

            for (Map.Entry<String, Integer> entry : STANDARD_ENTITLEMENTS.entrySet()) {
                String leaveType = entry.getKey();
                int baseEntitlement = entry.getValue();

                if (!leaveEntitlementRepository.existsByEmployeeEmailAndLeaveTypeAndYear(email, leaveType, year)) {
                    int totalEntitlement = baseEntitlement;
                    if ("SICK".equals(leaveType) && vacationCarryOver > 0) {
                        totalEntitlement = (int) (baseEntitlement + Math.floor(vacationCarryOver));
                    }

                    LeaveEntitlement newEnt = new LeaveEntitlement(email, leaveType, totalEntitlement, year);
                    if ("SICK".equals(leaveType) && vacationCarryOver > 0) {
                        newEnt.setCarryOverDays(vacationCarryOver);
                    }
                    leaveEntitlementRepository.save(newEnt);
                }
            }

            for (int month = 1; month <= 12; month++) {
                initializeShortLeaveEntitlementForMonth(email, year, month);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENTITLEMENT SUMMARY
    // ═══════════════════════════════════════════════════════════════════
    public Map<String, Object> getEntitlementSummary(String employeeEmail) {
        int currentYear = LocalDate.now().getYear();
        List<LeaveEntitlement> entitlements = getEmployeeEntitlements(employeeEmail);

        Map<String, Object> summary = new HashMap<>();
        summary.put("year", currentYear);
        summary.put("employeeEmail", employeeEmail);
        summary.put("entitlements", entitlements);

        double totalUsed = entitlements.stream()
                .filter(e -> !e.isUnlimited())
                .mapToDouble(e -> e.getUsedDays() + (e.getAccumulatedHalfDays() * 0.5))
                .sum();

        double totalRemaining = entitlements.stream()
                .filter(e -> !e.isUnlimited())
                .mapToDouble(LeaveEntitlement::getEffectiveRemainingDays)
                .sum();

        Optional<LeaveEntitlement> dutyLeave = entitlements.stream()
                .filter(e -> "DUTY".equals(e.getLeaveType()) && e.isUnlimited())
                .findFirst();

        if (dutyLeave.isPresent()) {
            summary.put("dutyLeaveUsed", dutyLeave.get().getUsedDays());
            summary.put("dutyLeaveUnlimited", true);
        }

        summary.put("totalUsed", totalUsed);
        summary.put("totalRemaining", totalRemaining);

        // Expose vacation carry-over info
        entitlements.stream()
                .filter(e -> "SICK".equals(e.getLeaveType()) && e.getCarryOverDays() > 0)
                .findFirst()
                .ifPresent(sick -> {
                    summary.put("vacationCarryOver", sick.getCarryOverDays());
                    summary.put("vacationBaseEntitlement", 24);
                    summary.put("vacationTotalEntitlement", sick.getTotalEntitlement());
                });

        int currentMonth = LocalDate.now().getMonthValue();
        ShortLeaveEntitlement currentMonthShortLeave =
                getEmployeeShortLeaveEntitlement(employeeEmail, currentYear, currentMonth);

        if (currentMonthShortLeave != null) {
            summary.put("shortLeaveThisMonth", Map.of(
                    "total", currentMonthShortLeave.getTotalShortLeaves(),
                    "used", currentMonthShortLeave.getUsedShortLeaves(),
                    "remaining", currentMonthShortLeave.getRemainingShortLeaves()
            ));
        }

        return summary;
    }

    public Map<String, Object> getComprehensiveEntitlementSummary(String employeeEmail) {
        Map<String, Object> summary = getEntitlementSummary(employeeEmail);
        List<ShortLeaveEntitlement> shortLeaveEntitlements = getEmployeeShortLeaveEntitlements(employeeEmail);
        summary.put("shortLeaveEntitlements", shortLeaveEntitlements);
        return summary;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ADJUST / FORCE REFRESH
    // ═══════════════════════════════════════════════════════════════════
    public void adjustEntitlement(String employeeEmail, String leaveType, int year, int newTotalEntitlement) {
        Optional<LeaveEntitlement> entitlementOpt = leaveEntitlementRepository
                .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, leaveType, year);

        if (entitlementOpt.isPresent()) {
            LeaveEntitlement ent = entitlementOpt.get();
            ent.setTotalEntitlement(newTotalEntitlement);
            if (newTotalEntitlement == -1) {
                ent.setRemainingDays(-1.0);
            } else {
                ent.setRemainingDays(newTotalEntitlement - ent.getUsedDays());
            }
            leaveEntitlementRepository.save(ent);
            logger.info("Adjusted entitlement for {} - Type: {}, New Total: {}, Unlimited: {}",
                    employeeEmail, leaveType,
                    newTotalEntitlement == -1 ? "Unlimited" : newTotalEntitlement,
                    newTotalEntitlement == -1);
        } else {
            LeaveEntitlement newEnt = new LeaveEntitlement(employeeEmail, leaveType, newTotalEntitlement, year);
            leaveEntitlementRepository.save(newEnt);
        }
    }

    public void forceRefreshEntitlements(String employeeEmail) {
        logger.info("Force refreshing entitlements for employee: {}", employeeEmail);
        recalculateEntitlements(employeeEmail);
        logger.info("Force refresh completed for employee: {}", employeeEmail);
    }

    // ═══════════════════════════════════════════════════════════════════
    // DUTY LEAVE STATISTICS
    // ═══════════════════════════════════════════════════════════════════
    public Map<String, Object> getDutyLeaveStatistics(String employeeEmail, int year) {
        Optional<LeaveEntitlement> dutyOpt = leaveEntitlementRepository
                .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, "DUTY", year);

        Map<String, Object> dutyStats = new HashMap<>();
        dutyStats.put("year", year);
        dutyStats.put("employeeEmail", employeeEmail);
        dutyStats.put("leaveType", "DUTY");
        dutyStats.put("isUnlimited", true);

        if (dutyOpt.isPresent()) {
            LeaveEntitlement duty = dutyOpt.get();
            dutyStats.put("totalDutyLeaveTaken", duty.getUsedDays());
            dutyStats.put("accumulatedHalfDays", duty.getAccumulatedHalfDays());
            dutyStats.put("effectiveDaysUsed", duty.getUsedDays() + (duty.getAccumulatedHalfDays() * 0.5));
        } else {
            dutyStats.put("totalDutyLeaveTaken", 0.0);
            dutyStats.put("accumulatedHalfDays", 0);
            dutyStats.put("effectiveDaysUsed", 0.0);
        }

        List<Leave> dutyLeaves = leaveRepository.findByEmployeeEmailOrderByCreatedAtDesc(employeeEmail)
                .stream()
                .filter(l -> "DUTY".equals(l.getLeaveType())
                        && l.getStartDate().getYear() == year
                        && l.getStatus() == LeaveStatus.APPROVED
                        && !l.isCancelled())
                .collect(Collectors.toList());

        dutyStats.put("totalDutyLeaveRequests", dutyLeaves.size());
        dutyStats.put("dutyLeaves", dutyLeaves.stream().map(l -> {
            Map<String, Object> info = new HashMap<>();
            info.put("id", l.getId());
            info.put("startDate", l.getStartDate());
            info.put("endDate", l.getEndDate());
            info.put("days", l.getTotalDays());
            info.put("reason", l.getReason());
            info.put("approvedAt", l.getApprovalOfficerApprovedAt());
            return info;
        }).collect(Collectors.toList()));

        return dutyStats;
    }

    public Map<String, Object> getDutyLeaveStatistics(String employeeEmail) {
        return getDutyLeaveStatistics(employeeEmail, LocalDate.now().getYear());
    }

    // ═══════════════════════════════════════════════════════════════════
    // SHORT LEAVE MONTHLY BREAKDOWN
    // ═══════════════════════════════════════════════════════════════════
    public Map<String, Object> getEmployeeShortLeaveMonthlyBreakdown(String employeeEmail) {
        try {
            int currentYear = LocalDate.now().getYear();
            Map<String, Object> monthlyData = new HashMap<>();
            String[] monthNames = {"January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"};

            for (int month = 1; month <= 12; month++) {
                try {
                    initializeShortLeaveEntitlementForMonth(employeeEmail, currentYear, month);
                    Optional<ShortLeaveEntitlement> opt =
                            shortLeaveEntitlementRepository.findByEmployeeEmailAndYearAndMonth(
                                    employeeEmail, currentYear, month);

                    Map<String, Integer> monthData = new HashMap<>();
                    if (opt.isPresent()) {
                        ShortLeaveEntitlement sl = opt.get();
                        monthData.put("used", sl.getUsedShortLeaves());
                        monthData.put("total", sl.getTotalShortLeaves());
                        monthData.put("remaining", sl.getRemainingShortLeaves());
                    } else {
                        monthData.put("used", 0);
                        monthData.put("total", 2);
                        monthData.put("remaining", 2);
                    }
                    monthlyData.put(monthNames[month - 1], monthData);
                } catch (Exception monthError) {
                    logger.warn("Error processing month {} for {}: {}", month, employeeEmail, monthError.getMessage());
                    Map<String, Integer> defaultData = new HashMap<>();
                    defaultData.put("used", 0);
                    defaultData.put("total", 2);
                    defaultData.put("remaining", 2);
                    monthlyData.put(monthNames[month - 1], defaultData);
                }
            }
            return monthlyData;
        } catch (Exception e) {
            logger.error("Error getting monthly short leave breakdown for {}: {}", employeeEmail, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════
    private int calculateDays(LocalDate startDate, LocalDate endDate) {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private LeaveEntitlement getOrCreateEntitlement(String employeeEmail, String leaveType, int year) {
        Optional<LeaveEntitlement> opt = leaveEntitlementRepository
                .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, leaveType, year);
        if (opt.isPresent()) return opt.get();

        initializeEntitlementsForEmployee(employeeEmail);
        return leaveEntitlementRepository
                .findByEmployeeEmailAndLeaveTypeAndYear(employeeEmail, leaveType, year)
                .orElseThrow(() -> new RuntimeException("Failed to create entitlement for " + leaveType));
    }

    private String getLeaveTypeDisplayName(String leaveType) {
        switch (leaveType) {
            case "CASUAL":    return "Casual";
            case "SICK":      return "Vacation";
            case "DUTY":      return "Duty";
            case "MATERNITY": return "Maternity";
            case "HALF_DAY":  return "Half Day";
            case "SHORT":
            case "SHORT_LEAVE": return "Short Leave";
            default: return leaveType.replace("_", " ");
        }
    }

    public String validateLeaveRequestWithWorkingDays(String employeeEmail, String leaveType,
                                                      LocalDate startDate, LocalDate endDate,
                                                      int workingDays) {
        int currentYear = startDate.getYear();
        LeaveEntitlement entitlement = getOrCreateEntitlement(employeeEmail, leaveType, currentYear);

        // DUTY leave is unlimited (totalEntitlement = -1, remainingDays = -1)
        // Never reject DUTY leave due to balance
        if (entitlement.isUnlimited() || entitlement.getTotalEntitlement() == -1
                || entitlement.getRemainingDays() < 0) {
            logger.info("DUTY/Unlimited leave — skipping balance check for {} (remainingDays={})",
                    employeeEmail, entitlement.getRemainingDays());
            return "VALID";
        }

        double remainingDays = entitlement.getRemainingDays();
        if (workingDays > remainingDays) {
            return String.format(
                    "Insufficient %s leave balance. Requested: %d working days, Available: %.1f days",
                    getLeaveTypeDisplayName(leaveType), workingDays, remainingDays);
        }
        return "VALID";
    }
}