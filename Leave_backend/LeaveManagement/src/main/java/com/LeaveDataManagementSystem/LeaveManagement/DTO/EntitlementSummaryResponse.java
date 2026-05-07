package com.LeaveDataManagementSystem.LeaveManagement.DTO;

import com.LeaveDataManagementSystem.LeaveManagement.Model.LeaveEntitlement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntitlementSummaryResponse {
    private String employeeEmail;
    private int year;
    private List<LeaveEntitlementResponse> entitlements;
    private double totalUsed;
    private double totalRemaining;
    private int totalEntitlement;
    private LocalDateTime lastUpdated;


    private Map<String, Double> unlimitedLeaveUsage;
    private List<String> unlimitedLeaveTypes;

    public EntitlementSummaryResponse() {}

    public EntitlementSummaryResponse(String employeeEmail, int year,
                                      List<LeaveEntitlement> entitlements) {
        this.employeeEmail = employeeEmail;
        this.year = year;
        this.entitlements = entitlements.stream()
                .map(LeaveEntitlementResponse::new)
                .toList();


        this.totalUsed = entitlements.stream()
                .filter(e -> !e.isUnlimited())
                .mapToDouble(e -> e.getUsedDays() + (e.getAccumulatedHalfDays() * 0.5))
                .sum();

        this.totalRemaining = entitlements.stream()
                .filter(e -> !e.isUnlimited())
                .mapToDouble(LeaveEntitlement::getEffectiveRemainingDays)
                .sum();

        this.totalEntitlement = entitlements.stream()
                .filter(e -> !e.isUnlimited())
                .mapToInt(LeaveEntitlement::getTotalEntitlement)
                .sum();

        // Handle unlimited leave usage
        this.unlimitedLeaveUsage = new HashMap<>();
        this.unlimitedLeaveTypes = new ArrayList<>();

        entitlements.stream()
                .filter(LeaveEntitlement::isUnlimited)
                .forEach(e -> {
                    this.unlimitedLeaveTypes.add(e.getLeaveType());
                    double effectiveUsage = e.getUsedDays() + (e.getAccumulatedHalfDays() * 0.5);
                    this.unlimitedLeaveUsage.put(e.getLeaveType(), effectiveUsage);
                });

        this.lastUpdated = LocalDateTime.now();
    }


    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public List<LeaveEntitlementResponse> getEntitlements() { return entitlements; }
    public void setEntitlements(List<LeaveEntitlementResponse> entitlements) { this.entitlements = entitlements; }

    public double getTotalUsed() { return totalUsed; }
    public void setTotalUsed(double totalUsed) { this.totalUsed = totalUsed; }

    public double getTotalRemaining() { return totalRemaining; }
    public void setTotalRemaining(double totalRemaining) { this.totalRemaining = totalRemaining; }

    public int getTotalEntitlement() { return totalEntitlement; }
    public void setTotalEntitlement(int totalEntitlement) { this.totalEntitlement = totalEntitlement; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public Map<String, Double> getUnlimitedLeaveUsage() { return unlimitedLeaveUsage; }
    public void setUnlimitedLeaveUsage(Map<String, Double> unlimitedLeaveUsage) { this.unlimitedLeaveUsage = unlimitedLeaveUsage; }

    public List<String> getUnlimitedLeaveTypes() { return unlimitedLeaveTypes; }
    public void setUnlimitedLeaveTypes(List<String> unlimitedLeaveTypes) { this.unlimitedLeaveTypes = unlimitedLeaveTypes; }


    public boolean hasUnlimitedLeaves() {
        return unlimitedLeaveTypes != null && !unlimitedLeaveTypes.isEmpty();
    }

    public double getDutyLeaveUsed() {
        return unlimitedLeaveUsage.getOrDefault("DUTY", 0.0);
    }
}