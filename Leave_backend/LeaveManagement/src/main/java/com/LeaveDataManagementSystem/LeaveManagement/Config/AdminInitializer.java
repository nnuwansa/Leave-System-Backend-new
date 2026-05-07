package com.LeaveDataManagementSystem.LeaveManagement.Config;

import com.LeaveDataManagementSystem.LeaveManagement.Model.User;
import com.LeaveDataManagementSystem.LeaveManagement.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${admin.emails:}")
    private String adminEmailsStr;

    @Value("${admin.passwords:}")
    private String adminPasswordsStr;

    @Value("${admin.names:}")
    private String adminNamesStr;

    @Override
    public void run(String... args) throws Exception {
        if (adminEmailsStr.isEmpty()) {
            System.out.println("⚠ No admin users configured");
            return;
        }

        List<String> adminEmails = Arrays.stream(adminEmailsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<String> adminPasswords = Arrays.stream(adminPasswordsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<String> adminNames = adminNamesStr.isEmpty() ?
                List.of() :
                Arrays.stream(adminNamesStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        if (adminEmails.size() != adminPasswords.size()) {
            System.err.println("❌ Admin emails and passwords count mismatch!");
            System.err.println("Emails count: " + adminEmails.size());
            System.err.println("Passwords count: " + adminPasswords.size());
            return;
        }

        // Find existing admins by role
        List<User> allUsers = userRepository.findAll();
        List<String> existingAdminEmails = allUsers.stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains("ADMIN"))
                .map(User::getEmail)
                .collect(Collectors.toList());

        // Delete admins that are not in the configured list
        for (String existingEmail : existingAdminEmails) {
            if (!adminEmails.contains(existingEmail)) {
                userRepository.deleteByEmail(existingEmail);
                System.out.println("🗑️ Old admin user deleted: " + existingEmail);
            }
        }

        // Create or update each admin
        for (int i = 0; i < adminEmails.size(); i++) {
            String email = adminEmails.get(i);
            String plainPassword = adminPasswords.get(i);
            String name = (adminNames.size() > i) ? adminNames.get(i) : "Admin " + (i + 1);

            if (!userRepository.existsByEmail(email)) {
                // CREATE NEW ADMIN WITH ENCODED PASSWORD
                User admin = new User();
                admin.setEmail(email);
                admin.setPassword(passwordEncoder.encode(plainPassword));
                admin.setName(name);
                admin.setFullName(name);
                admin.setRoles(Set.of("ADMIN", "EMPLOYEE"));
                admin.setDesignation("Administrator");
                admin.setDepartment("Administration");

                userRepository.save(admin);
                System.out.println("✅ Admin user created: " + email + " (password BCrypt encoded)");
            } else {
                // UPDATE EXISTING ADMIN
                User existingAdmin = userRepository.findByEmail(email);
                if (existingAdmin != null) {
                    String currentPassword = existingAdmin.getPassword();

                    boolean isBCrypt = currentPassword != null && (
                            currentPassword.startsWith("$2a$") ||
                                    currentPassword.startsWith("$2b$") ||
                                    currentPassword.startsWith("$2y$"));

                    if (!isBCrypt) {
                        System.out.println("⚠️  Fixing plain text password for: " + email);
                        existingAdmin.setPassword(passwordEncoder.encode(plainPassword));
                        System.out.println("✅ Password fixed with BCrypt encoding");
                    } else {
//                        System.out.println("ℹ️  Keeping existing BCrypt password for: " + email);
                    }

                    existingAdmin.setName(name);
                    existingAdmin.setFullName(name);

                    if (existingAdmin.getDepartment() == null) {
                        existingAdmin.setDepartment("Administration");
                    }
                    if (existingAdmin.getDesignation() == null) {
                        existingAdmin.setDesignation("Administrator");
                    }

                    if (existingAdmin.getRoles() == null || !existingAdmin.getRoles().contains("ADMIN")) {
                        existingAdmin.setRoles(Set.of("ADMIN", "EMPLOYEE"));
                    }

                    userRepository.save(existingAdmin);
//                    System.out.println("🔄 Admin user updated: " + email);
                }
            }
        }
    }
}