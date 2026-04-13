package com.example.authcurrencydatahub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class AuthCurrencyDataHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthCurrencyDataHubApplication.class, args);
    }

}
