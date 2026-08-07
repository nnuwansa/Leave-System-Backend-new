//package com.LeaveDataManagementSystem.LeaveManagement.Model;
//
//import org.springframework.data.annotation.CreatedDate;
//import org.springframework.data.annotation.Id;
//import org.springframework.data.annotation.LastModifiedDate;
//import org.springframework.data.mongodb.core.mapping.Document;
//import java.time.LocalDateTime;
//
//@Document(collection = "Leave Entitlements")
//public class LeaveEntitlement {
//    @Id
//    private String id;
//
//    private String employeeEmail;
//    private String leaveType;
//    private int totalEntitlement; // -1 for unlimited (DUTY leave)
//    private double usedDays;
//    private double remainingDays; // -1 for unlimited
//    private int year;
//
//    // Track accumulated half days
//    private int accumulatedHalfDays = 0;
//
//    // ── carry-over from previous year (SICK/Vacation only) ──────────
//    private double carryOverDays = 0.0;
//
//    // ── track which leave type was used for a half-day deduction ─────
//    private String halfDayDeductedFrom = null; // "CASUAL" or "SICK"
//
//    // ── Monthly usage breakdown — auto-updated on leave approval/revert ─
//    // Structure: { "July": { "SICK": { "days": 2.0, "dates": ["2026-07-02 ~ 2026-07-03 (2d)"] } } }
//    private java.util.Map<String, java.util.Map<String, Object>> monthlyUsage = new java.util.LinkedHashMap<>();
//
//    // Year total from leave records
//    private double yearTotalUsed = 0.0;
//
//    // When monthlyUsage was last recalculated
//    private java.time.LocalDateTime monthlyUsageUpdatedAt;
//
//    @CreatedDate
//    private LocalDateTime createdAt;
//
//    @LastModifiedDate
//    private LocalDateTime updatedAt;
//
//    // Constructors
//    public LeaveEntitlement() {}
//
//    public LeaveEntitlement(String employeeEmail, String leaveType, int totalEntitlement, int year) {
//        this.employeeEmail = employeeEmail;
//        this.leaveType = leaveType;
//        this.totalEntitlement = totalEntitlement;
//        this.usedDays = 0.0;
//        this.remainingDays = totalEntitlement == -1 ? -1.0 : (double) totalEntitlement;
//        this.year = year;
//        this.accumulatedHalfDays = 0;
//        this.carryOverDays = 0.0;
//    }
//
//    // Check if this is an unlimited entitlement (like DUTY leave)
//    public boolean isUnlimited() {
//        return totalEntitlement == -1;
//    }
//
//    // Method to check if sufficient leave is available
//    public boolean hasSufficientLeave(double requestedDays) {
//        if (isUnlimited()) return true;
//        return remainingDays >= requestedDays;
//    }
//
//    // Method to update used days (supports half days and unlimited leave)
//    public void updateUsedDays(double additionalDays) {
//        this.usedDays += additionalDays;
//        if (!isUnlimited()) {
//            this.remainingDays = this.totalEntitlement - this.usedDays;
//        }
//    }
//
//    // Method to add half day and convert to full day if needed
//    public void addHalfDay() {
//        this.accumulatedHalfDays++;
//        if (this.accumulatedHalfDays >= 2) {
//            this.usedDays += 1.0;
//            this.accumulatedHalfDays -= 2;
//            if (!isUnlimited()) {
//                this.remainingDays = this.totalEntitlement - this.usedDays;
//            }
//        }
//    }
//
//    // Method to remove half day (for reversions)
//    public void removeHalfDay() {
//        if (this.accumulatedHalfDays > 0) {
//            this.accumulatedHalfDays--;
//        } else if (this.usedDays >= 1.0) {
//            this.usedDays -= 1.0;
//            this.accumulatedHalfDays = 1;
//            if (!isUnlimited()) {
//                this.remainingDays = this.totalEntitlement - this.usedDays;
//            }
//        }
//    }
//
//    // Get effective remaining days including half days
//    public double getEffectiveRemainingDays() {
//        if (isUnlimited()) return Double.MAX_VALUE;
//        double effectiveUsed = this.usedDays + (this.accumulatedHalfDays * 0.5);
//        return this.totalEntitlement - effectiveUsed;
//    }
//
//    // Check if can take half day
//    public boolean canTakeHalfDay() {
//        if (isUnlimited()) return true;
//        return getEffectiveRemainingDays() >= 0.5;
//    }
//
//    // Get display string for remaining days
//    public String getRemainingDaysDisplay() {
//        if (isUnlimited()) return "Unlimited";
//        return String.format("%.1f", remainingDays);
//    }
//
//    // Get display string for total entitlement
//    public String getTotalEntitlementDisplay() {
//        if (isUnlimited()) return "Unlimited";
//        return String.valueOf(totalEntitlement);
//    }
//
//    // Getters and Setters
//    public String getId() { return id; }
//    public void setId(String id) { this.id = id; }
//
//    public String getEmployeeEmail() { return employeeEmail; }
//    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }
//
//    public String getLeaveType() { return leaveType; }
//    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }
//
//    public int getTotalEntitlement() { return totalEntitlement; }
//    public void setTotalEntitlement(int totalEntitlement) {
//        this.totalEntitlement = totalEntitlement;
//        if (totalEntitlement == -1) {
//            this.remainingDays = -1.0;
//        } else {
//            this.remainingDays = totalEntitlement - this.usedDays;
//        }
//    }
//
//    public double getUsedDays() { return usedDays; }
//    public void setUsedDays(double usedDays) {
//        this.usedDays = usedDays;
//        // NOTE: does NOT auto-recalculate remainingDays
//        // Callers must explicitly set remainingDays if needed
//        // This avoids double-deduction bugs when both fields are set manually
//    }
//
//    public double getRemainingDays() { return remainingDays; }
//    public void setRemainingDays(double remainingDays) { this.remainingDays = remainingDays; }
//
//    // Use this when approving regular leaves — recalculates remainingDays from totalEntitlement
//    public void setUsedDaysAndRecalculate(double usedDays) {
//        this.usedDays = usedDays;
//        if (!isUnlimited()) {
//            this.remainingDays = this.totalEntitlement - usedDays;
//        }
//    }
//
//    public int getYear() { return year; }
//    public void setYear(int year) { this.year = year; }
//
//    public int getAccumulatedHalfDays() { return accumulatedHalfDays; }
//    public void setAccumulatedHalfDays(int accumulatedHalfDays) { this.accumulatedHalfDays = accumulatedHalfDays; }
//
//    // ── NEW getters/setters ───────────────────────────────────────────
//    public double getCarryOverDays() { return carryOverDays; }
//    public void setCarryOverDays(double carryOverDays) { this.carryOverDays = carryOverDays; }
//
//    public String getHalfDayDeductedFrom() { return halfDayDeductedFrom; }
//    public void setHalfDayDeductedFrom(String halfDayDeductedFrom) { this.halfDayDeductedFrom = halfDayDeductedFrom; }
//
//    public java.util.Map<String, java.util.Map<String, Object>> getMonthlyUsage() { return monthlyUsage; }
//    public void setMonthlyUsage(java.util.Map<String, java.util.Map<String, Object>> monthlyUsage) {
//        this.monthlyUsage = monthlyUsage != null ? monthlyUsage : new java.util.LinkedHashMap<>();
//    }
//
//    public double getYearTotalUsed() { return yearTotalUsed; }
//    public void setYearTotalUsed(double yearTotalUsed) { this.yearTotalUsed = yearTotalUsed; }
//
//    public java.time.LocalDateTime getMonthlyUsageUpdatedAt() { return monthlyUsageUpdatedAt; }
//    public void setMonthlyUsageUpdatedAt(java.time.LocalDateTime v) { this.monthlyUsageUpdatedAt = v; }
//
//    public LocalDateTime getCreatedAt() { return createdAt; }
//    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
//
//    public LocalDateTime getUpdatedAt() { return updatedAt; }
//    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
//
//
//}



