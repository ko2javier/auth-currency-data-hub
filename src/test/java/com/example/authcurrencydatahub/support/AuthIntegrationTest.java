package com.example.authcurrencydatahub.support;

import com.example.authcurrencydatahub.kafka.KafkaEventProducer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de los tests de integracion del servicio de auth. Levanta lo que la
 * aplicacion necesita de verdad y rellena las variables que
 * application.properties pide sin valor por defecto.
 *
 * <p>Por que hace falta: el unico test que habia, contextLoads, no podia
 * arrancar fuera del servidor (DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET,
 * GATEWAY_URL y REDIS_URL sin valor). Y ni siquiera llegaba a intentarlo:
 * faltaba el junit-platform-launcher en build.gradle, asi que Gradle moria
 * antes de entrar en ninguna clase.
 *
 * <p><b>MySQL y Redis son de verdad, en contenedores.</b> MySQL porque el login
 * lee el usuario y sus roles con JPA (una relacion ManyToMany que con un mock
 * no se ejecuta). Redis porque el logout escribe la lista negra que despues lee
 * el gateway: es el contrato entre los dos servicios, y con un mock se estaria
 * comprobando que el mock guarda lo que se le dice.
 *
 * <p><b>No hace falta un schema.sql</b>, a diferencia de pharma_back_py: este
 * servicio va con ddl-auto=update, asi que Hibernate crea las tres tablas
 * (users, roles, users_roles) en el MySQL del contenedor al arrancar.
 *
 * <p><b>Kafka no se levanta.</b> El servicio solo produce eventos (no hay
 * ningun @KafkaListener), asi que el contexto arranca sin broker. El productor
 * se sustituye por un doble: sin broker, el KafkaProducer de verdad se quedaria
 * bloqueado esperando metadatos (max.block.ms, 60 s por defecto) en cada
 * login. Y lo que interesa aqui no es Kafka, sino que el login lo llame.
 *
 * <p><b>Los contenedores arrancan en un bloque static y no con
 * {@code @Container}</b>: con la anotacion los arranca la extension de
 * Testcontainers, y su orden frente a la de Spring no esta garantizado. Si
 * Spring crea el contexto primero, {@link DynamicPropertySource} lee un puerto
 * que todavia no existe. Los para Ryuk al morir la JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AuthIntegrationTest {

    /**
     * 32 bytes como minimo: JwtService usa Keys.hmacShaKeyFor, que exige 256
     * bits para HS256 y con menos lanza WeakKeyException. No es el de
     * produccion.
     */
    protected static final String SECRETO = "secreto-de-test-con-mas-de-32-bytes!!";

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    /** Ver el comentario de la clase: sustituye al productor de verdad. */
    @MockBean
    protected KafkaEventProducer kafkaEventProducer;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("DB_URL", MYSQL::getJdbcUrl);
        registro.add("DB_USERNAME", MYSQL::getUsername);
        registro.add("DB_PASSWORD", MYSQL::getPassword);
        registro.add("REDIS_URL", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort());
        registro.add("JWT_SECRET", () -> SECRETO);
        // Solo lo usa springdoc para pintar la URL del servidor en el Swagger.
        registro.add("GATEWAY_URL", () -> "http://localhost");
    }
}
