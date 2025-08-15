// src/main/java/upscale_project/UpscaleSPG/service/AsyncProcessorService.java
package upscale_project.UpscaleSPG.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import upscale_project.UpscaleSPG.model.Image;
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
    public void startUpscalingProcess(Long imageId, String originalFilePathStr, String originalFileName, String model, int scale) {
        String modelWeightsPath;
        Path originalFilePath = Paths.get(originalFilePathStr);
        Path processedFilePath;
        try {
            modelWeightsPath = getModelWeightsPath(imageId, model, scale);
            processedFilePath = getProcessedFilePath(imageId, originalFileName, modelWeightsPath, scale);
            doUpscaleProcess(imageId, originalFilePath, processedFilePath, modelWeightsPath, modelWeightsPath, scale);
            imageService.updateImageProcessingResult(imageId, processedFilePath.toString(), "processed");
        } catch (Exception e) {
            updateImageStatusToError(imageId);
            return;
        }
    }

    private String getModelWeightsPath(Long imageId, String model, int scale) throws Exception {
        String modelWeightsPath;
        if (model.equalsIgnoreCase("bilinear") || model.equalsIgnoreCase("bicubic")) {
            modelWeightsPath = "none";
            System.out.println("Info: Using interpolation method '" + model + "'. No model weights needed.");
        } else {
            String modelWeightsPropertyKey = "app.weights.path." + model + ".scale" + scale;
            modelWeightsPath = env.getProperty(modelWeightsPropertyKey);

            if (modelWeightsPath == null) {
                System.err.println("Error: Model weights path not found for model: " + model + " and scale: " + scale + " (key: " + modelWeightsPropertyKey + ")");
                throw new Exception();
            }
        }
        return modelWeightsPath;
    }

    private Path getProcessedFilePath(Long imageId, String originalFileName, String model, int scale) throws IOException {
        Path processedDir = Paths.get(uploadPath, "processed");
        if (!Files.exists(processedDir)) {
            try {
                Files.createDirectories(processedDir);
            } catch (IOException e) {
                System.err.println("Error creating processed directory: " + processedDir + " - " + e.getMessage());
                throw new IOException(e);
            }
        }
        String processedFileName = originalFileName + "_" + model + "_" + Integer.toString(scale) ;
        Path processedFilePath = processedDir.resolve(processedFileName);

        return processedFilePath;
    }

    private void doUpscaleProcess(Long imageId, Path originalFilePath, Path processedFilePath, 
                                String modelWeightsPath, String model, int scale) throws Exception {
        
        int exitCode;
        
        try {
            updateImageStatusToProcessing(imageId);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutablePath,
                    Paths.get(scriptsPath, "upscale_image.py").toString(),
                    originalFilePath.toString(),
                    processedFilePath.toString(),
                    modelWeightsPath,
                    model,
                    String.valueOf(scale)
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();
            printProcessInput(process);
            exitCode = process.waitFor();
            System.out.println("Python script finished with exit code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            System.err.println("Error during Python script execution for image " + imageId + ": " + e.getMessage());
            throw new Exception(e);
        } catch (Exception e) {
            System.err.println("Unexpected error in async processing for image " + imageId + ": " + e.getMessage());
            throw new Exception(e);
        }

        if (exitCode == 0) {
            System.out.println("Image " + imageId + " processing successful, status updated via ImageService.");
        } else {
            System.err.println("Image " + imageId + " processing failed with exit code: " + exitCode + ", status updated via ImageService.");
            throw new Exception("");
        }
    }

    private void updateImageStatusToProcessing(Long imageId) throws Exception {
        Image imageToUpdate = imageRepository.findById(imageId)
                        .orElseThrow(() -> new RuntimeException("Image not found for updating status to 'processing': " + imageId));
        imageToUpdate.setStatus("processing");
        imageToUpdate.setProcessStartTime(LocalDateTime.now());
        imageRepository.save(imageToUpdate);
        System.out.println("Image " + imageId + " status updated to 'processing'.");
    }

    private void printProcessInput(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("Python output: " + line);
        }
    }

    private void updateImageStatusToError(Long imageId) {
        try {
            imageService.updateImageProcessingResult(imageId, null, "error");
        } catch (Exception e) {
            System.err.println("Failed to update status to error for image " + imageId + ": " + e.getMessage());
        }
    }
}