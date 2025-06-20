package upscale_project.UpscaleSPG.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    private final ImageRepository imageRepository;
    private final Environment env;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${app.scripts.path}")
    private String scriptsPath;

    private String pythonExecutablePath = Paths.get(".venv", "Scripts", "python.exe").toString();

    @Autowired
    public AsyncProcessorService(ImageRepository imageRepository, Environment env) {
        this.imageRepository = imageRepository;
        this.env = env;
    }

    @Async // Эта аннотация теперь будет работать корректно
    public void startPythonProcessing(Long imageId, String originalFilePathStr, String originalFileName, String model, int scale) {
        String modelWeightsPath;

        if (model.equalsIgnoreCase("bilinear") || model.equalsIgnoreCase("bicubic")) {
            modelWeightsPath = "none";
            System.out.println("Info: Using interpolation method '" + model + "'. No model weights needed.");
        } else {
            String modelWeightsPropertyKey = "app.weights.path." + model + ".scale" + scale;
            modelWeightsPath = env.getProperty(modelWeightsPropertyKey);

            if (modelWeightsPath == null) {
                System.err.println("Error: Model weights path not found for model: " + model + " and scale: " + scale + " (key: " + modelWeightsPropertyKey + ")");
                imageRepository.findById(imageId).ifPresent(img -> {
                    img.setStatus("error");
                    imageRepository.save(img);
                });
                return;
            }
        }

        Path originalFilePath = Paths.get(originalFilePathStr);
        String processedFileName = "processed_" + UUID.randomUUID().toString() + "_" + originalFileName;
        Path processedFilePath = Paths.get(uploadPath + File.separator + "processed").resolve(processedFileName);

        try {
            // Обновляем статус на "processing" СРАЗУ ПОСЛЕ ЗАПУСКА
            Image imageToUpdate = imageRepository.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("Image not found for status update: " + imageId));
            imageToUpdate.setStatus("processing");
            imageRepository.save(imageToUpdate);
            System.out.println("Image " + imageId + " status updated to 'processing'.");

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

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Python output: " + line);
            }

            int exitCode = process.waitFor();
            System.out.println("Python script finished with exit code: " + exitCode);

            Image image = imageRepository.findById(imageId).orElseThrow(() -> new RuntimeException("Image not found after processing: " + imageId));

            if (exitCode == 0) {
                image.setStatus("processed");
                image.setProcessedFilePath(processedFilePath.toString());
                System.out.println("Image " + imageId + " status updated to 'processed'.");
            } else {
                image.setStatus("error");
                System.err.println("Image " + imageId + " processing failed with exit code: " + exitCode);
            }
            imageRepository.save(image);

        } catch (IOException | InterruptedException e) {
            System.err.println("Error during Python script execution for image " + imageId + ": " + e.getMessage());
            imageRepository.findById(imageId).ifPresent(img -> {
                img.setStatus("error");
                imageRepository.save(img);
            });
        } catch (Exception e) {
            System.err.println("Unexpected error in async processing for image " + imageId + ": " + e.getMessage());
            imageRepository.findById(imageId).ifPresent(img -> {
                img.setStatus("error");
                imageRepository.save(img);
            });
        }
    }
}
