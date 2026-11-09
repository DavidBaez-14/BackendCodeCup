package terminus.co.edu.ufps.codecup.identidad.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.api.Documentos;
import terminus.co.edu.ufps.codecup.identidad.api.VinculosPersona;
import terminus.co.edu.ufps.codecup.identidad.dto.DocumentoRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.MeResponseDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.RegistroRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.SolicitarRolRequestDTO;
import terminus.co.edu.ufps.codecup.identidad.dto.SolicitudRolResponseDTO;
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
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Cuentas de CodeCup sobre usuarios de Supabase Auth. Supabase autentica (correo
 * confirmado o Google) y este servicio solo guarda la cuenta, la persona y los roles.
 * Nunca llama a la API de Supabase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CuentaRepository cuentaRepository;
    private final CuentaRolRepository cuentaRolRepository;
    private final PersonaRepository personaRepository;
    private final RolesCuenta rolesCuenta;
    private final InvitacionService invitacionService;
    private final VinculosPersona vinculosPersona;

    @Transactional(readOnly = true)
    public MeResponseDTO me(String sub) {
        return toMe(cuentaDe(sub));
    }

    @Transactional
    public MeResponseDTO registrar(String sub, String correoDelToken, RegistroRequestDTO req) {
        if (cuentaRepository.existsByProveedorAndProveedorSub(Cuenta.PROVEEDOR_SUPABASE, sub)) {
            throw ApiException.conflict("CUENTA_YA_REGISTRADA", "Esta cuenta ya completó su registro.");
        }
        // Spec 0023: el documento es opcional, pero va completo (tipo y número) o no va.
        String numero = documentoCompleto(req.getTipoDocumento(), req.getNumeroDocumento());
        var existente = numero == null ? null : personaRepository.findFirstByNumeroDocumento(numero).orElse(null);
        if (existente != null && cuentaRepository.findByPersonaId(existente.getId()).isPresent()) {
            throw ApiException.conflict("DOCUMENTO_REGISTRADO",
                    "Ya existe una cuenta con este documento. Entra con la forma que usaste la primera vez (Google o tu correo y contraseña).");
        }

        // Si el documento es de un miembro sin cuenta (lo agregó un delegado), la cuenta recibe una
        // persona propia, sin documento ni historial, y se abre una solicitud de vínculo que aprueba
        // el delegado o un admin (spec 0023, D1). Que el documento coincida solo sugiere el vínculo.
        var persona = personaRepository.save(Persona.builder()
                .nombres(req.getNombres().trim())
                .apellidos(req.getApellidos().trim())
                .tipoDocumento(existente == null && numero != null ? req.getTipoDocumento() : null)
                .numeroDocumento(existente == null ? numero : null)
                .build());
        var cuenta = cuentaRepository.save(Cuenta.builder()
                .proveedor(Cuenta.PROVEEDOR_SUPABASE)
                .proveedorSub(sub)
                .correo(Documentos.normalizarCorreo(correoDelToken))
                .persona(persona)
                .estado(EstadoCuenta.ACTIVA)
                .fechaCreacion(LocalDateTime.now())
                .build());

        rolesCuenta.otorgar(cuenta, Rol.JUGADOR, "Registro");
        invitacionService.aplicarPendientes(cuenta);
        if (existente != null) {
            vinculosPersona.solicitar(cuenta.getId(), existente.getId());
        }

        log.info("[AUDIT][CUENTA_REGISTRADA] cuenta={}", cuenta.getId());
        return toMe(cuenta);
    }

    /** Resultado de agregar el documento: {@code vinculoAbierto} si el documento era de otra persona sin cuenta. */
    public record DocumentoAsignado(MeResponseDTO me, boolean vinculoAbierto) {
    }

    /**
     * Spec 0023: quien se registró sin documento lo agrega después (por ejemplo, porque un torneo
     * lo exige). Si el número es de una persona sin cuenta, se abre una solicitud de vínculo en
     * lugar de guardarlo, igual que en el registro.
     */
    @Transactional
    public DocumentoAsignado asignarDocumento(String sub, DocumentoRequestDTO req) {
        var cuenta = cuentaDe(sub);
        var persona = cuenta.getPersona();
        if (persona.getNumeroDocumento() != null) {
            throw ApiException.conflict("DOCUMENTO_YA_ASIGNADO",
                    "Ya tienes un documento registrado. Para cambiarlo, habla con un administrador.");
        }
        String numero = documentoCompleto(req.getTipoDocumento(), req.getNumeroDocumento());
        if (numero == null) {
            throw ApiException.badRequest("VALIDACION", "Escribe el tipo y el número de documento.");
        }
        var existente = personaRepository.findFirstByNumeroDocumento(numero).orElse(null);
        if (existente != null) {
            if (cuentaRepository.findByPersonaId(existente.getId()).isPresent()) {
                throw ApiException.conflict("DOCUMENTO_REGISTRADO", "Ya existe una cuenta con este documento.");
            }
            vinculosPersona.solicitar(cuenta.getId(), existente.getId());
            return new DocumentoAsignado(toMe(cuenta), true);
        }
        persona.setTipoDocumento(req.getTipoDocumento());
        persona.setNumeroDocumento(numero);
        personaRepository.save(persona);
        return new DocumentoAsignado(toMe(cuenta), false);
    }

    /** Número normalizado, o null si no llegó documento. Si llega solo el tipo o solo el número: 400. */
    private static String documentoCompleto(TipoDocumento tipo, String numeroEscrito) {
        String numero = Documentos.normalizar(numeroEscrito);
        boolean hayNumero = numeroEscrito != null && !numeroEscrito.isBlank();
        if (tipo == null && !hayNumero) return null;
        if (tipo == null || numero == null) {
            throw ApiException.badRequest("VALIDACION", "El documento va completo: tipo y número.");
        }
        return numero;
    }

    @Transactional
    public SolicitudRolResponseDTO solicitarRol(String sub, SolicitarRolRequestDTO req) {
        var cuenta = cuentaDe(sub);
        if (req.getRol() == Rol.ADMINISTRADOR || req.getRol() == Rol.SUPERADMIN || req.getRol() == Rol.JUGADOR) {
            throw ApiException.badRequest("ROL_NO_SOLICITABLE",
                    "Solo se puede solicitar DELEGADO o ARBITRO. JUGADOR ya lo tienes desde el registro.");
        }
        if (cuentaRolRepository.existsByCuentaIdAndRol(cuenta.getId(), req.getRol())) {
            throw ApiException.conflict("SOLICITUD_EXISTENTE", "Ya tienes una solicitud o el rol " + req.getRol() + ".");
        }

        cuentaRolRepository.save(CuentaRol.builder()
                .cuenta(cuenta)
                .rol(req.getRol())
                .estado(EstadoRol.PENDIENTE)
                .fechaSolicitud(LocalDateTime.now())
                .motivoSolicitud(trimOrNull(req.getMotivoSolicitud()))
                .build());

        return SolicitudRolResponseDTO.builder()
                .estado(EstadoRol.PENDIENTE.name())
                .mensaje("Solicitud enviada. Espera aprobación del administrador.")
                .build();
    }

    @Transactional
    public MeResponseDTO aceptarInvitacion(String sub, String correoDelToken, String token) {
        var cuenta = cuentaDe(sub);
        invitacionService.aceptar(token, cuenta, correoDelToken);
        return toMe(cuenta);
    }

    private Cuenta cuentaDe(String sub) {
        return cuentaRepository.findConPersona(Cuenta.PROVEEDOR_SUPABASE, sub)
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_REGISTRADA",
                        "Completa tu registro en CodeCup para continuar."));
    }

    private MeResponseDTO toMe(Cuenta cuenta) {
        var persona = cuenta.getPersona();
        return MeResponseDTO.builder()
                .cuentaId(cuenta.getId())
                .personaId(persona != null ? persona.getId() : null)
                .nombres(persona != null ? persona.getNombres() : null)
                .apellidos(persona != null ? persona.getApellidos() : null)
                .nombre(persona != null ? persona.nombreCompleto() : null)
                .correo(cuenta.getCorreo())
                .tipoDocumento(persona != null && persona.getTipoDocumento() != null
                        ? persona.getTipoDocumento().name() : null)
                .numeroDocumento(persona != null ? persona.getNumeroDocumento() : null)
                .estado(cuenta.getEstado().name())
                .roles(rolesCuenta.aprobados(cuenta))
                .rolesPendientes(rolesCuenta.pendientes(cuenta))
                .vinculoPendiente(vinculosPersona.pendienteDeCuenta(cuenta.getId())
                        .map(v -> new MeResponseDTO.VinculoPendiente(v.id(), v.personaId(), v.estado(), v.creadoEn()))
                        .orElse(null))
                .build();
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        var t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
