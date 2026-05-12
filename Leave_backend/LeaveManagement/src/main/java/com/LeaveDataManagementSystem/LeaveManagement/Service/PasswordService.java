package com.LeaveDataManagementSystem.LeaveManagement.Service;

import com.LeaveDataManagementSystem.LeaveManagement.DTO.ChangePasswordRequest;
import com.LeaveDataManagementSystem.LeaveManagement.Model.Notification;
import com.LeaveDataManagementSystem.LeaveManagement.Model.User;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.NotificationRepository;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String ADMIN_EMAIL = "admin@example.com";

    public String changePassword(String email, ChangePasswordRequest request) {
        //  use findByEmail, not findById
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User Not Found");
        }

        //  use BCrypt matches, not plain .equals()
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Old Password Is Incorrect");
        }

        // encode new password before saving
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        Notification notification = new Notification();
        notification.setRecipient(ADMIN_EMAIL);
        notification.setEmail(user.getEmail());
        notification.setMessage("Employee " + user.getFullName() + " has changed their password");
        //  Don't store passwords in notifications — removed setOldPassword/setNewPassword
        notificationRepository.save(notification);

        return "Password Changed Successfully!";
    }
}