package com.LeaveDataManagementSystem.LeaveManagement.Service;

import com.LeaveDataManagementSystem.LeaveManagement.Model.Leave;
import com.LeaveDataManagementSystem.LeaveManagement.Model.User;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationService {

    static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private EmailService emailService;

    @Autowired
    private SmsService smsService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Notify acting officer about a new leave request (Email + SMS)
     */
    public void notifyActingOfficer(Leave leave) {
        try {
            // Send Email
            emailService.sendLeaveRequestNotification(
                    leave.getActingOfficerEmail(),
                    leave.getActingOfficerName(),
                    leave,
                    "ACTING"
            );

            // Send SMS
            User actingOfficer = userRepository.findByEmail(leave.getActingOfficerEmail());
            if (actingOfficer != null && actingOfficer.getPhoneNumber() != null) {
                smsService.sendLeaveRequestNotification(
                        actingOfficer.getPhoneNumber(),
                        leave.getActingOfficerName(),
                        leave,
                        "ACTING"
                );
            }

            logger.info("✅ Notifications sent to Acting Officer: {}", leave.getActingOfficerEmail());

        } catch (Exception e) {
            logger.error("❌ Failed to notify acting officer: {}", e.getMessage());
        }
    }

    /**
     * Notify supervising officer (Email + SMS)
     */
    public void notifySupervisingOfficer(Leave leave) {
        try {
            // Send Email
            emailService.sendLeaveRequestNotification(
                    leave.getSupervisingOfficerEmail(),
                    leave.getSupervisingOfficerName(),
                    leave,
                    "SUPERVISING"
            );

            // Send SMS
            User supervisingOfficer = userRepository.findByEmail(leave.getSupervisingOfficerEmail());
            if (supervisingOfficer != null && supervisingOfficer.getPhoneNumber() != null) {
                smsService.sendLeaveRequestNotification(
                        supervisingOfficer.getPhoneNumber(),
                        leave.getSupervisingOfficerName(),
                        leave,
                        "SUPERVISING"
                );
            }

            logger.info("✅ Notifications sent to Supervising Officer: {}",
                    leave.getSupervisingOfficerEmail());

        } catch (Exception e) {
            logger.error("❌ Failed to notify supervising officer: {}", e.getMessage());
        }
    }

    /**
     * Notify approval officer (Email + SMS)
     */
    public void notifyApprovalOfficer(Leave leave) {
        try {
            // Send Email
            emailService.sendLeaveRequestNotification(
                    leave.getApprovalOfficerEmail(),
                    leave.getApprovalOfficerName(),
                    leave,
                    "APPROVAL"
            );

            // Send SMS
            User approvalOfficer = userRepository.findByEmail(leave.getApprovalOfficerEmail());
            if (approvalOfficer != null && approvalOfficer.getPhoneNumber() != null) {
                smsService.sendLeaveRequestNotification(
                        approvalOfficer.getPhoneNumber(),
                        leave.getApprovalOfficerName(),
                        leave,
                        "APPROVAL"
                );
            }

            logger.info("✅ Notifications sent to Approval Officer: {}",
                    leave.getApprovalOfficerEmail());

        } catch (Exception e) {
            logger.error("❌ Failed to notify approval officer: {}", e.getMessage());
        }
    }

    /**
     * Notify employee about status (Email + SMS)
     */
    public void notifyEmployee(Leave leave, String action, String officerType) {
        try {
            String comments = getCommentsForOfficerType(leave, officerType);

            // Send Email
            emailService.sendLeaveStatusNotification(
                    leave.getEmployeeEmail(),
                    leave.getEmployeeName(),
                    leave,
                    action.toUpperCase(),
                    officerType,
                    comments
            );

            // Send SMS
            User employee = userRepository.findByEmail(leave.getEmployeeEmail());
            if (employee != null && employee.getPhoneNumber() != null) {
                smsService.sendLeaveStatusNotification(
                        employee.getPhoneNumber(),
                        leave.getEmployeeName(),
                        leave,
                        action.toUpperCase(),
                        officerType,
                        comments
                );
            }

            logger.info("✅ Status notifications sent to Employee: {}", leave.getEmployeeEmail());

        } catch (Exception e) {
            logger.error("❌ Failed to notify employee: {}", e.getMessage());
        }
    }

    /**
     * Notify about leave cancellation (Email + SMS)
     */
    public void notifyLeaveCancellation(Leave leave, String cancelledBy) {
        try {
            // Notify all officers
            notifyOfficerOfCancellation(leave.getActingOfficerEmail(),
                    leave.getActingOfficerName(), leave);
            notifyOfficerOfCancellation(leave.getSupervisingOfficerEmail(),
                    leave.getSupervisingOfficerName(), leave);
            notifyOfficerOfCancellation(leave.getApprovalOfficerEmail(),
                    leave.getApprovalOfficerName(), leave);

            logger.info("✅ Cancellation notifications sent for leave: {}", leave.getId());

        } catch (Exception e) {
            logger.error("❌ Failed to send cancellation notifications: {}", e.getMessage());
        }
    }

    /**
     * Notify about maternity leave end date (Email + SMS)
     */
    public void notifyMaternityLeaveEndDateSet(Leave leave, String adminEmail) {
        try {
            User employee = userRepository.findByEmail(leave.getEmployeeEmail());
            if (employee != null) {
                String adminComments = leave.getMaternityAdditionalDetails();

                // Send Email
                emailService.sendMaternityLeaveEndDateNotification(
                        employee.getEmail(),
                        employee.getName(),
                        leave,
                        adminEmail,
                        adminComments
                );

                // Send SMS
                if (employee.getPhoneNumber() != null) {
                    smsService.sendMaternityLeaveEndDateNotification(
                            employee.getPhoneNumber(),
                            employee.getName(),
                            leave,
                            adminEmail
                    );
                }

                logger.info("✅ Maternity end date notifications sent to: {}", employee.getEmail());
            }
        } catch (Exception e) {
            logger.error("❌ Failed to notify about maternity end date: {}", e.getMessage());
        }
    }

    // ========== Helper Methods ==========

    private void notifyOfficerOfCancellation(String email, String name, Leave leave) {
        if (email == null || email.isEmpty()) return;

        try {
            // Send Email
            emailService.sendLeaveStatusNotification(
                    email, name, leave, "CANCELLED", "Employee",
                    "Leave request has been cancelled by the employee"
            );

            // Send SMS
            User officer = userRepository.findByEmail(email);
            if (officer != null && officer.getPhoneNumber() != null) {
                smsService.sendLeaveCancellationNotification(
                        officer.getPhoneNumber(),
                        name,
                        leave
                );
            }
        } catch (Exception e) {
            logger.error("Failed to notify officer {} of cancellation: {}", email, e.getMessage());
        }
    }

    private String getCommentsForOfficerType(Leave leave, String officerType) {
        switch (officerType) {
            case "Acting Officer":
                return leave.getActingOfficerComments();
            case "Supervising Officer":
                return leave.getSupervisingOfficerComments();
            case "Approval Officer":
                return leave.getApprovalOfficerComments();
            default:
                return null;
        }
    }
}