package terminus.co.edu.ufps.codecup.finanzas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlResponse {
    private String uploadUrl;
    private String fileKey;
}
