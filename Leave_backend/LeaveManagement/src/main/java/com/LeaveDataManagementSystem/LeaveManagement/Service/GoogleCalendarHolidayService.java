
package com.LeaveDataManagementSystem.LeaveManagement.Service;

import com.LeaveDataManagementSystem.LeaveManagement.Model.PublicHoliday;
import com.LeaveDataManagementSystem.LeaveManagement.Model.PublicHoliday.HolidayType;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.PublicHolidayRepository;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Service to fetch holidays from Google Calendar API
 * Uses Sri Lanka's public holiday calendar
 */
@Service
public class GoogleCalendarHolidayService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarHolidayService.class);

    // Sri Lanka Holidays Calendar ID
    private static final String SRI_LANKA_CALENDAR_ID = "en.lk#holiday@group.v.calendar.google.com";

    //  List of non-public holidays to exclude (observances, not official holidays)
    private static final List<String> EXCLUDED_HOLIDAYS = Arrays.asList(
            "valentine's day",
            "valentine day",
            "ramadan start",
            "ramadan begins",
            "start of ramadan",
            "easter sunday",
            "mother's day",
            "father's day",
            "muharram",
            "islamic new year",
            "christmas eve",
            "new year's eve",
            "halloween",
            "thanksgiving",
            "black friday",
            "cyber monday",
            "daylight saving",
            "spring equinox",
            "autumn equinox",
            "summer solstice",
            "winter solstice",
            "earth day",
            "world environment day",
            "international women's day",
            "labour day eve",
            "boxing day eve"
    );

    @Value("${google.api.key:}")
    private String apiKey;

    @Autowired
    private PublicHolidayRepository holidayRepository;

    /**
     * Check if a holiday should be excluded (not a real public holiday)
     */
    private boolean shouldExcludeHoliday(String holidayName) {
        if (holidayName == null) return true;

        String nameLower = holidayName.toLowerCase().trim();

        // Check if holiday name contains any excluded keywords
        for (String excluded : EXCLUDED_HOLIDAYS) {
            if (nameLower.contains(excluded)) {
                logger.info("   ⊗ Excluding non-public holiday: {}", holidayName);
                return true;
            }
        }

        // Exclude if it's marked as "observance" or "celebration" (not official)
        if (nameLower.contains("observance") ||
                nameLower.contains("celebration") ||
                nameLower.contains("awareness day") ||
                nameLower.contains("international day")) {
            //logger.info("   ⊗ Excluding observance/celebration: {}", holidayName);
            return true;
        }

        return false;
    }

    /**
     * Fetch holidays from Google Calendar for a specific year
     */
    public List<PublicHoliday> fetchHolidaysFromGoogle(int year) {
        //logger.info("🌐 Fetching holidays from Google Calendar for year: {}", year);

        List<PublicHoliday> holidays = new ArrayList<>();

        try {
            // Build Calendar service
            Calendar service = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    null)
                    .setApplicationName("Leave Management System")
                    .build();

            // Set date range for the year
            LocalDate startDate = LocalDate.of(year, 1, 1);
            LocalDate endDate = LocalDate.of(year, 12, 31);

            DateTime timeMin = new DateTime(Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            DateTime timeMax = new DateTime(Date.from(endDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));

            // Fetch events from Google Calendar
            Events events = service.events().list(SRI_LANKA_CALENDAR_ID)
                    .setKey(apiKey)
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            List<Event> items = events.getItems();
            //logger.info("📅 Found {} events from Google Calendar", items.size());

            // Convert Google Calendar events to PublicHoliday objects
            for (Event event : items) {
                try {
                    String holidayName = event.getSummary();

                    // FILTER: Skip non-public holidays
                    if (shouldExcludeHoliday(holidayName)) {
                        continue;
                    }

                    // Parse date from event
                    LocalDate holidayDate;
                    if (event.getStart().getDate() != null) {
                        // All-day event
                        holidayDate = LocalDate.parse(event.getStart().getDate().toString());
                    } else if (event.getStart().getDateTime() != null) {
                        // Date-time event
                        Date date = new Date(event.getStart().getDateTime().getValue());
                        holidayDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    } else {
                        logger.warn("⚠️ Could not parse date for event: {}", holidayName);
                        continue;
                    }

                    // Determine holiday type based on name/description
                    HolidayType type = determineHolidayType(holidayName, event.getDescription());

                    PublicHoliday holiday = new PublicHoliday(
                            holidayName,
                            holidayDate,
                            year,
                            type,
                            false
                    );

                    holiday.setDescription(event.getDescription());
                    holidays.add(holiday);

                    logger.info("   ✓ {} on {} [{}]", holidayName, holidayDate, type);

                } catch (Exception e) {
                    logger.error("❌ Error parsing event: {}", event.getSummary(), e);
                }
            }

            logger.info("✅ Successfully fetched {} valid public holidays from Google Calendar", holidays.size());

        } catch (Exception e) {
            logger.error("❌ Error fetching holidays from Google Calendar", e);
        }

        return holidays;
    }

    /**
     * Sync holidays from Google Calendar to database for a specific year
     */
    public void syncHolidaysForYear(int year) {
        logger.info("🔄 Syncing holidays for year {} from Google Calendar...", year);

        // Check if holidays already exist
        long existingCount = holidayRepository.countByYear(year);
        if (existingCount > 0) {
            logger.info("   ✓ {} holidays already exist for year {}", existingCount, year);
            logger.info("   🗑️ Deleting existing holidays to re-sync...");
            holidayRepository.deleteByYear(year);
        }

        // Fetch holidays from Google
        List<PublicHoliday> holidays = fetchHolidaysFromGoogle(year);

        if (!holidays.isEmpty()) {
            // Save to database
            holidayRepository.saveAll(holidays);
            //logger.info("✅ Synced {} holidays for year {}", holidays.size(), year);
        } else {
            logger.warn("⚠️ No holidays fetched from Google Calendar for year {}", year);
        }
    }

    /**
     * Determine holiday type based on name and description
     */
    private HolidayType determineHolidayType(String name, String description) {
        if (name == null) return HolidayType.PUBLIC;

        String nameLower = name.toLowerCase();
        String descLower = description != null ? description.toLowerCase() : "";

        // Mercantile holidays (Bank holidays)
        if (nameLower.contains("pongal") ||
                nameLower.contains("may day") ||
                nameLower.contains("bank holiday") ||
                nameLower.contains("mercantile") ||
                descLower.contains("mercantile") ||
                descLower.contains("bank holiday")) {
            return HolidayType.MERCANTILE;
        }

        // Poya days (Full moon days)
        if (nameLower.contains("poya") ||
                nameLower.contains("full moon")) {
            return HolidayType.POYA;
        }

        // Default to public holiday
        return HolidayType.PUBLIC;
    }
}