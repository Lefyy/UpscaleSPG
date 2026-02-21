package upscale_project.UpscaleSPG.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate upscaleRestTemplate(
        @Value("${app.upscale.api.connect-timeout-ms:2000}") int connectTimeoutMs,
        @Value("${app.upscale.api.read-timeout-ms:120000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return new RestTemplate(requestFactory);
    }
    
}
