package com.LeaveDataManagementSystem.LeaveManagement.Config;

import com.LeaveDataManagementSystem.LeaveManagement.Service.GoogleCalendarHolidayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Initializes holidays from Google Calendar on application startup
 */
@Component
public class GoogleCalendarInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarInitializer.class);

    @Autowired
    private GoogleCalendarHolidayService googleCalendarService;

    @Value("${holidays.google.enabled:true}")
    private boolean googleCalendarEnabled;

    @Value("${holidays.sync.years:3}")
    private int yearsToSync;

    @Override
    public void run(String... args) {
        if (!googleCalendarEnabled) {
            logger.info("⏸️ Google Calendar holiday sync is disabled");
            return;
        }

        logger.info("🌐 Starting Google Calendar holiday synchronization...");

        int currentYear = LocalDate.now().getYear();

        // Sync holidays for current year and next N years
        for (int year = currentYear; year <= currentYear + yearsToSync; year++) {
            try {
                googleCalendarService.syncHolidaysForYear(year);
            } catch (Exception e) {
                logger.error("❌ Failed to sync holidays for year {}", year, e);
            }
        }

        logger.info("✅ Google Calendar holiday synchronization completed!");
    }
}