package com.LeaveDataManagementSystem.LeaveManagement.Model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "Leave Entitlements")
public class LeaveEntitlement {
    @Id
    private String id;

    // ── STABLE IDENTIFIER ─────────────────────────────────────────────
    // This is the User's Mongo _id (User.getId()). Use THIS for all
    // lookups/joins going forward — it never changes even if the user's
    // email is edited later (e.g. on department transfer).
    private String userId;

    // employeeEmail is now DISPLAY-ONLY / for legacy queries during the
    // migration window. Do not use it as a lookup key in new code.
    private String employeeEmail;
    private String leaveType;
    private int totalEntitlement; // -1 for unlimited (DUTY leave)
    private double usedDays;
    private double remainingDays; // -1 for unlimited
    private int year;

    // Track accumulated half days
    private int accumulatedHalfDays = 0;

    // ── carry-over from previous year (SICK/Vacation only) ──────────
    private double carryOverDays = 0.0;

    // ── track which leave type was used for a half-day deduction ─────
    private String halfDayDeductedFrom = null; // "CASUAL" or "SICK"

    // ── Monthly usage breakdown — auto-updated on leave approval/revert ─
    // Structure: { "July": { "SICK": { "days": 2.0, "dates": ["2026-07-02 ~ 2026-07-03 (2d)"] } } }
    private java.util.Map<String, java.util.Map<String, Object>> monthlyUsage = new java.util.LinkedHashMap<>();

    // Year total from leave records
    private double yearTotalUsed = 0.0;

    // When monthlyUsage was last recalculated
    private java.time.LocalDateTime monthlyUsageUpdatedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Constructors
    public LeaveEntitlement() {}

    public LeaveEntitlement(String employeeEmail, String leaveType, int totalEntitlement, int year) {
        this.employeeEmail = employeeEmail;
        this.leaveType = leaveType;
        this.totalEntitlement = totalEntitlement;
        this.usedDays = 0.0;
        this.remainingDays = totalEntitlement == -1 ? -1.0 : (double) totalEntitlement;
        this.year = year;
        this.accumulatedHalfDays = 0;
        this.carryOverDays = 0.0;
    }

