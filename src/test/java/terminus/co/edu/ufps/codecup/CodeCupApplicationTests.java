package terminus.co.edu.ufps.codecup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Criterio 1 de la spec 0015.
 *
 * <p>Este test parece trivial pero es el que sostiene la fusión: arranca el contexto
 * completo con MS2 + MS3 + MS4 en la misma JVM. Falla en rojo si vuelve a aparecer
 * un nombre de bean duplicado (el caso {@code globalExceptionHandler} / {@code ms2InternalClient}),
 * si se cuela un segundo DataSource, o si dos configuraciones de CORS se pisan.
 */
@SpringBootTest
@ActiveProfiles("test")
class CodeCupApplicationTests {

    @Test
    void contextLoads() {
        // Sin aserciones: el propio arranque del contexto es la verificación.
    }
}
