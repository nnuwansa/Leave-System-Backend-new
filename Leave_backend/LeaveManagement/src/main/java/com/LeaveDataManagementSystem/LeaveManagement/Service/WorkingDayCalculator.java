package com.LeaveDataManagementSystem.LeaveManagement.Service;

import com.LeaveDataManagementSystem.LeaveManagement.Model.PublicHoliday;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.PublicHolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkingDayCalculator {

    private static final Logger logger = LoggerFactory.getLogger(WorkingDayCalculator.class);

    @Autowired
    private PublicHolidayRepository holidayRepository;

    /**
     * Calculate working days between two dates (excluding weekends and ALL holidays)
     */
    public Map<String, Integer> calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        logger.info("Calculating working days from {} to {}", startDate, endDate);

        // Validate dates
        if (startDate == null || endDate == null) {
            logger.error("Start date or end date is null");
            return createErrorResponse();
        }

        if (endDate.isBefore(startDate)) {
            logger.error("End date is before start date");
            return createErrorResponse();
        }

        // Calculate total days (inclusive)
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // Get ALL holidays in the date range from database (public + mercantile + poya)
        final List<PublicHoliday> holidays = holidayRepository.findByDateBetween(startDate, endDate);

        // ✅ FIX: Use Set of dates instead of Map to handle multiple holidays on same date
        // This fixes the error when May 1st has both Vesak Poya Day AND May Day
        Set<LocalDate> holidayDates = holidays.stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        logger.info("Found {} total holidays in date range (all types)", holidays.size());
        for (PublicHoliday holiday : holidays) {
            logger.info("  - {} on {} [{}]", holiday.getName(), holiday.getDate(), holiday.getType());
        }

        // Count different types
        int weekendDays = 0;
        int publicHolidays = 0;
        int workingDays = 0;

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
            boolean isWeekend = (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY);
            boolean isHoliday = holidayDates.contains(currentDate); //  Changed from holidayMap.containsKey()

            if (isWeekend) {
                weekendDays++;
                logger.debug("  {} - Weekend ({})", currentDate, dayOfWeek);
            } else if (isHoliday) {
                publicHolidays++;
                // Find the holiday name(s) for logging
                final LocalDate checkDate = currentDate; // Make effectively final for lambda
                String holidayNames = holidays.stream()
                        .filter(h -> h.getDate().equals(checkDate))
                        .map(h -> h.getName() + " [" + h.getType() + "]")
                        .collect(Collectors.joining(", "));
                logger.info("  {} - Holiday: {}", currentDate, holidayNames);
            } else {
                workingDays++;
                logger.debug("  {} - Working day", currentDate);
            }

            currentDate = currentDate.plusDays(1);
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("totalDays", (int) totalDays);
        result.put("workingDays", workingDays);
        result.put("weekendDays", weekendDays);
        result.put("publicHolidays", publicHolidays);

        logger.info("Working days calculation result: Total={}, Working={}, Weekends={}, Holidays={}",
                totalDays, workingDays, weekendDays, publicHolidays);

        return result;
    }

    /**
     * Check if a specific date is a working day
     */
    public boolean isWorkingDay(LocalDate date) {
        // Check if weekend
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            logger.debug("{} is a weekend", date);
            return false;
        }

        // Check if ANY type of holiday (public, mercantile, poya)
        //  Changed to handle multiple holidays on same date
        List<PublicHoliday> holidaysOnDate = holidayRepository.findByDateBetween(date, date);
        if (!holidaysOnDate.isEmpty()) {
            String holidayNames = holidaysOnDate.stream()
                    .map(h -> h.getName() + " [" + h.getType() + "]")
                    .collect(Collectors.joining(", "));
            logger.info("{} is a holiday: {}", date, holidayNames);
            return false;
        }

        return true;
    }

    /**
     * Get all public holidays for a specific year
     */
    public List<PublicHoliday> getHolidaysForYear(int year) {
        return holidayRepository.findByYear(year);
    }

    /**
     * Get all public holidays between two dates
     */
    public List<PublicHoliday> getHolidaysBetween(LocalDate startDate, LocalDate endDate) {
        return holidayRepository.findByDateBetween(startDate, endDate);
    }

    private Map<String, Integer> createErrorResponse() {
        Map<String, Integer> error = new HashMap<>();
        error.put("totalDays", 0);
        error.put("workingDays", 0);
        error.put("weekendDays", 0);
        error.put("publicHolidays", 0);
        return error;
    }

    /**
     * Inner class to hold leave day breakdown
     */
    public static class LeaveDayBreakdown {
        private final int totalDays;
        private final int workingDays;
        private final int weekendDays;
        private final int publicHolidays;

        public LeaveDayBreakdown(int totalDays, int workingDays, int weekendDays, int publicHolidays) {
            this.totalDays = totalDays;
            this.workingDays = workingDays;
            this.weekendDays = weekendDays;
            this.publicHolidays = publicHolidays;
        }

        public int getTotalDays() { return totalDays; }
        public int getWorkingDays() { return workingDays; }
        public int getWeekendDays() { return weekendDays; }
        public int getPublicHolidays() { return publicHolidays; }
    }

    /**
     * Get breakdown of days in a leave period
     */
    public LeaveDayBreakdown getLeaveBreakdown(LocalDate startDate, LocalDate endDate) {
        Map<String, Integer> result = calculateWorkingDays(startDate, endDate);
        return new LeaveDayBreakdown(
                result.get("totalDays"),
                result.get("workingDays"),
                result.get("weekendDays"),
                result.get("publicHolidays")
        );
    }
}