    // ── NEW preferred constructor — always set userId going forward ────
    public LeaveEntitlement(String userId, String employeeEmail, String leaveType, int totalEntitlement, int year) {
        this(employeeEmail, leaveType, totalEntitlement, year);
        this.userId = userId;
    }

    // Check if this is an unlimited entitlement (like DUTY leave)
    public boolean isUnlimited() {
        return totalEntitlement == -1;
    }

    // Method to check if sufficient leave is available
    public boolean hasSufficientLeave(double requestedDays) {
        if (isUnlimited()) return true;
        return remainingDays >= requestedDays;
    }

    // Method to update used days (supports half days and unlimited leave)
    public void updateUsedDays(double additionalDays) {
        this.usedDays += additionalDays;
        if (!isUnlimited()) {
            this.remainingDays = this.totalEntitlement - this.usedDays;
        }
    }

    // Method to add half day and convert to full day if needed
    public void addHalfDay() {
        this.accumulatedHalfDays++;
        if (this.accumulatedHalfDays >= 2) {
            this.usedDays += 1.0;
            this.accumulatedHalfDays -= 2;
            if (!isUnlimited()) {
                this.remainingDays = this.totalEntitlement - this.usedDays;
            }
        }
    }

    // Method to remove half day (for reversions)
    public void removeHalfDay() {
        if (this.accumulatedHalfDays > 0) {
            this.accumulatedHalfDays--;
        } else if (this.usedDays >= 1.0) {
            this.usedDays -= 1.0;
            this.accumulatedHalfDays = 1;
            if (!isUnlimited()) {
                this.remainingDays = this.totalEntitlement - this.usedDays;
            }
        }
    }

    // Get effective remaining days including half days
    public double getEffectiveRemainingDays() {
        if (isUnlimited()) return Double.MAX_VALUE;
        double effectiveUsed = this.usedDays + (this.accumulatedHalfDays * 0.5);
        return this.totalEntitlement - effectiveUsed;
    }

    // Check if can take half day
    public boolean canTakeHalfDay() {
        if (isUnlimited()) return true;
        return getEffectiveRemainingDays() >= 0.5;
    }

    // Get display string for remaining days
    public String getRemainingDaysDisplay() {
        if (isUnlimited()) return "Unlimited";
        return String.format("%.1f", remainingDays);
    }

    // Get display string for total entitlement
    public String getTotalEntitlementDisplay() {
        if (isUnlimited()) return "Unlimited";
        return String.valueOf(totalEntitlement);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public int getTotalEntitlement() { return totalEntitlement; }
    public void setTotalEntitlement(int totalEntitlement) {
        this.totalEntitlement = totalEntitlement;
        if (totalEntitlement == -1) {
            this.remainingDays = -1.0;
        } else {
            this.remainingDays = totalEntitlement - this.usedDays;
        }
    }

    public double getUsedDays() { return usedDays; }
    public void setUsedDays(double usedDays) {
        this.usedDays = usedDays;
        // NOTE: does NOT auto-recalculate remainingDays
        // Callers must explicitly set remainingDays if needed
        // This avoids double-deduction bugs when both fields are set manually
    }

    public double getRemainingDays() { return remainingDays; }
    public void setRemainingDays(double remainingDays) { this.remainingDays = remainingDays; }

    // Use this when approving regular leaves — recalculates remainingDays from totalEntitlement
    public void setUsedDaysAndRecalculate(double usedDays) {
        this.usedDays = usedDays;
        if (!isUnlimited()) {
            this.remainingDays = this.totalEntitlement - usedDays;
        }
    }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getAccumulatedHalfDays() { return accumulatedHalfDays; }
    public void setAccumulatedHalfDays(int accumulatedHalfDays) { this.accumulatedHalfDays = accumulatedHalfDays; }

    // ── NEW getters/setters ───────────────────────────────────────────
    public double getCarryOverDays() { return carryOverDays; }
    public void setCarryOverDays(double carryOverDays) { this.carryOverDays = carryOverDays; }

    public String getHalfDayDeductedFrom() { return halfDayDeductedFrom; }
    public void setHalfDayDeductedFrom(String halfDayDeductedFrom) { this.halfDayDeductedFrom = halfDayDeductedFrom; }

    public java.util.Map<String, java.util.Map<String, Object>> getMonthlyUsage() { return monthlyUsage; }
    public void setMonthlyUsage(java.util.Map<String, java.util.Map<String, Object>> monthlyUsage) {
        this.monthlyUsage = monthlyUsage != null ? monthlyUsage : new java.util.LinkedHashMap<>();
    }

    public double getYearTotalUsed() { return yearTotalUsed; }
    public void setYearTotalUsed(double yearTotalUsed) { this.yearTotalUsed = yearTotalUsed; }

    public java.time.LocalDateTime getMonthlyUsageUpdatedAt() { return monthlyUsageUpdatedAt; }
    public void setMonthlyUsageUpdatedAt(java.time.LocalDateTime v) { this.monthlyUsageUpdatedAt = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }


}