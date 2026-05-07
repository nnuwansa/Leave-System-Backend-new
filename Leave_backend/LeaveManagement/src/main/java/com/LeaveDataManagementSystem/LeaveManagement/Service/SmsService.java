package com.LeaveDataManagementSystem.LeaveManagement.Service;

import com.LeaveDataManagementSystem.LeaveManagement.Model.Leave;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class SmsService {

    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);

    @Value("${sms.api.url:https://api.twilio.com/2010-04-01/Accounts/ACXXXXXXX/Messages.json}")
    private String smsApiUrl;

    @Value("${twilio.account.sid}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token}")
    private String twilioAuthToken;

    @Value("${sms.sender.id:+1234567890}")
    private String smsSenderId;

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${app.company-name:Company}")
    private String companyName;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Send SMS for leave request notification to officer
     */
    @Async
    public void sendLeaveRequestNotification(String phoneNumber, String officerName, Leave leave, String role) {
        if (!smsEnabled) {
            logger.info("SMS notifications are disabled");
            return;
        }

        try {
            String message = buildLeaveRequestMessage(officerName, leave, role);
            sendSms(phoneNumber, message);

            logger.info("SMS sent successfully to {} ({}) for leave request {}",
                    officerName, phoneNumber, leave.getId());

        } catch (Exception e) {
            logger.error("Failed to send SMS to {} - Error: {}", phoneNumber, e.getMessage(), e);
        }
    }

    /**
     * Send SMS for leave status notification to employee
     */
    @Async
    public void sendLeaveStatusNotification(String phoneNumber, String employeeName, Leave leave,
                                            String status, String officerType, String comments) {
        if (!smsEnabled) {
            logger.info("SMS notifications are disabled");
            return;
        }

        try {
            String message = buildStatusMessage(employeeName, leave, status, officerType, comments);
            sendSms(phoneNumber, message);

            logger.info("Status SMS sent successfully to {} ({}) for leave request {}",
                    employeeName, phoneNumber, leave.getId());

        } catch (Exception e) {
            logger.error("Failed to send status SMS to {} - Error: {}", phoneNumber, e.getMessage(), e);
        }
    }

    /**
     * Send SMS for maternity leave end date notification
     */
    @Async
    public void sendMaternityLeaveEndDateNotification(String phoneNumber, String employeeName,
                                                      Leave leave, String adminEmail) {
        if (!smsEnabled) {
            logger.info("SMS notifications are disabled");
            return;
        }

        try {
            String message = buildMaternityEndDateMessage(employeeName, leave);
            sendSms(phoneNumber, message);

            logger.info("Maternity leave end date SMS sent to {} ({}) for leave request {}",
                    employeeName, phoneNumber, leave.getId());

        } catch (Exception e) {
            logger.error("Failed to send maternity end date SMS to {} - Error: {}",
                    phoneNumber, e.getMessage(), e);
        }
    }

    /**
     * Send SMS for leave cancellation
     */
    @Async
    public void sendLeaveCancellationNotification(String phoneNumber, String recipientName, Leave leave) {
        if (!smsEnabled) {
            logger.info("SMS notifications are disabled");
            return;
        }

        try {
            String message = buildCancellationMessage(recipientName, leave);
            sendSms(phoneNumber, message);

            logger.info("Cancellation SMS sent to {} for leave request {}", phoneNumber, leave.getId());

        } catch (Exception e) {
            logger.error("Failed to send cancellation SMS to {} - Error: {}", phoneNumber, e.getMessage(), e);
        }
    }

    /**
     * Core method to send SMS via Twilio API with Basic Authentication
     */
    private void sendSms(String phoneNumber, String message) {
        try {
            // Clean phone number (remove spaces, dashes, etc.)
            String cleanedPhone = cleanPhoneNumber(phoneNumber);

            if (cleanedPhone == null || cleanedPhone.isEmpty()) {
                logger.warn("Invalid phone number: {}", phoneNumber);
                return;
            }

            // Prepare headers with Basic Authentication
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Create Basic Auth header: Base64(AccountSid:AuthToken)
            String auth = twilioAccountSid + ":" + twilioAuthToken;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth);
            headers.set("Authorization", authHeader);

            // Prepare form data (Twilio requires form-urlencoded)
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("To", cleanedPhone);
            requestBody.add("From", smsSenderId);
            requestBody.add("Body", message);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            // Send SMS
            logger.debug("Sending SMS to {} via Twilio", cleanedPhone);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    smsApiUrl,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.CREATED) {
                logger.info("SMS sent successfully to {}", cleanedPhone);
            } else {
                logger.warn("SMS API returned status: {} for phone: {}",
                        response.getStatusCode(), cleanedPhone);
            }

        } catch (Exception e) {
            logger.error("Error sending SMS to {}: {}", phoneNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to send SMS", e);
        }
    }


    /**
     * Build message for leave request notification
     */
    private String buildLeaveRequestMessage(String officerName, Leave leave, String role) {
        String actionText = getActionText(role);
        String dates = formatDateRange(leave.getStartDate(), leave.getEndDate());

        return String.format(
                "%s\n" +
                        "%s\n" +
                        "Dear %s,\n" +
                        "New leave request from %s\n" +
                        "Type: %s\n" +
                        "Date: %s\n" +
                        "Please review in system.",
                companyName,
                actionText,
                officerName,
                leave.getEmployeeName(),
                formatLeaveType(leave.getLeaveType()),
                dates
        );
    }

    /**
     * Build message for status notification
     */
    private String buildStatusMessage(String employeeName, Leave leave, String status,
                                      String officerType, String comments) {
        String dates = formatDateRange(leave.getStartDate(), leave.getEndDate());
        StringBuilder message = new StringBuilder();

        message.append(companyName).append("\n");
        message.append("-----------------\n");
        message.append("Leave Update\n");
        message.append("Dear ").append(employeeName).append(",\n");
        message.append("Your leave request has been ").append(status.toLowerCase());
        message.append(" by ").append(officerType).append(".\n");
        message.append("Type: ").append(formatLeaveType(leave.getLeaveType())).append("\n");
        message.append("Date: ").append(dates).append("\n");

        if (comments != null && !comments.trim().isEmpty()) {
            message.append("Comments: ").append(comments).append("\n");
        }

        message.append("Check system for details.");

        return message.toString();
    }
    /**
     * Build message for maternity leave end date
     */
    private String buildMaternityEndDateMessage(String employeeName, Leave leave) {
        long totalDays = ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1;

        return String.format(
                "%s Maternity Leave Update\n" +
                        "Dear %s,\n" +
                        "Your maternity leave end date has been set.\n" +
                        "Type: %s\n" +
                        "Start: %s\n" +
                        "End: %s\n" +
                        "Duration: %d days\n" +
                        "Return Date: %s\n" +
                        "Check email for full details.",
                companyName,
                employeeName,
                formatMaternityType(leave.getMaternityLeaveType()),
                formatDate(leave.getStartDate()),
                formatDate(leave.getEndDate()),
                totalDays,
                formatDate(leave.getEndDate().plusDays(1))
        );
    }

    /**
     * Build message for leave cancellation
     */
    private String buildCancellationMessage(String recipientName, Leave leave) {
        String dates = formatDateRange(leave.getStartDate(), leave.getEndDate());

        return String.format(
                "%s\n" +
                        "-------------------\n" +
                        "Leave Cancelled\n" +
                        "Dear %s,\n" +
                        "Leave request from %s has been cancelled.\n" +
                        "Type: %s\n" +
                        "Date: %s\n" +
                        "No action required.",
                companyName,
                recipientName,
                leave.getEmployeeName(),
                formatLeaveType(leave.getLeaveType()),
                dates
        );
    }
    /**
     * Helper method to format date range
     */
    private String formatDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate == null || endDate == null) return "N/A";

        String start = formatDate(startDate);
        String end = formatDate(endDate);

        // If same date, show only once
        if (startDate.equals(endDate)) {
            return start;
        }

        return start + " to " + end;
    }

    /**
     * Helper method to get action text based on role
     */
    private String getActionText(String role) {
        switch (role.toUpperCase()) {
            case "ACTING": return "Action Required - Acting Officer";
            case "SUPERVISING": return "Action Required - Supervising Officer";
            case "APPROVAL": return "Action Required - Final Approval";
            default: return "New Leave Request";
        }
    }

    /**
     * Helper method to format leave type
     */
    private String formatLeaveType(String leaveType) {
        switch (leaveType) {
            case "CASUAL": return "Casual Leave";
            case "SICK": return "Medical Leave";
            case "MATERNITY": return "Maternity Leave";
            case "SHORT": case "SHORT_LEAVE": return "Short Leave";
            case "HALF_DAY": return "Half Day";
            default: return leaveType.replace("_", " ");
        }
    }

    /**
     * Helper method to format maternity leave type
     */
    private String formatMaternityType(String type) {
        if (type == null) return "Full Pay";
        switch (type.toUpperCase()) {
            case "FULL_PAY": return "Full Pay";
            case "HALF_PAY": return "Half Pay";
            case "NO_PAY": return "No Pay";
            default: return type.replace("_", " ");
        }
    }

    /**
     * Helper method to format date
     */
    private String formatDate(java.time.LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
    }

    /**
     * Clean phone number and add Sri Lanka country code if needed
     */
    private String cleanPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;

        // Remove spaces, dashes, parentheses
        String cleaned = phoneNumber.replaceAll("[\\s\\-\\(\\)]", "");

        // Keep only digits and +
        cleaned = cleaned.replaceAll("[^0-9+]", "");

        // If no country code, add +94 for Sri Lanka
        if (!cleaned.startsWith("+")) {
            // Remove leading 0 if present
            if (cleaned.startsWith("0")) {
                cleaned = cleaned.substring(1);
            }
            cleaned = "+94" + cleaned;
        }

        return cleaned;
    }

    /**
     * Validate phone number format
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        String cleaned = cleanPhoneNumber(phoneNumber);

        // Basic validation: should have + and 10-15 digits
        return cleaned.matches("\\+\\d{10,15}");
    }
}