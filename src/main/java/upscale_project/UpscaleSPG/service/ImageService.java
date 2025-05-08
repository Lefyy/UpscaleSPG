package upscale_project.UpscaleSPG.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import upscale_project.UpscaleSPG.model.Image;
import upscale_project.UpscaleSPG.repository.ImageRepository;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.io.BufferedReader;

@Service
public class ImageService {
    private final ImageRepository imageRepository;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${app.scripts.path}")
    private String scriptsPath;

    @Autowired
    public ImageService(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    public Long processImageUpload(MultipartFile file,
                                     String processingMethod,
                                     int scaleFactor) throws Exception {

        String uploadDir = this.uploadPath;
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = uploadPath.resolve(uniqueFileName);

        Files.copy(file.getInputStream(), filePath);

        String savedOriginalFilePath = filePath.toAbsolutePath().toString();

        Image newImage = new Image(
                originalFilename,
                savedOriginalFilePath,
                "uploaded",
                processingMethod,
                scaleFactor
        );

        Image savedImage = imageRepository.save(newImage);

        String pythonExecutable = "python3";
        String pythonScriptPath = scriptsPath + "/upscale_image.py";

        String processedUploadDir = this.uploadPath + "/processed";
        Path processedUploadPath = Paths.get(processedUploadDir);
        if (!Files.exists(processedUploadPath)) {
            Files.createDirectories(processedUploadPath);
        }

        String processedUniqueFileName = savedImage.getId() + "_upscaled" + fileExtension;
        Path processedFilePath = processedUploadPath.resolve(processedUniqueFileName);
        String savedProcessedFilePath = processedFilePath.toAbsolutePath().toString();


        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(pythonScriptPath);
        command.add(savedOriginalFilePath); // Аргумент 1: путь к оригинальному файлу
        command.add(savedProcessedFilePath); // Аргумент 2: путь куда сохранить результат
        command.add(processingMethod); // Аргумент 3: метод обработки
        command.add(String.valueOf(scaleFactor)); // Аргумент 4: кратность (переводим int в String)

        System.out.println("Executing Python command: " + String.join(" ", command)); // Для отладки

        ProcessBuilder processBuilder = new ProcessBuilder(command);

        int exitCode;
        Process process = null;
        try {
            process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            System.out.println("Python STDOUT:\n" + output.toString());

            StringBuilder errorOutput = new StringBuilder();

            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            System.err.println("Python STDERR:\n" + errorOutput.toString()); // Временно печатаем для отладки

            exitCode = process.waitFor(); // Ждем завершения Python-скрипта

            System.out.println("Python process finished with exit code: " + exitCode); // Временно печатаем для отладки

            if (exitCode == 0) {
                System.out.println("Image processing successful.");
                savedImage.setStatus("processed");
                savedImage.setProcessedFilePath(savedProcessedFilePath);

            } else {
                System.err.println("Image processing failed with exit code: " + exitCode);
                savedImage.setStatus("error");
                imageRepository.save(savedImage);
                throw new RuntimeException("Image processing failed. Python exit code: " + exitCode + "\nError Output:\n" + errorOutput.toString());
            }

        } catch (IOException e) {
            System.err.println("Failed to start Python process: " + e.getMessage());
            savedImage.setStatus("error");
            throw new RuntimeException("Failed to start image processing process.", e); // Оборачиваем и перебрасываем
        } catch (InterruptedException e) {
            System.err.println("Python process interrupted: " + e.getMessage());
            process.destroy();
            savedImage.setStatus("error");
            Thread.currentThread().interrupt();
            throw new RuntimeException("Image processing process was interrupted.", e); // Оборачиваем и перебрасываем
        } finally {
            imageRepository.save(savedImage);
        }

        return savedImage.getId();
    }
}
