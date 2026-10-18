package com.example.authcurrencydatahub;

import com.example.authcurrencydatahub.support.AuthIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * El test que venia con el proyecto. Nunca llego a ejecutarse: faltaba el
 * junit-platform-launcher en build.gradle, asi que Gradle moria antes de
 * entrar en ninguna clase. Y de haber entrado, habria fallado igual, porque
 * application.properties pide la base de datos, Redis y el secreto sin valor
 * por defecto. Ahora todo eso sale de {@link AuthIntegrationTest}.
 */
class AuthCurrencyDataHubApplicationTests extends AuthIntegrationTest {

    @Test
    void contextLoads() {
    }
}
