package upscale_project.UpscaleSPG.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import upscale_project.UpscaleSPG.exception.ImageProcessingException;
import upscale_project.UpscaleSPG.model.ImageStatus;
import upscale_project.UpscaleSPG.model.UpscalingMethod;

@Service
public class AsyncProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncProcessorService.class);

    private final ImageLifecycleService imageLifecycleService;
    private final UpscaleApiClient upscaleApiClient;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Autowired
    public AsyncProcessorService(
        ImageLifecycleService imageLifecycleService,
        UpscaleApiClient upscaleApiClient
    ) {
        this.imageLifecycleService = imageLifecycleService;
        this.upscaleApiClient = upscaleApiClient;
    }

    @Async
    public void startUpscalingProcess(Long imageId, String originalFilePathStr, UpscalingMethod model, int scale) {
        Path originalFilePath = Paths.get(originalFilePathStr);
        Path processedFilePath;
        try {
            processedFilePath = getProcessedFilePath(imageId, originalFilePathStr, model, scale);
            doUpscaleProcess(imageId, originalFilePath, processedFilePath, model, scale);
            imageLifecycleService.updateImageProcessingResult(imageId, processedFilePath.toString(), ImageStatus.PROCESSED);
        } catch (Exception e) {
            logger.error("Failed to process upscaling for image ID {}: {}", imageId, e.getMessage());
            imageLifecycleService.updateImageStatus(imageId, ImageStatus.ERROR);
        }
    }

    private Path getProcessedFilePath(Long imageId, String originalFilePath, UpscalingMethod model, int scale) {
        Path processedDir = Paths.get(uploadPath, "processed");
        if (!Files.exists(processedDir)) {
            try {
                Files.createDirectories(processedDir);
            } catch (IOException e) {
                logger.error("Failed to create processed directory {} for image ID {}: {}", processedDir, imageId, e.getMessage());
                throw new ImageProcessingException("Failed to create directory for processed images.", e);
            }
        }

        String filename = Paths.get(originalFilePath).getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        String processedFileName = filename.substring(0, dotIndex) + "_" + model + "_" + scale + "x" + filename.substring(dotIndex);

        return processedDir.resolve(processedFileName);
    }

    private void doUpscaleProcess(
        Long imageId,
        Path originalFilePath,
        Path processedFilePath,
        UpscalingMethod model,
        int scale
    ) {

        imageLifecycleService.updateImageStatus(imageId, ImageStatus.PROCESSING);

        byte[] processedBytes = upscaleApiClient.upscale(
            originalFilePath,
            model.toString().toLowerCase(),
            scale
        );
        
        try {
            Files.write(processedFilePath, processedBytes);
        } catch (IOException e) {
            throw new ImageProcessingException("Failed to write processed image to disk.", e);
        }

        logger.info("Image {} processing successful.", imageId);
    }
}