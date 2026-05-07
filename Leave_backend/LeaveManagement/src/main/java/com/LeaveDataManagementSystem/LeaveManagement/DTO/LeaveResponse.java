package com.LeaveDataManagementSystem.LeaveManagement.DTO;

import com.LeaveDataManagementSystem.LeaveManagement.Model.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class LeaveResponse {
    private String id;
    private String employeeEmail;
    private String employeeName;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

    // ✅ NEW: Working days information
    private int workingDays;
    private int totalDays;
    private int weekendDays;
    private int publicHolidays;
    private String durationDisplay;

    private boolean isShortLeave;
    private boolean isHalfDay;
    private String halfDayPeriod;
    private LocalTime shortLeaveStartTime;
    private LocalTime shortLeaveEndTime;

    private boolean isMaternityLeave;
    private String maternityLeaveType;
    private boolean isMaternityEndDateSet;
    private String maternityAdditionalDetails;

    private String actingOfficerEmail;
    private String actingOfficerName;
    private String supervisingOfficerEmail;
    private String supervisingOfficerName;
    private String approvalOfficerEmail;
    private String approvalOfficerName;

    private LeaveStatus status;
    private ActingOfficerStatus actingOfficerStatus;
    private SupervisingOfficerStatus supervisingOfficerStatus;
    private ApprovalOfficerStatus approvalOfficerStatus;

    private String actingOfficerComments;
    private String supervisingOfficerComments;
    private String approvalOfficerComments;

    private LocalDateTime actingOfficerApprovedAt;
    private LocalDateTime supervisingOfficerApprovedAt;
    private LocalDateTime approvalOfficerApprovedAt;

    private boolean isCancelled;
    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private String cancellationReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public LeaveResponse() {}

    // ✅ UPDATED: Constructor that includes working days information
    public LeaveResponse(Leave leave) {
        this.id = leave.getId();
        this.employeeEmail = leave.getEmployeeEmail();
        this.employeeName = leave.getEmployeeName();
        this.leaveType = leave.getLeaveType();
        this.startDate = leave.getStartDate();
        this.endDate = leave.getEndDate();
        this.reason = leave.getReason();

        // ✅ Map working days fields
        this.workingDays = leave.getWorkingDays();
        this.totalDays = leave.getTotalDays();
        this.weekendDays = leave.getWeekendDays();
        this.publicHolidays = leave.getPublicHolidays();
        this.durationDisplay = leave.getDurationDisplay();

        this.isShortLeave = leave.isShortLeave();
        this.isHalfDay = leave.isHalfDay();
        this.halfDayPeriod = leave.getHalfDayPeriod();
        this.shortLeaveStartTime = leave.getShortLeaveStartTime();
        this.shortLeaveEndTime = leave.getShortLeaveEndTime();

        this.isMaternityLeave = leave.isMaternityLeave();
        this.maternityLeaveType = leave.getMaternityLeaveType();
        this.isMaternityEndDateSet = leave.isMaternityEndDateSet();
        this.maternityAdditionalDetails = leave.getMaternityAdditionalDetails();

        this.actingOfficerEmail = leave.getActingOfficerEmail();
        this.actingOfficerName = leave.getActingOfficerName();
        this.supervisingOfficerEmail = leave.getSupervisingOfficerEmail();
        this.supervisingOfficerName = leave.getSupervisingOfficerName();
        this.approvalOfficerEmail = leave.getApprovalOfficerEmail();
        this.approvalOfficerName = leave.getApprovalOfficerName();

        this.status = leave.getStatus();
        this.actingOfficerStatus = leave.getActingOfficerStatus();
        this.supervisingOfficerStatus = leave.getSupervisingOfficerStatus();
        this.approvalOfficerStatus = leave.getApprovalOfficerStatus();

        this.actingOfficerComments = leave.getActingOfficerComments();
        this.supervisingOfficerComments = leave.getSupervisingOfficerComments();
        this.approvalOfficerComments = leave.getApprovalOfficerComments();

        this.actingOfficerApprovedAt = leave.getActingOfficerApprovedAt();
        this.supervisingOfficerApprovedAt = leave.getSupervisingOfficerApprovedAt();
        this.approvalOfficerApprovedAt = leave.getApprovalOfficerApprovedAt();

        this.isCancelled = leave.isCancelled();
        this.cancelledAt = leave.getCancelledAt();
        this.cancelledBy = leave.getCancelledBy();
        this.cancellationReason = leave.getCancellationReason();

        this.createdAt = leave.getCreatedAt();
        this.updatedAt = leave.getUpdatedAt();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    // ✅ NEW: Working days getters/setters
    public int getWorkingDays() { return workingDays; }
    public void setWorkingDays(int workingDays) { this.workingDays = workingDays; }

    public int getTotalDays() { return totalDays; }
    public void setTotalDays(int totalDays) { this.totalDays = totalDays; }

    public int getWeekendDays() { return weekendDays; }
    public void setWeekendDays(int weekendDays) { this.weekendDays = weekendDays; }

    public int getPublicHolidays() { return publicHolidays; }
    public void setPublicHolidays(int publicHolidays) { this.publicHolidays = publicHolidays; }

    public String getDurationDisplay() { return durationDisplay; }
    public void setDurationDisplay(String durationDisplay) { this.durationDisplay = durationDisplay; }

    public boolean isShortLeave() { return isShortLeave; }
    public void setShortLeave(boolean shortLeave) { isShortLeave = shortLeave; }

    public boolean isHalfDay() { return isHalfDay; }
    public void setHalfDay(boolean halfDay) { isHalfDay = halfDay; }

    public String getHalfDayPeriod() { return halfDayPeriod; }
    public void setHalfDayPeriod(String halfDayPeriod) { this.halfDayPeriod = halfDayPeriod; }

    public LocalTime getShortLeaveStartTime() { return shortLeaveStartTime; }
    public void setShortLeaveStartTime(LocalTime shortLeaveStartTime) { this.shortLeaveStartTime = shortLeaveStartTime; }

    public LocalTime getShortLeaveEndTime() { return shortLeaveEndTime; }
    public void setShortLeaveEndTime(LocalTime shortLeaveEndTime) { this.shortLeaveEndTime = shortLeaveEndTime; }

    public boolean isMaternityLeave() { return isMaternityLeave; }
    public void setMaternityLeave(boolean maternityLeave) { isMaternityLeave = maternityLeave; }

    public String getMaternityLeaveType() { return maternityLeaveType; }
    public void setMaternityLeaveType(String maternityLeaveType) { this.maternityLeaveType = maternityLeaveType; }

    public boolean isMaternityEndDateSet() { return isMaternityEndDateSet; }
    public void setMaternityEndDateSet(boolean maternityEndDateSet) { this.isMaternityEndDateSet = maternityEndDateSet; }

    public String getMaternityAdditionalDetails() { return maternityAdditionalDetails; }
    public void setMaternityAdditionalDetails(String maternityAdditionalDetails) {
        this.maternityAdditionalDetails = maternityAdditionalDetails;
    }

    public String getActingOfficerEmail() { return actingOfficerEmail; }
    public void setActingOfficerEmail(String actingOfficerEmail) { this.actingOfficerEmail = actingOfficerEmail; }

    public String getActingOfficerName() { return actingOfficerName; }
    public void setActingOfficerName(String actingOfficerName) { this.actingOfficerName = actingOfficerName; }

    public String getSupervisingOfficerEmail() { return supervisingOfficerEmail; }
    public void setSupervisingOfficerEmail(String supervisingOfficerEmail) {
        this.supervisingOfficerEmail = supervisingOfficerEmail;
    }

    public String getSupervisingOfficerName() { return supervisingOfficerName; }
    public void setSupervisingOfficerName(String supervisingOfficerName) {
        this.supervisingOfficerName = supervisingOfficerName;
    }

    public String getApprovalOfficerEmail() { return approvalOfficerEmail; }
    public void setApprovalOfficerEmail(String approvalOfficerEmail) {
        this.approvalOfficerEmail = approvalOfficerEmail;
    }

    public String getApprovalOfficerName() { return approvalOfficerName; }
    public void setApprovalOfficerName(String approvalOfficerName) {
        this.approvalOfficerName = approvalOfficerName;
    }

    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }

    public ActingOfficerStatus getActingOfficerStatus() { return actingOfficerStatus; }
    public void setActingOfficerStatus(ActingOfficerStatus actingOfficerStatus) {
        this.actingOfficerStatus = actingOfficerStatus;
    }

    public SupervisingOfficerStatus getSupervisingOfficerStatus() { return supervisingOfficerStatus; }
    public void setSupervisingOfficerStatus(SupervisingOfficerStatus supervisingOfficerStatus) {
        this.supervisingOfficerStatus = supervisingOfficerStatus;
    }

    public ApprovalOfficerStatus getApprovalOfficerStatus() { return approvalOfficerStatus; }
    public void setApprovalOfficerStatus(ApprovalOfficerStatus approvalOfficerStatus) {
        this.approvalOfficerStatus = approvalOfficerStatus;
    }

    public String getActingOfficerComments() { return actingOfficerComments; }
    public void setActingOfficerComments(String actingOfficerComments) {
        this.actingOfficerComments = actingOfficerComments;
    }

    public String getSupervisingOfficerComments() { return supervisingOfficerComments; }
    public void setSupervisingOfficerComments(String supervisingOfficerComments) {
        this.supervisingOfficerComments = supervisingOfficerComments;
    }

    public String getApprovalOfficerComments() { return approvalOfficerComments; }
    public void setApprovalOfficerComments(String approvalOfficerComments) {
        this.approvalOfficerComments = approvalOfficerComments;
    }

    public LocalDateTime getActingOfficerApprovedAt() { return actingOfficerApprovedAt; }
    public void setActingOfficerApprovedAt(LocalDateTime actingOfficerApprovedAt) {
        this.actingOfficerApprovedAt = actingOfficerApprovedAt;
    }

    public LocalDateTime getSupervisingOfficerApprovedAt() { return supervisingOfficerApprovedAt; }
    public void setSupervisingOfficerApprovedAt(LocalDateTime supervisingOfficerApprovedAt) {
        this.supervisingOfficerApprovedAt = supervisingOfficerApprovedAt;
    }

    public LocalDateTime getApprovalOfficerApprovedAt() { return approvalOfficerApprovedAt; }
    public void setApprovalOfficerApprovedAt(LocalDateTime approvalOfficerApprovedAt) {
        this.approvalOfficerApprovedAt = approvalOfficerApprovedAt;
    }

    public boolean isCancelled() { return isCancelled; }
    public void setCancelled(boolean cancelled) { isCancelled = cancelled; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}