package com.LeaveDataManagementSystem.LeaveManagement.Model;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "publicHolidays")
public class PublicHoliday {

    @Id
    private String id;

    private String name;
    private LocalDate date;
    private int year;
    private HolidayType type;
    private boolean isRecurring;
    private String description;

    // Enum for holiday types
    public enum HolidayType {
        MERCANTILE,      // Mercantile/Bank holidays (Thai Pongal, May Day, etc.)
        PUBLIC,          // Public holidays (Independence Day, Christmas, etc.)
        POYA,            // Poya days (Full moon days)
        BANK_ONLY,       // Bank-only holidays
        GOVERNMENT       // Government sector holidays
    }

    // Constructor for easy creation
    public PublicHoliday(String name, LocalDate date, int year, HolidayType type) {
        this.name = name;
        this.date = date;
        this.year = year;
        this.type = type;
        this.isRecurring = false;
    }

    public PublicHoliday(String name, LocalDate date, int year, HolidayType type, boolean isRecurring) {
        this.name = name;
        this.date = date;
        this.year = year;
        this.type = type;
        this.isRecurring = isRecurring;
    }
}