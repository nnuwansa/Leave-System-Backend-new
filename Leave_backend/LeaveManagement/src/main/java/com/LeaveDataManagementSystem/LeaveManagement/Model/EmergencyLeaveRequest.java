//
//package com.LeaveDataManagementSystem.LeaveManagement.Model;
//
//import org.springframework.data.annotation.CreatedDate;
//import org.springframework.data.annotation.Id;
//import org.springframework.data.mongodb.core.mapping.Document;
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Document(collection = "Emergency Leave Requests")
//public class EmergencyLeaveRequest {
//
//    @Id
//    private String id;
//
//    // ── Employee info ─────────────────────────────────────────────────────────
//    private String employeeEmail;
//    private String employeeName;
//
//    // ── Admin who created this request ────────────────────────────────────────
//    private String requestedByAdminEmail;
//    private String requestedByAdminName;
//
//    // ── Multi-year grants (NEW) ───────────────────────────────────────────────
//    // Each YearGrant holds: previousYear, remainingDays, daysToGrant
//    private List<YearGrant> yearGrants;
//
//    // ── Total days across all years ───────────────────────────────────────────
//    private double totalDaysToGrant;
//
//    // ── Reason ───────────────────────────────────────────────────────────────
//    private String reason;
//
//    // ── Approval officer ─────────────────────────────────────────────────────
//    private String approvalOfficerEmail;
//    private String approvalOfficerName;
//
//    // ── Status: PENDING_APPROVAL → APPROVED / REJECTED ───────────────────────
//    private String status = "PENDING_APPROVAL";
//
//    private String approvalOfficerComments;
//    private LocalDateTime approvalOfficerActionAt;
//
//    // ── Applied: set true after vacation balance is updated ──────────────────
//    private boolean applied = false;
//    private LocalDateTime appliedAt;
//
//    // ── Target year: which year's SICK entitlement gets increased ─────────────
//    private int targetYear;   // current year (e.g. 2026)
//
//    @CreatedDate
//    private LocalDateTime createdAt;
//
//    // ════════════════════════════════════════════════════════════════════════
//    // Inner class: YearGrant — one entry per previous year selected
//    // ════════════════════════════════════════════════════════════════════════
//    public static class YearGrant {
//        private int    previousYear;
//        private double previousYearRemainingDays;
//        private double daysToGrant;
//
//        public YearGrant() {}
//
//        public YearGrant(int previousYear, double previousYearRemainingDays, double daysToGrant) {
//            this.previousYear               = previousYear;
//            this.previousYearRemainingDays  = previousYearRemainingDays;
//            this.daysToGrant                = daysToGrant;
//        }
//
//        public int    getPreviousYear()                     { return previousYear; }
//        public void   setPreviousYear(int v)                { this.previousYear = v; }
//
//        public double getPreviousYearRemainingDays()        { return previousYearRemainingDays; }
//        public void   setPreviousYearRemainingDays(double v){ this.previousYearRemainingDays = v; }
//
//        public double getDaysToGrant()                      { return daysToGrant; }
//        public void   setDaysToGrant(double v)              { this.daysToGrant = v; }
//    }
//
//    // ── Constructors ──────────────────────────────────────────────────────────
//    public EmergencyLeaveRequest() {}
//
//    // ── Getters & Setters ─────────────────────────────────────────────────────
//    public String getId()                           { return id; }
//    public void   setId(String id)                  { this.id = id; }
//
//    public String getEmployeeEmail()                { return employeeEmail; }
//    public void   setEmployeeEmail(String v)        { this.employeeEmail = v; }
//
//    public String getEmployeeName()                 { return employeeName; }
//    public void   setEmployeeName(String v)         { this.employeeName = v; }
//
//    public String getRequestedByAdminEmail()        { return requestedByAdminEmail; }
//    public void   setRequestedByAdminEmail(String v){ this.requestedByAdminEmail = v; }
//
//    public String getRequestedByAdminName()         { return requestedByAdminName; }
//    public void   setRequestedByAdminName(String v) { this.requestedByAdminName = v; }
//
//    public List<YearGrant> getYearGrants()          { return yearGrants; }
//    public void   setYearGrants(List<YearGrant> v)  { this.yearGrants = v; }
//
//    public double getTotalDaysToGrant()             { return totalDaysToGrant; }
//    public void   setTotalDaysToGrant(double v)     { this.totalDaysToGrant = v; }
//
//    public String getReason()                       { return reason; }
//    public void   setReason(String v)               { this.reason = v; }
//
//    public String getApprovalOfficerEmail()         { return approvalOfficerEmail; }
//    public void   setApprovalOfficerEmail(String v) { this.approvalOfficerEmail = v; }
//
//    public String getApprovalOfficerName()          { return approvalOfficerName; }
//    public void   setApprovalOfficerName(String v)  { this.approvalOfficerName = v; }
//
//    public String getStatus()                       { return status; }
//    public void   setStatus(String v)               { this.status = v; }
//
//    public String getApprovalOfficerComments()      { return approvalOfficerComments; }
//    public void   setApprovalOfficerComments(String v){ this.approvalOfficerComments = v; }
//
//    public LocalDateTime getApprovalOfficerActionAt()         { return approvalOfficerActionAt; }
//    public void          setApprovalOfficerActionAt(LocalDateTime v){ this.approvalOfficerActionAt = v; }
//
//    public boolean isApplied()                      { return applied; }
//    public void    setApplied(boolean v)            { this.applied = v; }
//
//    public LocalDateTime getAppliedAt()             { return appliedAt; }
//    public void          setAppliedAt(LocalDateTime v){ this.appliedAt = v; }
//
//    public int    getTargetYear()                   { return targetYear; }
//    public void   setTargetYear(int v)              { this.targetYear = v; }
//
//    public LocalDateTime getCreatedAt()             { return createdAt; }
//    public void          setCreatedAt(LocalDateTime v){ this.createdAt = v; }
//}


