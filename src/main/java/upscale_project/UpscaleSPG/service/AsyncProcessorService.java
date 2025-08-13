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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class AsyncProcessorService {

    private final ImageRepository imageRepository; // Оставляем для первоначального обновления статуса
    private final ImageService imageService;      // НОВОЕ: Внедряем ImageService
    private final Environment env;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${app.scripts.path}")
    private String scriptsPath;

    // TODO: Consider making this configurable or robustly discoverable
    private String pythonExecutablePath = Paths.get(".venv", "Scripts", "python.exe").toString();

    @Autowired
    public AsyncProcessorService(ImageRepository imageRepository, Environment env, @Lazy ImageService imageService) {
        this.imageRepository = imageRepository;
        this.env = env;
        this.imageService = imageService; // Инициализируем ImageService
    }

    @Async
    public void startPythonProcessing(Long imageId, String originalFilePathStr, String originalFileName, String model, int scale) {
        String modelWeightsPath;

        // ====================================================================
        // Блок определения пути к весам модели (без изменений)
        // ====================================================================
        if (model.equalsIgnoreCase("bilinear") || model.equalsIgnoreCase("bicubic")) {
            modelWeightsPath = "none";
            System.out.println("Info: Using interpolation method '" + model + "'. No model weights needed.");
        } else {
            String modelWeightsPropertyKey = "app.weights.path." + model + ".scale" + scale;
            modelWeightsPath = env.getProperty(modelWeightsPropertyKey);

            if (modelWeightsPath == null) {
                System.err.println("Error: Model weights path not found for model: " + model + " and scale: " + scale + " (key: " + modelWeightsPropertyKey + ")");
                // Используем imageService для обновления статуса ошибки
                try {
                    imageService.updateImageProcessingResult(imageId, null, "error");
                } catch (Exception e) {
                    System.err.println("Failed to update status to error for image " + imageId + ": " + e.getMessage());
                }
                return;
            }
        }

        Path originalFilePath = Paths.get(originalFilePathStr);
        // Создаем путь для обработанного файла в папке 'processed'
        Path processedDir = Paths.get(uploadPath, "processed");
        if (!java.nio.file.Files.exists(processedDir)) {
            try {
                java.nio.file.Files.createDirectories(processedDir);
            } catch (IOException e) {
                System.err.println("Error creating processed directory: " + processedDir + " - " + e.getMessage());
                try {
                    imageService.updateImageProcessingResult(imageId, null, "error");
                } catch (Exception ex) {
                    System.err.println("Failed to update status to error for image " + imageId + ": " + ex.getMessage());
                }
                return;
            }
        }
        String processedFileName = "processed_" + UUID.randomUUID().toString() + "_" + originalFileName;
        Path processedFilePath = processedDir.resolve(processedFileName);


        try {
            // Обновляем статус на "processing" СРАЗУ ПОСЛЕ ЗАПУСКА
            // Здесь все еще используем imageRepository напрямую, так как ImageService.updateImageProcessingResult
            // предназначен для обновления ПОСЛЕ обработки, включая метаданные.
            // Для начального статуса "processing" достаточно прямого обновления.
            Image imageToUpdate = imageRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("Image not found for initial status update: " + imageId));
            imageToUpdate.setStatus("processing");
            imageToUpdate.setProcessStartTime(java.time.LocalDateTime.now()); // Устанавливаем время начала обработки
            imageRepository.save(imageToUpdate);
            System.out.println("Image " + imageId + " status updated to 'processing'.");

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutablePath,
                    Paths.get(scriptsPath, "upscale_image.py").toString(),
                    originalFilePath.toString(),
                    processedFilePath.toString(), // Путь для сохранения обработанного файла
                    modelWeightsPath,
                    model,
                    String.valueOf(scale)
            );

            pb.redirectErrorStream(true); // Объединить stderr и stdout
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Python output: " + line);
            }

            int exitCode = process.waitFor();
            System.out.println("Python script finished with exit code: " + exitCode);

            // ====================================================================
            // ИЗМЕНЕНИЯ ЗДЕСЬ: Используем imageService.updateImageProcessingResult
            // ====================================================================
            if (exitCode == 0) {
                // Если все успешно, вызываем ImageService для обновления
                // Он сам определит новое разрешение и размер файла
                imageService.updateImageProcessingResult(imageId, processedFilePath.toString(), "processed");
                System.out.println("Image " + imageId + " processing successful, status updated via ImageService.");
            } else {
                // Если ошибка, также вызываем ImageService для обновления
                imageService.updateImageProcessingResult(imageId, null, "error"); // Передаем null для processedFilePath при ошибке
                System.err.println("Image " + imageId + " processing failed with exit code: " + exitCode + ", status updated via ImageService.");
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error during Python script execution for image " + imageId + ": " + e.getMessage());
            // Обновляем статус на "error" через imageService
            try {
                imageService.updateImageProcessingResult(imageId, null, "error");
            } catch (Exception ex) {
                System.err.println("Failed to update status to error for image " + imageId + ": " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Unexpected error in async processing for image " + imageId + ": " + e.getMessage());
            // Обновляем статус на "error" через imageService
            try {
                imageService.updateImageProcessingResult(imageId, null, "error");
            } catch (Exception ex) {
                System.err.println("Failed to update status to error for image " + imageId + ": " + ex.getMessage());
            }
        }
    }
}