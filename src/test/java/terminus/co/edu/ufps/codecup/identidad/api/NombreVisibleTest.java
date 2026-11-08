package terminus.co.edu.ufps.codecup.identidad.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

/** Spec 0022, criterio 15: el nombre de una persona en los endpoints públicos. */
class NombreVisibleTest {

    @Test
    void adulto_nombreCompleto() {
        assertThat(NombreVisible.de(TipoDocumento.CC, "Ana María", "Pérez Gómez")).isEqualTo("Ana María Pérez Gómez");
        assertThat(NombreVisible.de(TipoDocumento.PASAPORTE, "John", null)).isEqualTo("John");
    }

    @Test
    void sinDocumento_nombreCompleto() {
        assertThat(NombreVisible.de(null, "Pedro", "Invitado")).isEqualTo("Pedro Invitado");
    }

    @Test
    void menorConTI_nombresEInicialDelApellido() {
        assertThat(NombreVisible.de(TipoDocumento.TI, "Tomás Andrés", "niño Rojas")).isEqualTo("Tomás Andrés N.");
    }

    @Test
    void menorConTI_sinApellidosAparte_primeraPalabraEInicialDeLaSiguiente() {
        // Un miembro sin cuenta: el delegado escribió todo el nombre en un solo campo.
        assertThat(NombreVisible.de(TipoDocumento.TI, "  Tomás   Niño Rojas ", null)).isEqualTo("Tomás N.");
        assertThat(NombreVisible.de(TipoDocumento.TI, "Tomás", null)).isEqualTo("Tomás");
    }
}
