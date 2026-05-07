package com.LeaveDataManagementSystem.LeaveManagement.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "Late Coverage Records")
public class LateCoverageRecord {

    @Id
    private String id;

    private String       employeeEmail;
    private String       employeeName;
    private int          month;
    private int          year;

    // List of date strings "YYYY-MM-DD" — each date employee was late & did NOT cover
    private List<String> uncoveredDates;

    private int          uncoveredCount;      // = uncoveredDates.size()
    private int          halfDaysDeducted;    // = uncoveredCount / 3
    private double       casualDaysDeducted;  // = halfDaysDeducted * 0.5
    private int          remainder;           // = uncoveredCount % 3

    private String       adminNote;
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Getters & Setters ──────────────────────────────────────────────────
    public String getId()                           { return id; }
    public void   setId(String id)                  { this.id = id; }

    public String getEmployeeEmail()                { return employeeEmail; }
    public void   setEmployeeEmail(String v)        { this.employeeEmail = v; }

    public String getEmployeeName()                 { return employeeName; }
    public void   setEmployeeName(String v)         { this.employeeName = v; }

    public int    getMonth()                        { return month; }
    public void   setMonth(int v)                   { this.month = v; }

    public int    getYear()                         { return year; }
    public void   setYear(int v)                    { this.year = v; }

    public List<String> getUncoveredDates()         { return uncoveredDates; }
    public void   setUncoveredDates(List<String> v) { this.uncoveredDates = v; }

    public int    getUncoveredCount()               { return uncoveredCount; }
    public void   setUncoveredCount(int v)          { this.uncoveredCount = v; }

    public int    getHalfDaysDeducted()             { return halfDaysDeducted; }
    public void   setHalfDaysDeducted(int v)        { this.halfDaysDeducted = v; }

    public double getCasualDaysDeducted()           { return casualDaysDeducted; }
    public void   setCasualDaysDeducted(double v)   { this.casualDaysDeducted = v; }

    public int    getRemainder()                    { return remainder; }
    public void   setRemainder(int v)               { this.remainder = v; }

    public String getAdminNote()                    { return adminNote; }
    public void   setAdminNote(String v)            { this.adminNote = v; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void   setCreatedAt(LocalDateTime v)     { this.createdAt = v; }
}