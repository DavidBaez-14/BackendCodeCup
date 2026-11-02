package terminus.co.edu.ufps.codecup.finanzas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class StorageConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
