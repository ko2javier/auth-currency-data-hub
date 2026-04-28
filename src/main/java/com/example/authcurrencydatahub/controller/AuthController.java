package com.example.authcurrencydatahub.controller;

import com.example.authcurrencydatahub.dto.AuthRequest;
import com.example.authcurrencydatahub.kafka.KafkaEventProducer;
import com.example.authcurrencydatahub.model.User;
import com.example.authcurrencydatahub.repository.UserRepository;
import com.example.authcurrencydatahub.service.AuthService;
import com.example.authcurrencydatahub.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthService authService;
    private final KafkaEventProducer kafkaEventProducer;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {

        Optional<User> userOptional = userRepository.findByUsername(request.getUsername());

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                List<String> roles = user.getRoles().stream()
                        .map(role -> role.getName())
                        .collect(Collectors.toList());

                String token = jwtService.generateToken(user.getUsername(), roles);

                String clientIp = resolveClientIp(httpRequest);
                kafkaEventProducer.publishUserLogin(user.getUsername(), clientIp);

                return ResponseEntity.ok("{\"token\": \"" + token + "\"}");
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales inválidas");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Missing token");
        }
        String token = header.substring(7);
        if (jwtService.isValid(token)) {
            String username = jwtService.extractUsername(token);
            return ResponseEntity.ok(username);
        }
        return ResponseEntity.status(401).body("Invalid token");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return ResponseEntity.ok("Sesión cerrada exitosamente y token revocado.");
    }
}