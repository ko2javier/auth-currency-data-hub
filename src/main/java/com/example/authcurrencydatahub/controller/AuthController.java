package com.example.authcurrencydatahub.controller;

import com.example.authcurrencydatahub.dto.AuthRequest;
import com.example.authcurrencydatahub.dto.AuthResponse;
import com.example.authcurrencydatahub.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        if ("admin".equals(request.getUsername()) && "admin".equals(request.getPassword())) {
            String token = jwtService.generateToken(request.getUsername());
            return ResponseEntity.ok(new AuthResponse(token));
        }
        return ResponseEntity.status(401).body("Invalid credentials");
    }
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String header) {

        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing token");
        }

        String token = header.substring(7);

        if (jwtService.isValid(token)) {
            // Extraemos el username del token
            String username = jwtService.extractUsername(token);
            // Devolvemos el nombre del usuario en lugar de "Valid"
            return ResponseEntity.ok(username);
        }

        return ResponseEntity.status(401).body("Invalid token");
    }
}
