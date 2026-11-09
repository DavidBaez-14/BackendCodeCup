package terminus.co.edu.ufps.codecup.identidad;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.CuentaRol;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;

/**
 * Base de los tests de autenticación. El {@link JwtDecoder} es un mock: cada test
 * "emite" un access token de Supabase con los claims que quiera, y el resto de la
 * cadena (converter, roles desde la BD, ProblemDetail) es la real.
 *
 * <p>Cada test usa subs, correos y documentos al azar porque el contexto (y su H2)
 * se comparte entre clases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AuthTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CuentaRepository cuentaRepository;

    @Autowired
    protected CuentaRolRepository cuentaRolRepository;

    @Autowired
    protected PersonaRepository personaRepository;

    @MockBean
    protected JwtDecoder jwtDecoder;

    /** Un usuario de Supabase (sub + correo) y su header Authorization. */
    protected record Usuario(String sub, String correo, String bearer) {
    }

    protected Usuario usuario() {
        return usuario(Map.of());
    }

    protected Usuario usuario(Map<String, Object> claimsExtra) {
        String sub = UUID.randomUUID().toString();
        return usuario(sub, "u" + sub.substring(0, 8) + "@correo.test", claimsExtra);
    }

    protected Usuario usuario(String sub, String correo, Map<String, Object> claimsExtra) {
        String token = "token-" + sub;
        Instant ahora = Instant.now();
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "ES256")
                .subject(sub)
                .issuer("http://localhost:0/auth/v1")
                .audience(List.of("authenticated"))
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(3600))
                .claim("email", correo)
                .claim("role", "authenticated")
                .claims(c -> c.putAll(claimsExtra))
                .build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
        return new Usuario(sub, correo, "Bearer " + token);
    }

    protected static String documentoAlAzar() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
    }

    /** POST /api/auth/registro con un documento nuevo. Devuelve el número usado. */
    protected String registrar(Usuario u) throws Exception {
        String doc = documentoAlAzar();
        registrar(u, "CC", doc).andExpect(status().isCreated());
        return doc;
    }

    protected org.springframework.test.web.servlet.ResultActions registrar(Usuario u, String tipo, String doc)
            throws Exception {
        var body = Map.of("nombres", "Ana María", "apellidos", "Pérez", "tipoDocumento", tipo, "numeroDocumento", doc);
        return mvc.perform(post("/api/auth/registro")
                .header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * Crea directo en la BD una cuenta con los roles dados (sin pasar por el API). Como en
     * producción (RolesCuenta.otorgar), ADMINISTRADOR trae SUPERADMIN: el panel de Usuarios.
     * Desde la spec 0024 ningún rol global da acceso a un torneo: usa {@link #hacerAdmin}.
     */
    protected Usuario cuentaConRoles(Rol... roles) {
        var lista = new java.util.ArrayList<>(java.util.Arrays.asList(roles));
        if (lista.contains(Rol.ADMINISTRADOR) && !lista.contains(Rol.SUPERADMIN)) {
            lista.add(Rol.SUPERADMIN);
        }
        return cuentaConRolesExactos(lista.toArray(Rol[]::new));
    }

    @Autowired
    protected terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository soporteTorneos;

    @Autowired
    protected terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository soporteRolesTorneo;

    @Autowired
    protected terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository soportePartidos;

    protected UUID cuentaIdDe(Usuario u) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, u.sub()).orElseThrow().getId();
    }

    /** Spec 0024: hace a la cuenta admin de ese torneo. */
    protected void hacerAdmin(Usuario u, UUID torneoId) {
        darRolDeTorneo(u, torneoId, terminus.co.edu.ufps.codecup.competicion.model.RolTorneo.ADMIN);
    }

    protected terminus.co.edu.ufps.codecup.competicion.model.TorneoRol darRolDeTorneo(
            Usuario u, UUID torneoId, terminus.co.edu.ufps.codecup.competicion.model.RolTorneo rol) {
        return soporteRolesTorneo.save(terminus.co.edu.ufps.codecup.competicion.model.TorneoRol.builder()
                .torneo(soporteTorneos.getReferenceById(torneoId))
                .cuentaId(cuentaIdDe(u))
                .rol(rol)
                .creadoEn(LocalDateTime.now())
                .build());
    }

    /** Un torneo mínimo, guardado directo en la BD. */
    protected terminus.co.edu.ufps.codecup.competicion.model.Torneo torneoDePrueba(
            terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo estado) {
        return soporteTorneos.save(terminus.co.edu.ufps.codecup.competicion.model.Torneo.builder()
                .nombre("Torneo " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(estado)
                .build());
    }

    /** Un partido sin equipos (basta para probar permisos). */
    protected terminus.co.edu.ufps.codecup.competicion.model.Partido partidoDePrueba(
            terminus.co.edu.ufps.codecup.competicion.model.Torneo torneo) {
        return soportePartidos.save(terminus.co.edu.ufps.codecup.competicion.model.Partido.builder()
                .torneo(torneo)
                .fecha(LocalDateTime.now().plusDays(1))
                .build());
    }

    /** Como {@link #cuentaConRoles}, pero sin agregar nada. */
    protected Usuario cuentaConRolesExactos(Rol... roles) {
        Usuario u = usuario();
        var persona = personaRepository.save(Persona.builder()
                .nombres("Admin").apellidos("Prueba")
                .tipoDocumento(TipoDocumento.CC).numeroDocumento(documentoAlAzar())
                .build());
        var cuenta = cuentaRepository.save(Cuenta.builder()
                .proveedor(Cuenta.PROVEEDOR_SUPABASE).proveedorSub(u.sub()).correo(u.correo())
                .persona(persona).estado(EstadoCuenta.ACTIVA).fechaCreacion(LocalDateTime.now())
                .build());
        for (Rol rol : roles) {
            cuentaRolRepository.save(CuentaRol.builder()
                    .cuenta(cuenta).rol(rol).estado(EstadoRol.APROBADO)
                    .fechaSolicitud(LocalDateTime.now()).fechaResolucion(LocalDateTime.now())
                    .build());
        }
        return u;
    }

    protected String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }
}
