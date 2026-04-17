package com.example.authcurrencydatahub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthOpenApiConfig {

        @Value("${GATEWAY_URL:http://localhost:7000}")
        private String gatewayUrl;

        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                        .info(new Info()
                                .title("Auth Service API")
                                .version("1.0")
                                .description("Servicio de Autenticación, Roles y Logout"))
                        .addServersItem(new Server()
                                .url(gatewayUrl)
                                .description("API Gateway"));
        }
}