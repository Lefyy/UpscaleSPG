// src/main/java/upscale_project/UpscaleSPG/service/AsyncProcessorService.java
package upscale_project.UpscaleSPG.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import upscale_project.UpscaleSPG.exception.ImageNotFoundException;
import upscale_project.UpscaleSPG.exception.ImageProcessingException;
import upscale_project.UpscaleSPG.model.Image;
import upscale_project.UpscaleSPG.model.ImageStatus;
import upscale_project.UpscaleSPG.model.UpscalingMethod;
import upscale_project.UpscaleSPG.repository.ImageRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Service
public class AsyncProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncProcessorService.class);

    private final ImageRepository imageRepository;
    private final ImageService imageService;
    private final Environment env;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${app.scripts.path}")
    private String scriptsPath;

    @Value("${app.python.executable.path}")
    private String pythonExecutablePath;

    @Autowired
    public AsyncProcessorService(ImageRepository imageRepository, Environment env, @Lazy ImageService imageService) {
        this.imageRepository = imageRepository;
        this.env = env;
        this.imageService = imageService;
    }

    @Async
    public void startUpscalingProcess(Long imageId, String originalFilePathStr, String originalFileName, UpscalingMethod model, int scale) {
        String modelWeightsPath;
        Path originalFilePath = Paths.get(originalFilePathStr);
        Path processedFilePath;
        try {
            modelWeightsPath = getModelWeightsPath(imageId, model, scale);
            processedFilePath = getProcessedFilePath(imageId, originalFilePathStr, model, scale);
            doUpscaleProcess(imageId, originalFilePath, processedFilePath, modelWeightsPath, model, scale);
            imageService.updateImageProcessingResult(imageId, processedFilePath.toString(), ImageStatus.PROCESSED);
        } catch (Exception e) {
            logger.error("Failed to process upscaling for image ID {}: {}", imageId, e);
            updateImageStatusToError(imageId);
        }
    }

    private String getModelWeightsPath(Long imageId, UpscalingMethod model, int scale) {
        String modelWeightsPath;
        if (model == UpscalingMethod.BILINEAR || model == UpscalingMethod.BICUBIC) {
            modelWeightsPath = "none";
            logger.info("Using interpolation method '{}' for image ID {}. No model weights needed.", model, imageId);
        } else {
            String modelWeightsPropertyKey = "app.weights.path." + model + ".scale" + scale;
            modelWeightsPath = env.getProperty(modelWeightsPropertyKey);

            if (modelWeightsPath == null) {
                logger.error("Model weights path not found for model: {} and scale: {} (key: {}) for image ID {}", model, scale, modelWeightsPropertyKey, imageId);
                throw new ImageProcessingException("Model weights not configured for the selected model and scale.");
            }
        }
        return modelWeightsPath;
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
        String processedFileName = Paths.get(originalFilePath).getFileName().toString() + "_" + model + "_" + Integer.toString(scale) ;
        Path processedFilePath = processedDir.resolve(processedFileName);

        return processedFilePath;
    }

    private void doUpscaleProcess(Long imageId, Path originalFilePath, Path processedFilePath, 
                                String modelWeightsPath, UpscalingMethod model, int scale) {
        
        int exitCode;
        
        try {
            updateImageStatusToProcessing(imageId);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutablePath,
                    Paths.get(scriptsPath, "upscale_image.py").toString(),
                    originalFilePath.toString(),
                    processedFilePath.toString(),
                    modelWeightsPath,
                    model.toString(),
                    String.valueOf(scale)
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();
            printProcessInput(process);
            exitCode = process.waitFor();
            logger.info("Python script finished with exit code: {} for image ID {}", exitCode, imageId);

        } catch (IOException | InterruptedException e) {
            logger.error("Error during Python script execution for image ID {}: {}", imageId, e.getMessage());
            throw new ImageProcessingException("Failed to execute upscaling script.", e);
        }

        if (exitCode == 0) {
            logger.info("Image {} processing successful.", imageId);
        } else {
            logger.error("Image {} processing failed with exit code: {}", imageId, exitCode);
            throw new ImageProcessingException("Upscaling script failed with non-zero exit code: " + exitCode);
        }
    }

    private void updateImageStatusToProcessing(Long imageId) {
        Image imageToUpdate = imageRepository.findById(imageId)
                        .orElseThrow(() -> new ImageNotFoundException("Image not found for updating status to 'processing': " + imageId));
        imageToUpdate.setStatus(ImageStatus.PROCESSING);
        imageToUpdate.setProcessStartTime(LocalDateTime.now());
        imageRepository.save(imageToUpdate);
        logger.info("Image {} status updated to 'processing'.", imageId);
    }

    private void printProcessInput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("Python output: {}", line);
            }
        } catch (IOException e) {
            logger.error("Failed to read Python script output: {}", e.getMessage());
            throw new ImageProcessingException("Failed to read output from upscaling script.", e);
        }
    }

    private void updateImageStatusToError(Long imageId) {
        try {
            imageService.updateImageProcessingResult(imageId, null, ImageStatus.ERROR);
        } catch (Exception e) {
            logger.error("Failed to update status to error for image {}: {}", imageId, e.getMessage());
        }
    }
}