package terminus.co.edu.ufps.codecup.identidad.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;

/**
 * Spec 0017, criterio 16. Reemplaza a Ms1JugadoresClient: la competición busca una
 * cédula y recibe la persona registrada, sin HTTP.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DirectorioPersonas.class)
class DirectorioPersonasTest {

    @Autowired DirectorioPersonas directorio;
    @Autowired PersonaRepository personaRepository;
    @Autowired CuentaRepository cuentaRepository;

    @Test
    void buscaPorDocumento_normalizadoYConCorreoDeLaCuenta() {
        var persona = personaRepository.save(Persona.builder()
                .nombres("Luis").apellidos("Gómez")
                .tipoDocumento(TipoDocumento.CC).numeroDocumento("1090777888")
                .build());
        cuentaRepository.save(Cuenta.builder()
                .proveedor(Cuenta.PROVEEDOR_SUPABASE).proveedorSub(UUID.randomUUID().toString())
                .correo("luis@correo.test").persona(persona)
                .estado(EstadoCuenta.ACTIVA).fechaCreacion(LocalDateTime.now())
                .build());

        var encontrado = directorio.buscarPorDocumento(" 1.090.777.888 ").orElseThrow();

        assertThat(encontrado.personaId()).isEqualTo(persona.getId());
        assertThat(encontrado.nombreCompleto()).isEqualTo("Luis Gómez");
        assertThat(encontrado.correo()).isEqualTo("luis@correo.test");
    }

    @Test
    void personaSinCuenta_vieneSinCorreo_yDesconocidaVacia() {
        personaRepository.save(Persona.builder()
                .nombres("Sofía").tipoDocumento(TipoDocumento.TI).numeroDocumento("1001002003").build());

        assertThat(directorio.buscarPorDocumento("1001002003")).get()
                .extracting(PersonaResumen::correo).isNull();
        assertThat(directorio.buscarPorDocumento("5550001112")).isEmpty();
        assertThat(directorio.buscarPorDocumento("  ")).isEmpty();
    }
}
