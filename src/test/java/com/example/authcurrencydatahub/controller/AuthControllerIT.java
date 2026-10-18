package com.example.authcurrencydatahub.controller;

import com.example.authcurrencydatahub.support.AuthIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * El servicio que emite los tokens de todo el Hub, contra un MySQL y un Redis
 * de verdad. Las peticiones van por HTTP al puerto real (RANDOM_PORT), asi que
 * atraviesan la cadena de seguridad de Spring entera.
 */
class AuthControllerIT extends AuthIntegrationTest {

    private static final String USUARIO = "jabier";
    private static final String CLAVE = "clave-correcta";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redis;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Cada test parte de un unico usuario con ROLE_USER. Con JdbcTemplate y no
     * con repositorios porque no hay RoleRepository: los roles solo existen
     * como tabla. Los nombres de tablas y columnas son los que crea
     * ddl-auto=update a partir de User y Role.
     */
    @BeforeEach
    void unUsuarioConSuRol() {
        jdbc.update("DELETE FROM users_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM roles");
        crearUsuario(USUARIO, CLAVE, true);
    }

    private void crearUsuario(String usuario, String clave, boolean habilitado) {
        jdbc.update("INSERT IGNORE INTO roles (name) VALUES ('ROLE_USER')");
        Long rol = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'ROLE_USER'", Long.class);
        // El hash lo hace el MISMO PasswordEncoder que usa el login (BCrypt), asi
        // que si alguien cambiara el algoritmo, este test lo notaria.
        jdbc.update("INSERT INTO users (username, password, enabled) VALUES (?, ?, ?)",
                usuario, passwordEncoder.encode(clave), habilitado);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, usuario);
        jdbc.update("INSERT INTO users_roles (user_id, role_id) VALUES (?, ?)", id, rol);
    }

    private ResponseEntity<String> login(String usuario, String clave, HttpHeaders extra) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        if (extra != null) {
            cabeceras.putAll(extra);
        }
        return http.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("username", usuario, "password", clave), cabeceras),
                String.class);
    }

    private String tokenDe(ResponseEntity<String> respuesta) throws Exception {
        // El controlador devuelve el JSON escrito a mano como String
        // ("{\"token\": \"...\"}"), no un objeto: se parsea aqui para que el
        // test falle si esa cadena deja de ser JSON valido.
        JsonNode cuerpo = json.readTree(respuesta.getBody());
        return cuerpo.get("token").asText();
    }

    private Claims leer(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // -------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------
    @Test
    void un_login_correcto_devuelve_un_token_con_el_usuario_y_sus_roles() throws Exception {
        ResponseEntity<String> r = login(USUARIO, CLAVE, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        Claims claims = leer(tokenDe(r));
        assertThat(claims.getSubject()).isEqualTo(USUARIO);
        // El gateway lee este claim con claims.get("roles", List.class) y lo
        // reenvia como X-User-Roles. Si cambiara de nombre o de forma, los
        // servicios de detras se quedarian sin roles sin que nada fallase.
        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_USER");
        // JwtService.EXPIRATION: una hora.
        long vida = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(vida).isEqualTo(3_600_000L);
    }

    @Test
    void el_login_publica_el_evento_con_la_ip_real_del_cliente() {
        // Detras del gateway, getRemoteAddr() es la IP del gateway. La del
        // cliente viene en la primera entrada de X-Forwarded-For.
        HttpHeaders extra = new HttpHeaders();
        extra.set("X-Forwarded-For", "203.0.113.7, 10.0.0.1");

        login(USUARIO, CLAVE, extra);

        verify(kafkaEventProducer).publishUserLogin(USUARIO, "203.0.113.7");
    }

    @Test
    void una_clave_mala_devuelve_401_y_no_publica_nada() {
        ResponseEntity<String> r = login(USUARIO, "clave-mala", null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).isEqualTo("Credenciales inválidas");
        verify(kafkaEventProducer, never()).publishUserLogin(anyString(), anyString());
    }

    @Test
    void un_usuario_que_no_existe_recibe_la_misma_respuesta_que_una_clave_mala() {
        // Si las dos respuestas fueran distintas, el login diria que usuarios
        // existen a quien pruebe nombres.
        ResponseEntity<String> r = login("nadie", CLAVE, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).isEqualTo("Credenciales inválidas");
    }

    /**
     * CARACTERIZACION: fija lo que hace HOY, no lo que deberia.
     *
     * <p>User tiene un campo enabled, pero el login no lo mira
     * (AuthController.java:34-39): busca el usuario, compara la clave y emite el
     * token. Un usuario deshabilitado entra igual. Queda apuntado como deuda; el
     * dia que se arregle, este test se pone rojo, que es lo que se busca.
     */
    @Test
    void un_usuario_deshabilitado_PUEDE_hacer_login() {
        crearUsuario("deshabilitado", CLAVE, false);

        ResponseEntity<String> r = login("deshabilitado", CLAVE, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------------
    // Logout: el contrato con el gateway
    // -------------------------------------------------------------------
    @Test
    void el_logout_escribe_el_token_en_la_lista_negra_como_lo_lee_el_gateway() throws Exception {
        String token = tokenDe(login(USUARIO, CLAVE, null));
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("Authorization", "Bearer " + token);

        ResponseEntity<String> r = http.postForEntity("/auth/logout", new HttpEntity<>(cabeceras), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        // ESTE es el contrato entre los dos servicios. El gateway
        // (JwtAuthFilter.java:57 en api-gateway-currency-data-hub) pregunta
        // hasKey(token) con el token SIN el prefijo "Bearer ". Si el logout lo
        // guardara con el prefijo, o con otra clave, el gateway no lo
        // encontraria y el token revocado seguiria valiendo -- sin un solo
        // error en ninguno de los dos servicios.
        assertThat(redis.hasKey(token)).isTrue();
        assertThat(redis.hasKey("Bearer " + token)).isFalse();
        assertThat(redis.opsForValue().get(token)).isEqualTo("revoked");
        // El TTL es lo que le queda de vida al token: pasado ese tiempo, el token
        // ya no vale por caducado y no hace falta seguir guardandolo.
        assertThat(redis.getExpire(token)).isBetween(3_500L, 3_600L);
    }

    // -------------------------------------------------------------------
    // Validate
    // -------------------------------------------------------------------
    @Test
    void validate_con_un_token_bueno_devuelve_el_usuario() throws Exception {
        String token = tokenDe(login(USUARIO, CLAVE, null));
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("Authorization", "Bearer " + token);

        ResponseEntity<String> r = http.postForEntity("/auth/validate", new HttpEntity<>(cabeceras), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(USUARIO);
    }

    @Test
    void validate_con_un_token_falsificado_devuelve_401() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("Authorization", "Bearer esto.no.es-un-token");

        ResponseEntity<String> r = http.postForEntity("/auth/validate", new HttpEntity<>(cabeceras), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).isEqualTo("Invalid token");
    }
}
