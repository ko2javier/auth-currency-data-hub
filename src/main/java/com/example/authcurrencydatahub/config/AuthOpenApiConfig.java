package com.example.authcurrencydatahub.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Auth Service API",
                version = "1.0",
                description = "Servicio de Autenticación, Roles y Logout"
        ),
        // 🔥 IMPORTANTE: Apuntamos al Gateway (7000) para evitar líos de CORS y tokens
        servers = {
                @Server(url = "http://localhost:7000", description = "API Gateway")
        }
)
public class AuthOpenApiConfig {
}