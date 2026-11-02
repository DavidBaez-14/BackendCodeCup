package terminus.co.edu.ufps.codecup.finanzas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Supabase Storage con la clave secreta nueva ({@code sb_secret_...}). Esa clave no
 * es un JWT: va solo en el header {@code apikey}. Supabase rechaza un
 * {@code Authorization: Bearer} que no sea un JWT (spec 0017).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.secret-key:}")
    private String secretKey;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    public UploadUrlResult generarUploadUrl(UUID equipoTorneoId) {
        // Clave determinista: un solo archivo por inscripcion. Al re-subir se sobrescribe
        // (el PUT del cliente envia x-upsert: true), evitando duplicados/huerfanos en Storage.
        String fileKey = equipoTorneoId + "/comprobante";

        try {
            var response = restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/upload/sign/" + bucket + "/" + fileKey,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(), headers()),
                    String.class);

            JsonNode json = objectMapper.readTree(response.getBody());
            String url = json.get("url").asText();
            return new UploadUrlResult(toAbsoluteUrl(url), fileKey);
        } catch (StorageNoConfiguradoException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generando upload URL para {}: {}", fileKey, e.getMessage());
            throw new RuntimeException("No se pudo generar URL de subida del comprobante: " + e.getMessage(), e);
        }
    }

    public String generarDownloadUrl(String fileKey) {
        int expiresIn = 3600;
        var body = Map.of("expiresIn", expiresIn);

        try {
            var response = restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/sign/" + bucket + "/" + fileKey,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers()),
                    String.class);

            JsonNode json = objectMapper.readTree(response.getBody());
            String signed = json.has("signedURL")
                    ? json.get("signedURL").asText()
                    : json.get("signedUrl").asText();
            return toAbsoluteUrl(signed);
        } catch (StorageNoConfiguradoException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generando download URL para {}: {}", fileKey, e.getMessage());
            throw new RuntimeException("No se pudo generar URL de descarga: " + e.getMessage(), e);
        }
    }

    private String toAbsoluteUrl(String url) {
        if (url == null || url.isBlank()) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/storage/v1")) return supabaseUrl + url;
        if (url.startsWith("/")) return supabaseUrl + "/storage/v1" + url;
        return supabaseUrl + "/storage/v1/" + url;
    }

    public boolean archivoExiste(String fileKey) {
        try {
            var response = restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/info/" + bucket + "/" + fileKey,
                    HttpMethod.HEAD,
                    new HttpEntity<>(headers()),
                    Void.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (StorageNoConfiguradoException e) {
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    private HttpHeaders headers() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new StorageNoConfiguradoException();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public record UploadUrlResult(String uploadUrl, String fileKey) {}

    /** Falta SUPABASE_SECRET_KEY en el .env: mejor un mensaje claro que un 401 de Supabase. */
    public static class StorageNoConfiguradoException extends RuntimeException {
        public StorageNoConfiguradoException() {
            super("Storage no configurado: falta SUPABASE_SECRET_KEY en el .env del backend.");
        }
    }
}
