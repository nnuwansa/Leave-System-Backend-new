package com.LeaveDataManagementSystem.LeaveManagement.Repository;



import com.LeaveDataManagementSystem.LeaveManagement.Model.PublicHoliday;
import com.LeaveDataManagementSystem.LeaveManagement.Model.PublicHoliday.HolidayType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PublicHolidayRepository extends MongoRepository<PublicHoliday, String> {

    // Find all holidays for a specific year
    List<PublicHoliday> findByYear(int year);

    // Count holidays for a specific year
    long countByYear(int year);

    // Find holidays between two dates
    List<PublicHoliday> findByDateBetween(LocalDate startDate, LocalDate endDate);

    // Find holiday by exact date
    PublicHoliday findByDate(LocalDate date);

    // Find holidays by type
    List<PublicHoliday> findByType(HolidayType type);

    // Find holidays by type and year
    List<PublicHoliday> findByTypeAndYear(HolidayType type, int year);

    // Check if a specific date is a holiday
    @Query("{'date': ?0}")
    PublicHoliday findHolidayByDate(LocalDate date);

    // Find all recurring holidays
    List<PublicHoliday> findByIsRecurringTrue();

    // Delete all holidays for a specific year
    void deleteByYear(int year);
}