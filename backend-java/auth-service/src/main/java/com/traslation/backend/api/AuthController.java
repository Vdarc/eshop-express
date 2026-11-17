package com.traslation.backend.api;

import com.traslation.backend.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.register(body));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        return service.login(body);
    }

    @PostMapping("/register/merchant")
    public ResponseEntity<?> registerMerchant(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.registerMerchant(body));
    }

    @PostMapping("/update-password")
    public Map<String, Object> updatePassword(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        return service.updatePassword(authentication.getName(), body);
    }
}
