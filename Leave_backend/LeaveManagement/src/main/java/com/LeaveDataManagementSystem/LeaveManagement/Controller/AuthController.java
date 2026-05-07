package com.LeaveDataManagementSystem.LeaveManagement.Controller;

import com.LeaveDataManagementSystem.LeaveManagement.DTO.LoginRequest;
import com.LeaveDataManagementSystem.LeaveManagement.DTO.LoginResponse;
import com.LeaveDataManagementSystem.LeaveManagement.DTO.RegisterRequest;
import com.LeaveDataManagementSystem.LeaveManagement.Service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            LoginResponse response = authService.login(loginRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body("❌ Invalid credentials");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        try {
            String message = authService.register(registerRequest);
            return ResponseEntity.ok(message);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("❌ " + e.getMessage());
        }
    }
}
