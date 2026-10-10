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
                                .description("""
                                        **Start here.** This is where you get the JWT that the other two definitions ask for.

                                        Run `POST /auth/login` with one of the demo accounts below, copy the `token` from the response, then switch definition in the **selector at the top right** and click **Authorize**.

                                        Demo accounts: `user` / `user123` (ROLE_USER, weather and currency) and `admin` / `admin123` (ROLE_ADMIN, also the audit endpoints).

                                        `POST /auth/logout` revokes a token server-side by writing it to a Redis blacklist, using the token's own remaining lifetime as the key TTL. It stops working immediately instead of at expiry, and the blacklist cleans itself.
                                        """))
                        .addServersItem(new Server()
                                .url(gatewayUrl)
                                .description("API Gateway"));
        }
}