package com.LeaveDataManagementSystem.LeaveManagement.Service;

import com.LeaveDataManagementSystem.LeaveManagement.Model.EmergencyLeaveRequest;
import com.LeaveDataManagementSystem.LeaveManagement.Model.EmergencyLeaveRequest.YearGrant;
import com.LeaveDataManagementSystem.LeaveManagement.Model.HistoricalLeaveSummary;
import com.LeaveDataManagementSystem.LeaveManagement.Model.LeaveEntitlement;
import com.LeaveDataManagementSystem.LeaveManagement.Model.User;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.EmergencyLeaveRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.HistoricalLeaveSummaryRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.LeaveEntitlementRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EmergencyLeaveService {

    private static final Logger logger = LoggerFactory.getLogger(EmergencyLeaveService.class);

    @Autowired private EmergencyLeaveRepository         emergencyLeaveRepository;
    @Autowired private LeaveEntitlementRepository       leaveEntitlementRepository;
    @Autowired private UserRepository                   userRepository;
    @Autowired private HistoricalLeaveSummaryRepository historicalLeaveSummaryRepository;

    // =========================================================================
    // CHECK: current year vacation balance remaining
    // Emergency leave can only be requested when current year balance = 0
    // =========================================================================
    public double getCurrentYearVacationRemaining(String employeeEmail) {
        int currentYear = LocalDate.now().getYear();
        List<LeaveEntitlement> ents = leaveEntitlementRepository.findByEmployeeEmail(employeeEmail);
        Optional<LeaveEntitlement> sickEnt = ents.stream()
                .filter(e -> "SICK".equals(e.getLeaveType()) && e.getYear() == currentYear)
                .findFirst();
        if (sickEnt.isPresent()) {
            return Math.max(0, sickEnt.get().getRemainingDays());
        }
        return 0.0; // no entitlement record = 0 remaining
    }

    // =========================================================================
    // CREATE — single request carrying multiple year grants
    // RULE: only years <= currentYear-2 are eligible
    //       (currentYear-1 i.e. 2025 is already included in current year balance)
    //       current year vacation balance must be 0 before requesting
    // =========================================================================
    public Map<String, Object> createRequest(
            String adminEmail,
            String employeeEmail,
            List<Map<String, Object>> yearGrantsRaw,
            String reason,
            String approvalOfficerEmail) {

        Map<String, Object> result = new LinkedHashMap<>();
        int currentYear = LocalDate.now().getYear();

        User employee = userRepository.findByEmail(employeeEmail);
        if (employee == null) { result.put("success",false); result.put("message","❌ Employee not found: "+employeeEmail); return result; }

        User admin = userRepository.findByEmail(adminEmail);
        if (admin == null) { result.put("success",false); result.put("message","❌ Admin not found"); return result; }

        User approvalOfficer = userRepository.findByEmail(approvalOfficerEmail);
        if (approvalOfficer == null) { result.put("success",false); result.put("message","❌ Approval officer not found: "+approvalOfficerEmail); return result; }

        if (yearGrantsRaw == null || yearGrantsRaw.isEmpty()) {
            result.put("success",false); result.put("message","❌ At least one year grant is required"); return result;
        }


        double currentYearRemaining = getCurrentYearVacationRemaining(employeeEmail);
        if (currentYearRemaining > 0) {
            result.put("success", false);
            result.put("message",
                    "❌ Cannot request emergency leave. Employee still has " + currentYearRemaining +
                            " vacation days remaining in " + currentYear + ". " +
                            "Emergency leave from previous years can only be granted after current year vacation balance is fully used.");
            result.put("currentYearRemaining", currentYearRemaining);
            return result;
        }

        // Validate and build YearGrant list
        List<YearGrant> yearGrants = new ArrayList<>();
        double totalDays = 0.0;

        for (Map<String, Object> raw : yearGrantsRaw) {
            int    yr   = Integer.parseInt(raw.get("previousYear").toString());
            double days = Double.parseDouble(raw.get("daysToGrant").toString());

            if (days <= 0) {
                result.put("success",false); result.put("message","❌ Days must be > 0 for year "+yr); return result;
            }


            if (yr >= currentYear - 1) {
                result.put("success", false);
                result.put("message",
                        "❌ Year " + yr + " is not eligible for emergency leave. " +
                                "Only years up to " + (currentYear - 2) + " are eligible. " +
                                "Year " + (currentYear-1) + " vacation is already included in your " + currentYear + " balance.");
                return result;
            }

            double remaining = getPreviousYearRemaining(employeeEmail, yr);

            final int yearToCheck = yr;
            // Only block on PENDING requests (APPROVED ones already reduced sickUsed)
            double alreadyGranted = emergencyLeaveRepository.findByEmployeeEmail(employeeEmail).stream()
                    .filter(r -> "PENDING_APPROVAL".equals(r.getStatus()) && r.getYearGrants() != null)
                    .flatMap(r -> r.getYearGrants().stream())
                    .filter(g -> g.getPreviousYear() == yearToCheck)
                    .mapToDouble(YearGrant::getDaysToGrant)
                    .sum();

            double netAvailable = remaining >= 0 ? Math.max(0, remaining - alreadyGranted) : Double.MAX_VALUE;

            if (remaining >= 0 && days > netAvailable) {
                result.put("success",false);
                result.put("message","❌ Year "+yr+": cannot grant "+days+" days. Net available: "+netAvailable);
                return result;
            }

            yearGrants.add(new YearGrant(yr, remaining, days));
            totalDays += days;
        }

        String employeeName = employee.getFullName() != null && !employee.getFullName().isBlank() ? employee.getFullName() : employee.getName();
        String adminName    = admin.getFullName()    != null && !admin.getFullName().isBlank()    ? admin.getFullName()    : admin.getName();
        String officerName  = approvalOfficer.getFullName() != null && !approvalOfficer.getFullName().isBlank() ? approvalOfficer.getFullName() : approvalOfficer.getName();

        EmergencyLeaveRequest req = new EmergencyLeaveRequest();
        req.setEmployeeEmail(employeeEmail);
        req.setEmployeeName(employeeName);
        req.setRequestedByAdminEmail(adminEmail);
        req.setRequestedByAdminName(adminName);
        req.setYearGrants(yearGrants);
        req.setTotalDaysToGrant(totalDays);
        req.setReason(reason);
        req.setApprovalOfficerEmail(approvalOfficerEmail);
        req.setApprovalOfficerName(officerName);
        req.setStatus("PENDING_APPROVAL");
        req.setTargetYear(currentYear);
        req.setCreatedAt(LocalDateTime.now());

        emergencyLeaveRepository.save(req);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < yearGrants.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append(yearGrants.get(i).getPreviousYear()).append(" (").append(yearGrants.get(i).getDaysToGrant()).append("d)");
        }

        result.put("success", true);
        result.put("message", "✅ Emergency leave request submitted. Total: "+totalDays+" days from "+sb);
        result.put("requestId", req.getId());
        result.put("totalDaysToGrant", totalDays);
        result.put("yearCount", yearGrants.size());
        return result;
    }

    // =========================================================================
    // APPROVE — applies total days to current year SICK entitlement
    // =========================================================================
    public Map<String, Object> approveRequest(String requestId, String officerEmail, String comments) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<EmergencyLeaveRequest> opt = emergencyLeaveRepository.findById(requestId);
        if (opt.isEmpty()) { result.put("success",false); result.put("message","❌ Request not found"); return result; }
        EmergencyLeaveRequest req = opt.get();
        if (!req.getApprovalOfficerEmail().equalsIgnoreCase(officerEmail)) { result.put("success",false); result.put("message","❌ Not authorized"); return result; }
        if (!"PENDING_APPROVAL".equals(req.getStatus())) { result.put("success",false); result.put("message","❌ Already processed"); return result; }

        req.setStatus("APPROVED");
        req.setApprovalOfficerComments(comments);
        req.setApprovalOfficerActionAt(LocalDateTime.now());
        applyAllYearGrantsToEntitlement(req);
        emergencyLeaveRepository.save(req);

        result.put("success", true);
        result.put("message", "✅ Approved. "+req.getTotalDaysToGrant()+" vacation days added to "+req.getEmployeeName()+"'s "+req.getTargetYear()+" balance.");
        return result;
    }

    // =========================================================================
    // REJECT
    // =========================================================================
    public Map<String, Object> rejectRequest(String requestId, String officerEmail, String comments) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<EmergencyLeaveRequest> opt = emergencyLeaveRepository.findById(requestId);
        if (opt.isEmpty()) { result.put("success",false); result.put("message","❌ Request not found"); return result; }
        EmergencyLeaveRequest req = opt.get();
        if (!req.getApprovalOfficerEmail().equalsIgnoreCase(officerEmail)) { result.put("success",false); result.put("message","❌ Not authorized"); return result; }
        if (!"PENDING_APPROVAL".equals(req.getStatus())) { result.put("success",false); result.put("message","❌ Already processed"); return result; }

        req.setStatus("REJECTED");
        req.setApprovalOfficerComments(comments);
        req.setApprovalOfficerActionAt(LocalDateTime.now());
        emergencyLeaveRepository.save(req);

        result.put("success", true); result.put("message","✅ Emergency leave request rejected.");
        return result;
    }

    // =========================================================================
    // Apply total days to current year SICK entitlement
    // AND reduce each previous year's remaining count in HistoricalLeaveSummary
    // =========================================================================
    private void applyAllYearGrantsToEntitlement(EmergencyLeaveRequest req) {
        String email     = req.getEmployeeEmail();
        int    year      = req.getTargetYear();
        double totalDays = req.getTotalDaysToGrant();

        // ── Step 1: Add totalDays to current year SICK entitlement ──────────
        List<LeaveEntitlement> ents = leaveEntitlementRepository.findByEmployeeEmail(email);
        LeaveEntitlement sickEnt = ents.stream()
                .filter(e -> "SICK".equals(e.getLeaveType()) && e.getYear() == year)
                .findFirst().orElse(null);

        if (sickEnt != null) {
            // Emergency leave: ONLY add to remainingDays (temporary credit)
            // Do NOT change totalEntitlement or carryOverDays
            // Those fields represent the annual base+carryover entitlement only
            double newRemaining = sickEnt.getRemainingDays() + totalDays;
            sickEnt.setRemainingDays(newRemaining);
            leaveEntitlementRepository.save(sickEnt);
            logger.info("Emergency grant applied: +{} days remaining to {} year {} SICK. Remaining now={}",
                    totalDays, email, year, newRemaining);
        } else {
            // No entitlement record yet — create minimal one with just remaining days
            // totalEntitlement stays at 0 to show this is an emergency-only grant
            LeaveEntitlement newEnt = new LeaveEntitlement(email, "SICK", 0, year);
            newEnt.setRemainingDays(totalDays);
            newEnt.setCarryOverDays(0);
            leaveEntitlementRepository.save(newEnt);
            logger.info("Emergency grant: added {} remaining days for {} year {} (no prior entitlement)",
                    totalDays, email, year);
        }

        // ── Step 2: For each year grant, update HistoricalLeaveSummary ──────
        // Increase sickUsed so that remaining count shows correctly
        if (req.getYearGrants() != null) {
            for (YearGrant grant : req.getYearGrants()) {
                int    prevYear  = grant.getPreviousYear();
                double grantDays = grant.getDaysToGrant();

                // Try HistoricalLeaveSummary first
                List<HistoricalLeaveSummary> summaries =
                        historicalLeaveSummaryRepository.findByEmployeeEmail(email);
                Optional<HistoricalLeaveSummary> histOpt = summaries.stream()
                        .filter(h -> h.getYear() == prevYear)
                        .findFirst();

                if (histOpt.isPresent()) {
                    HistoricalLeaveSummary hist = histOpt.get();
                    double currentUsed  = hist.getSickUsed();
                    double currentTotal = hist.getSickTotal() > 0 ? hist.getSickTotal() : 24;
                    double newUsed      = Math.min(currentTotal, currentUsed + grantDays);
                    hist.setSickUsed(newUsed);
                    historicalLeaveSummaryRepository.save(hist);
                    logger.info("HistoricalLeaveSummary updated: year={} sickUsed {} → {} for {}",
                            prevYear, currentUsed, newUsed, email);
                } else {
                    // Fallback: update LeaveEntitlement remainingDays for that year
                    List<LeaveEntitlement> prevEnts = leaveEntitlementRepository.findByEmployeeEmail(email);
                    Optional<LeaveEntitlement> prevEnt = prevEnts.stream()
                            .filter(e -> "SICK".equals(e.getLeaveType()) && e.getYear() == prevYear)
                            .findFirst();
                    if (prevEnt.isPresent()) {
                        LeaveEntitlement pe = prevEnt.get();
                        double newRemaining = Math.max(0, pe.getRemainingDays() - grantDays);
                        pe.setRemainingDays(newRemaining);
                        leaveEntitlementRepository.save(pe);
                        logger.info("LeaveEntitlement updated: year={} remaining {} → {} for {}",
                                prevYear, pe.getRemainingDays() + grantDays, newRemaining, email);
                    } else {
                        logger.warn("No HistoricalLeaveSummary or LeaveEntitlement found for year={} email={}",
                                prevYear, email);
                    }
                }
            }
        }

        req.setApplied(true);
        req.setAppliedAt(LocalDateTime.now());
    }

    // =========================================================================
    // Previous year remaining (for historical years only)
    // =========================================================================
    public double getPreviousYearRemaining(String employeeEmail, int year) {
        List<HistoricalLeaveSummary> summaries = historicalLeaveSummaryRepository.findByEmployeeEmail(employeeEmail);
        Optional<HistoricalLeaveSummary> hist = summaries.stream().filter(h -> h.getYear() == year).findFirst();
        if (hist.isPresent()) {
            HistoricalLeaveSummary h = hist.get();
            return Math.max(0, (h.getSickTotal() > 0 ? h.getSickTotal() : 24) - h.getSickUsed());
        }
        List<LeaveEntitlement> ents = leaveEntitlementRepository.findByEmployeeEmail(employeeEmail);
        Optional<LeaveEntitlement> ent = ents.stream()
                .filter(e -> "SICK".equals(e.getLeaveType()) && e.getYear() == year)
                .findFirst();
        return ent.map(e -> Math.max(0, e.getRemainingDays())).orElse(-1.0);
    }

    // =========================================================================
    // ALL eligible previous years breakdown
    // RULE: start from currentYear-2 (skip currentYear-1 = already in current balance)
    //       also returns current year remaining so frontend can show eligibility
    // =========================================================================
    public Map<String, Object> getAllPreviousYearsRemaining(String employeeEmail) {
        int currentYear = LocalDate.now().getYear();
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> yearBreakdown = new ArrayList<>();
        double grandTotal = 0.0;

        // Current year vacation remaining — for eligibility check
        double currentYearRemaining = getCurrentYearVacationRemaining(employeeEmail);
        result.put("currentYearRemaining", currentYearRemaining);
        result.put("currentYear",          currentYear);
        result.put("eligible",             currentYearRemaining <= 0);
        result.put("notEligibleReason",
                currentYearRemaining > 0
                        ? "Employee still has " + currentYearRemaining + " vacation days in " + currentYear +
                        ". Emergency leave can only be requested after " + currentYear + " vacation is fully used."
                        : null);

        // Start from currentYear-2 (skip currentYear-1: already merged into current year balance)
        for (int yr = currentYear - 2; yr >= currentYear - 6; yr--) {
            double remaining = getPreviousYearRemaining(employeeEmail, yr);
            if (remaining < 0) break; // no data for this year, stop

            final int yearToCheck = yr;
            // Only count PENDING requests as "alreadyGranted" for display purposes.
            // APPROVED requests already reduced sickUsed in HistoricalLeaveSummary,
            // so getPreviousYearRemaining() already reflects those deductions.
            double alreadyGranted = emergencyLeaveRepository.findByEmployeeEmail(employeeEmail).stream()
                    .filter(r -> "PENDING_APPROVAL".equals(r.getStatus()) && r.getYearGrants() != null)
                    .flatMap(r -> r.getYearGrants().stream())
                    .filter(g -> g.getPreviousYear() == yearToCheck)
                    .mapToDouble(YearGrant::getDaysToGrant)
                    .sum();

            double netRemaining = Math.max(0, remaining - alreadyGranted);

            Map<String, Object> yearData = new LinkedHashMap<>();
            yearData.put("year",           yr);
            yearData.put("totalRemaining", remaining);
            yearData.put("alreadyGranted", alreadyGranted);
            yearData.put("netAvailable",   netRemaining);
            yearData.put("hasData",        true);

            yearBreakdown.add(yearData);
            grandTotal += netRemaining;

            logger.info("Year {}: remaining={}, alreadyGranted={}, netAvailable={} for {}",
                    yr, remaining, alreadyGranted, netRemaining, employeeEmail);
        }

        result.put("employeeEmail",       employeeEmail);
        result.put("yearBreakdown",       yearBreakdown);
        result.put("grandTotalAvailable", grandTotal);
        result.put("hasAnyData",          !yearBreakdown.isEmpty());
        return result;
    }

    public List<EmergencyLeaveRequest> getAllRequests()                    { return emergencyLeaveRepository.findAllByOrderByCreatedAtDesc(); }
    public List<EmergencyLeaveRequest> getPendingForOfficer(String email)  { return emergencyLeaveRepository.findByApprovalOfficerEmailAndStatus(email,"PENDING_APPROVAL"); }
    public List<EmergencyLeaveRequest> getByEmployee(String email)         { return emergencyLeaveRepository.findByEmployeeEmail(email); }
}