package com.LeaveDataManagementSystem.LeaveManagement.DTO;

import com.LeaveDataManagementSystem.LeaveManagement.Model.LeaveStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public class LeaveRequest {
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private String actingOfficerEmail;
    private String supervisingOfficerEmail;
    private String approvalOfficerEmail;

    private String halfDayPeriod;
    private LocalTime startTime;
    private LocalTime endTime;

    // ── NEW: Half day specific times ──────────────────────────────────────
    private LocalTime halfDayStartTime;
    private LocalTime halfDayEndTime;

    private String maternityLeaveType;  // "FULL_PAY", "HALF_PAY", "NO_PAY"

    // Default constructor
    public LeaveRequest() {}

    // Regular leave constructor
    public LeaveRequest(String leaveType, LocalDate startDate, LocalDate endDate,
                        String reason, String actingOfficerEmail, String supervisingOfficerEmail,
                        String approvalOfficerEmail) {
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.actingOfficerEmail = actingOfficerEmail;
        this.supervisingOfficerEmail = supervisingOfficerEmail;
        this.approvalOfficerEmail = approvalOfficerEmail;
    }

    // Half-day leave constructor (updated with times)
    public LeaveRequest(String leaveType, LocalDate date, String halfDayPeriod,
                        LocalTime halfDayStartTime, LocalTime halfDayEndTime,
                        String reason, String actingOfficerEmail, String supervisingOfficerEmail,
                        String approvalOfficerEmail) {
        this.leaveType = leaveType;
        this.startDate = date;
        this.endDate = date;
        this.halfDayPeriod = halfDayPeriod;
        this.halfDayStartTime = halfDayStartTime;
        this.halfDayEndTime = halfDayEndTime;
        this.reason = reason;
        this.actingOfficerEmail = actingOfficerEmail;
        this.supervisingOfficerEmail = supervisingOfficerEmail;
        this.approvalOfficerEmail = approvalOfficerEmail;
    }

    // Short leave constructor
    public LeaveRequest(LocalDate date, LocalTime startTime, LocalTime endTime,
                        String reason, String actingOfficerEmail, String supervisingOfficerEmail,
                        String approvalOfficerEmail) {
        this.leaveType = "SHORT";
        this.startDate = date;
        this.endDate = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
        this.actingOfficerEmail = actingOfficerEmail;
        this.supervisingOfficerEmail = supervisingOfficerEmail;
        this.approvalOfficerEmail = approvalOfficerEmail;
    }

    // Maternity leave constructor
    public LeaveRequest(LocalDate startDate, String maternityLeaveType, String reason,
                        String actingOfficerEmail, String supervisingOfficerEmail,
                        String approvalOfficerEmail) {
        this.leaveType = "MATERNITY";
        this.startDate = startDate;
        this.endDate = null;
        this.maternityLeaveType = maternityLeaveType;
        this.reason = reason;
        this.actingOfficerEmail = actingOfficerEmail;
        this.supervisingOfficerEmail = supervisingOfficerEmail;
        this.approvalOfficerEmail = approvalOfficerEmail;
    }

    // ── Helper methods ────────────────────────────────────────────────────

    public boolean hasActingOfficer() {
        return actingOfficerEmail != null &&
                !actingOfficerEmail.trim().isEmpty() &&
                !"NONE".equalsIgnoreCase(actingOfficerEmail);
    }

    public boolean hasSupervisingOfficer() {
        return supervisingOfficerEmail != null &&
                !supervisingOfficerEmail.trim().isEmpty() &&
                !"NONE".equalsIgnoreCase(supervisingOfficerEmail);
    }

    public boolean hasApprovalOfficer() {
        return approvalOfficerEmail != null &&
                !approvalOfficerEmail.trim().isEmpty() &&
                !"NONE".equalsIgnoreCase(approvalOfficerEmail);
    }

    public boolean isMaternityLeave() {
        return "MATERNITY".equalsIgnoreCase(this.leaveType);
    }

    public LeaveStatus getInitialWorkflowStatus() {
        if (hasActingOfficer())      return LeaveStatus.PENDING_ACTING_OFFICER;
        if (hasSupervisingOfficer()) return LeaveStatus.PENDING_SUPERVISING_OFFICER;
        if (hasApprovalOfficer())    return LeaveStatus.PENDING_APPROVAL_OFFICER;
        return LeaveStatus.APPROVED;
    }

    public LeaveStatus getNextWorkflowStatus(LeaveStatus currentStatus) {
        switch (currentStatus) {
            case PENDING_ACTING_OFFICER:
                if (hasSupervisingOfficer()) return LeaveStatus.PENDING_SUPERVISING_OFFICER;
                if (hasApprovalOfficer())    return LeaveStatus.PENDING_APPROVAL_OFFICER;
                return LeaveStatus.APPROVED;
            case PENDING_SUPERVISING_OFFICER:
                if (hasApprovalOfficer())    return LeaveStatus.PENDING_APPROVAL_OFFICER;
                return LeaveStatus.APPROVED;
            case PENDING_APPROVAL_OFFICER:
                return LeaveStatus.APPROVED;
            default:
                return LeaveStatus.APPROVED;
        }
    }

    // ── Getters & Setters ─────────────────────────────────────────────────

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getActingOfficerEmail() { return actingOfficerEmail; }
    public void setActingOfficerEmail(String actingOfficerEmail) {
        this.actingOfficerEmail = "NONE".equalsIgnoreCase(actingOfficerEmail) ? null : actingOfficerEmail;
    }

    public String getSupervisingOfficerEmail() { return supervisingOfficerEmail; }
    public void setSupervisingOfficerEmail(String supervisingOfficerEmail) {
        this.supervisingOfficerEmail = "NONE".equalsIgnoreCase(supervisingOfficerEmail) ? null : supervisingOfficerEmail;
    }

    public String getApprovalOfficerEmail() { return approvalOfficerEmail; }
    public void setApprovalOfficerEmail(String approvalOfficerEmail) { this.approvalOfficerEmail = approvalOfficerEmail; }

    public String getHalfDayPeriod() { return halfDayPeriod; }
    public void setHalfDayPeriod(String halfDayPeriod) { this.halfDayPeriod = halfDayPeriod; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    // ── NEW: Half day time getters/setters ────────────────────────────────
    public LocalTime getHalfDayStartTime() { return halfDayStartTime; }
    public void setHalfDayStartTime(LocalTime halfDayStartTime) { this.halfDayStartTime = halfDayStartTime; }

    public LocalTime getHalfDayEndTime() { return halfDayEndTime; }
    public void setHalfDayEndTime(LocalTime halfDayEndTime) { this.halfDayEndTime = halfDayEndTime; }

    public String getMaternityLeaveType() { return maternityLeaveType; }
    public void setMaternityLeaveType(String maternityLeaveType) { this.maternityLeaveType = maternityLeaveType; }
}