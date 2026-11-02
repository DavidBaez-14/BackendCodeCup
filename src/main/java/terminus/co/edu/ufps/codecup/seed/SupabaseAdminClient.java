package terminus.co.edu.ufps.codecup.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Admin API de Supabase Auth, SOLO para el seed (perfil {@code seed}). La app nunca la usa:
 * el backend en ejecución solo valida tokens con el JWKS (spec 0017).
 *
 * <p>La clave secreta nueva ({@code sb_secret_…}) va únicamente en el header {@code apikey}:
 * no es un JWT y Supabase rechaza un {@code Authorization: Bearer} que no lo sea.
 *
 * <p>Nunca llama a signUp, invite ni recover: crea los usuarios ya confirmados
 * ({@code email_confirm: true}), así no sale ningún correo y no se gasta el cupo de Brevo.
 */
@Component
@Profile("seed")
public class SupabaseAdminClient implements CuentasDeSupabase {

    private static final int POR_PAGINA = 200;

    private final RestClient rest;
    private Map<String, String> idsPorCorreo;

    public SupabaseAdminClient(@Value("${supabase.url}") String supabaseUrl,
                               @Value("${supabase.secret-key:}") String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "El seed necesita SUPABASE_SECRET_KEY (sb_secret_…) en .env para crear los usuarios de prueba.");
        }
        this.rest = RestClient.builder()
                .baseUrl(supabaseUrl + "/auth/v1")
                .defaultHeader("apikey", secretKey)
                .build();
    }

    @Override
    public String asegurarUsuario(String correo, String contrasena, String nombreVisible) {
        String clave = correo.toLowerCase(Locale.ROOT);
        String existente = usuariosExistentes().get(clave);
        Map<String, Object> datos = Map.of("full_name", nombreVisible);

        if (existente != null) {
            rest.put().uri("/admin/users/{id}", existente)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("password", contrasena, "email_confirm", true, "user_metadata", datos))
                    .retrieve()
                    .toBodilessEntity();
            return existente;
        }

        try {
            JsonNode creado = rest.post().uri("/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", correo, "password", contrasena, "email_confirm", true, "user_metadata", datos))
                    .retrieve()
                    .body(JsonNode.class);
            String id = creado.get("id").asText();
            idsPorCorreo.put(clave, id);
            return id;
        } catch (HttpClientErrorException e) {
            // Lo crearon entre la lectura y ahora: se recarga la lista y se reutiliza.
            if (e.getStatusCode().value() != 422) throw e;
            idsPorCorreo = null;
            String id = usuariosExistentes().get(clave);
            if (id == null) throw e;
            return asegurarUsuario(correo, contrasena, nombreVisible);
        }
    }

    private Map<String, String> usuariosExistentes() {
        if (idsPorCorreo != null) return idsPorCorreo;
        Map<String, String> ids = new HashMap<>();
        for (int pagina = 1; pagina <= 50; pagina++) {
            JsonNode respuesta = rest.get()
                    .uri("/admin/users?page={p}&per_page={n}", pagina, POR_PAGINA)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode usuarios = respuesta == null ? null : respuesta.get("users");
            if (usuarios == null || usuarios.isEmpty()) break;
            usuarios.forEach(u -> ids.put(u.path("email").asText().toLowerCase(Locale.ROOT), u.path("id").asText()));
            if (usuarios.size() < POR_PAGINA) break;
        }
        idsPorCorreo = ids;
        return ids;
    }
}