package com.LeaveDataManagementSystem.LeaveManagement.Model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "Emergency Leave Requests")
public class EmergencyLeaveRequest {

    @Id
    private String id;

    // ── Employee info ─────────────────────────────────────────────────────────
    // Stable identifier — use this for lookups going forward
    private String userId;
    private String employeeEmail; // display-only now
    private String employeeName;

    // ── Admin who created this request ────────────────────────────────────────
    private String requestedByAdminEmail;
    private String requestedByAdminName;

    // ── Multi-year grants (NEW) ───────────────────────────────────────────────
    // Each YearGrant holds: previousYear, remainingDays, daysToGrant
    private List<YearGrant> yearGrants;

    // ── Total days across all years ───────────────────────────────────────────
    private double totalDaysToGrant;

    // ── Reason ───────────────────────────────────────────────────────────────
    private String reason;

    // ── Approval officer ─────────────────────────────────────────────────────
    private String approvalOfficerEmail;
    private String approvalOfficerName;

    // ── Status: PENDING_APPROVAL → APPROVED / REJECTED ───────────────────────
    private String status = "PENDING_APPROVAL";

    private String approvalOfficerComments;
    private LocalDateTime approvalOfficerActionAt;

    // ── Applied: set true after vacation balance is updated ──────────────────
    private boolean applied = false;
    private LocalDateTime appliedAt;

    // ── Target year: which year's SICK entitlement gets increased ─────────────
    private int targetYear;   // current year (e.g. 2026)

    @CreatedDate
    private LocalDateTime createdAt;

    // ════════════════════════════════════════════════════════════════════════
    // Inner class: YearGrant — one entry per previous year selected
    // ════════════════════════════════════════════════════════════════════════
    public static class YearGrant {
        private int    previousYear;
        private double previousYearRemainingDays;
        private double daysToGrant;

        public YearGrant() {}

        public YearGrant(int previousYear, double previousYearRemainingDays, double daysToGrant) {
            this.previousYear               = previousYear;
            this.previousYearRemainingDays  = previousYearRemainingDays;
            this.daysToGrant                = daysToGrant;
        }

        public int    getPreviousYear()                     { return previousYear; }
        public void   setPreviousYear(int v)                { this.previousYear = v; }

        public double getPreviousYearRemainingDays()        { return previousYearRemainingDays; }
        public void   setPreviousYearRemainingDays(double v){ this.previousYearRemainingDays = v; }

        public double getDaysToGrant()                      { return daysToGrant; }
        public void   setDaysToGrant(double v)              { this.daysToGrant = v; }
    }

    // ── Constructors ──────────────────────────────────────────────────────────
    public EmergencyLeaveRequest() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public String getId()                           { return id; }
    public void   setId(String id)                  { this.id = id; }

    public String getUserId()                       { return userId; }
    public void   setUserId(String v)               { this.userId = v; }

    public String getEmployeeEmail()                { return employeeEmail; }
    public void   setEmployeeEmail(String v)        { this.employeeEmail = v; }

    public String getEmployeeName()                 { return employeeName; }
    public void   setEmployeeName(String v)         { this.employeeName = v; }

    public String getRequestedByAdminEmail()        { return requestedByAdminEmail; }
    public void   setRequestedByAdminEmail(String v){ this.requestedByAdminEmail = v; }

    public String getRequestedByAdminName()         { return requestedByAdminName; }
    public void   setRequestedByAdminName(String v) { this.requestedByAdminName = v; }

    public List<YearGrant> getYearGrants()          { return yearGrants; }
    public void   setYearGrants(List<YearGrant> v)  { this.yearGrants = v; }

    public double getTotalDaysToGrant()             { return totalDaysToGrant; }
    public void   setTotalDaysToGrant(double v)     { this.totalDaysToGrant = v; }

    public String getReason()                       { return reason; }
    public void   setReason(String v)               { this.reason = v; }

    public String getApprovalOfficerEmail()         { return approvalOfficerEmail; }
    public void   setApprovalOfficerEmail(String v) { this.approvalOfficerEmail = v; }

    public String getApprovalOfficerName()          { return approvalOfficerName; }
    public void   setApprovalOfficerName(String v)  { this.approvalOfficerName = v; }

    public String getStatus()                       { return status; }
    public void   setStatus(String v)               { this.status = v; }

    public String getApprovalOfficerComments()      { return approvalOfficerComments; }
    public void   setApprovalOfficerComments(String v){ this.approvalOfficerComments = v; }

    public LocalDateTime getApprovalOfficerActionAt()         { return approvalOfficerActionAt; }
    public void          setApprovalOfficerActionAt(LocalDateTime v){ this.approvalOfficerActionAt = v; }

    public boolean isApplied()                      { return applied; }
    public void    setApplied(boolean v)            { this.applied = v; }

    public LocalDateTime getAppliedAt()             { return appliedAt; }
    public void          setAppliedAt(LocalDateTime v){ this.appliedAt = v; }

    public int    getTargetYear()                   { return targetYear; }
    public void   setTargetYear(int v)              { this.targetYear = v; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void          setCreatedAt(LocalDateTime v){ this.createdAt = v; }
}