package terminus.co.edu.ufps.codecup.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger con botón "Authorize": se pega el access token de Supabase. */
@Configuration
public class OpenApiConfig {

    static final String TAG_TOKEN = "Token de prueba (Supabase)";

    @Bean
    public OpenAPI codecupOpenApi() {
        return new OpenAPI()
                .info(new Info().title("BackendCodeCup API").version("v1"))
                .components(new Components().addSecuritySchemes("supabase",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("supabase"));
    }

    /**
     * Agrega a Swagger, como primera sección, la operación de Supabase Auth que
     * entrega el access token ({@code POST /auth/v1/token?grant_type=password}).
     * El navegador la llama directo a Supabase, igual que lo harán la web y el
     * móvil: el backend no participa (nunca llama a la API del proveedor).
     */
    @Bean
    public OpenApiCustomizer tokenDePruebaDeSupabase(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.publishable-key:}") String publishableKey) {
        return openApi -> {
            var apikey = new Parameter()
                    .in("header").name("apikey").required(true)
                    .description("Publishable key de Supabase (sb_publishable_…). Es pública. "
                            + "Si pones SUPABASE_PUBLISHABLE_KEY en el .env, viene llena.")
                    .schema(new StringSchema()._default(publishableKey.isBlank() ? null : publishableKey));
            var grantType = new Parameter()
                    .in("query").name("grant_type").required(true)
                    .schema(new StringSchema()._enum(List.of("password"))._default("password"));
            var credenciales = new ObjectSchema()
                    .addProperty("email", new StringSchema().example("tu@correo.com"))
                    .addProperty("password", new StringSchema().example("tu-contraseña"));

            var operacion = new Operation()
                    .tags(List.of(TAG_TOKEN))
                    .summary("Pedirle a Supabase un access token con correo y contraseña")
                    .description("""
                            1. Escribe tu correo y contraseña de Supabase y pulsa Execute.
                            2. Copia el valor de `access_token` de la respuesta (sin comillas).
                            3. Pulsa **Authorize** (arriba), pégalo y confirma.

                            El token dura 1 hora. Esta petición va directo a Supabase, no al backend.""")
                    .addParametersItem(grantType)
                    .addParametersItem(apikey)
                    .requestBody(new RequestBody().required(true).content(new Content()
                            .addMediaType("application/json", new MediaType().schema(credenciales))))
                    // Sin el Bearer global: esta petición se hace antes de tener token.
                    .security(List.of())
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("Trae access_token, expires_in y refresh_token."))
                            .addApiResponse("400", new ApiResponse().description("Correo o contraseña incorrectos, o correo sin confirmar.")));

            openApi.path("/auth/v1/token", new PathItem()
                    .servers(List.of(new Server().url(supabaseUrl).description("Supabase Auth")))
                    .post(operacion));

            // La primera sección de Swagger: es lo primero que se usa.
            List<Tag> tags = openApi.getTags() == null ? new ArrayList<>() : new ArrayList<>(openApi.getTags());
            tags.add(0, new Tag().name(TAG_TOKEN).description("Para probar el API sin la web: primero pide aquí el token."));
            openApi.setTags(tags);
        };
    }
}
