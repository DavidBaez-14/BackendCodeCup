package terminus.co.edu.ufps.codecup.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger con botón "Authorize": se pega el access token de Supabase. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI codecupOpenApi() {
        return new OpenAPI()
                .info(new Info().title("BackendCodeCup API").version("v1"))
                .components(new Components().addSecuritySchemes("supabase",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("supabase"));
    }
}
