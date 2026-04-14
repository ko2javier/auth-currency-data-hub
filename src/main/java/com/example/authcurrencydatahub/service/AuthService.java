package com.example.authcurrencydatahub.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Date;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@Service
public class AuthService {

    private final StringRedisTemplate redisTemplate;

    // El secreto debe ser el mismo para poder leer la expiración
    private String secret = "my-super-secret-key-12345678901234567890";

    public AuthService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void logout(String token) {
        // 1. Limpiamos el prefijo "Bearer "
        String jwt = token.substring(7);

        // 2. Calculamos cuánto le queda de vida al token
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secret.getBytes())
                .build()
                .parseClaimsJws(jwt)
                .getBody();

        Date expiration = claims.getExpiration();
        long diff = expiration.getTime() - System.currentTimeMillis();

        // 3. Si el token aún no ha muerto, lo metemos en la Lista Negra
        if (diff > 0) {
            redisTemplate.opsForValue().set(jwt, "revoked", Duration.ofMillis(diff));
        }
    }
}