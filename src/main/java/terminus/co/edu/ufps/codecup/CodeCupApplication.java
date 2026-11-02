package terminus.co.edu.ufps.codecup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// @EnableAsync lo necesita analytics (reconstrucción de snapshots fuera del hilo del request).
@EnableAsync
@SpringBootApplication
public class CodeCupApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeCupApplication.class, args);
    }
}
