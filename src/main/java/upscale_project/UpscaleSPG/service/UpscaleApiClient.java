package upscale_project.UpscaleSPG.service;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import upscale_project.UpscaleSPG.exception.ImageProcessingException;


@Service
public class UpscaleApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(UpscaleApiClient.class);

    private final RestTemplate restTemplate;
    private final String upscaleApiUrl;
    private final int maxRetries;

    public UpscaleApiClient(
        @Qualifier("upscaleRestTemplate") RestTemplate restTemplate,
        @Value("${app.upscale.api.url:http://localhost:8000/upscale}") String upscaleApiUrl,
        @Value("${app.upscale.api.max-retries:2}") int maxRetries
    ) {
        this.restTemplate = restTemplate;
        this.upscaleApiUrl = upscaleApiUrl;
        this.maxRetries = Math.max(0, maxRetries);
    }

        public byte[] upscale(Path inputFilePath, String modelName, int scale) {
        int attempts = maxRetries + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new FileSystemResource(inputFilePath));
                body.add("model_name", modelName);
                body.add("scale", String.valueOf(scale));

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                ResponseEntity<byte[]> response = restTemplate.exchange(
                    upscaleApiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
                );

                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new ImageProcessingException("Upscale API returned HTTP " + response.getStatusCode().value());
                }

                byte[] responseBody = response.getBody();
                if (responseBody == null || responseBody.length == 0) {
                    throw new ImageProcessingException("Upscale API returned empty image content.");
                }

                logger.info("Upscale API succeeded for model '{}' and scale x{}.", modelName, scale);
                return responseBody;
            } catch (ResourceAccessException e) {
                if (attempt == attempts) {
                    throw new ImageProcessingException("Upscale API is unavailable after retries.", e);
                }
                logger.warn("Upscale API connection failed (attempt {}/{}). Retrying...", attempt, attempts);
                sleepBackoff(attempt);
            } catch (RestClientException e) {
                throw new ImageProcessingException("Upscale API call failed.", e);
            }
        }

        throw new ImageProcessingException("Upscale API failed unexpectedly.");
    }

    private void sleepBackoff(int attempt) {
        try {
            TimeUnit.MILLISECONDS.sleep(200L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageProcessingException("Thread interrupted during API retry backoff.", e);
        }
    }

}
