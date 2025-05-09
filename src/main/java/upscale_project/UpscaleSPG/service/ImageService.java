package upscale_project.UpscaleSPG.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import upscale_project.UpscaleSPG.model.Image;
import upscale_project.UpscaleSPG.repository.ImageRepository;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
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

    public ResponseEntity<Resource> getProcessedImageFile(Long imageId) throws FileNotFoundException, RuntimeException {
        Optional<Image> imageOptional = imageRepository.findById(imageId);

        if (imageOptional.isEmpty()) {
            throw new FileNotFoundException("Image not found with ID: " + imageId);
        }

        Image image = imageOptional.get();

        if (!"processed".equals(image.getStatus()) || image.getProcessedFilePath() == null) {
            String errorMessage = "Image with ID " + imageId + " is not processed yet or processing failed. Current status: " + image.getStatus();
            throw new RuntimeException(errorMessage);
        }

        String processedFilePath = image.getProcessedFilePath();

        Path file = Paths.get(processedFilePath);

        if (!Files.exists(file)) {
            String errorMessage = "Processed image file not found on disk at path: " + processedFilePath;
            throw new FileNotFoundException(errorMessage);
        }

        String contentType = null;
        try {
            contentType = Files.probeContentType(file);

            if (contentType == null || "application/octet-stream".equals(contentType)) {
                String fileExtension = "";
                String fileName = file.getFileName().toString();
                if (fileName != null && fileName.contains(".")) {
                    fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
                    switch (fileExtension) {
                        case "jpg":
                        case "jpeg":
                            contentType = "image/jpeg";
                            break;
                        case "png":
                            contentType = "image/png";
                            break;
                        default:
                            contentType = "application/octet-stream";
                            break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not determine file content type for " + processedFilePath + ": " + e.getMessage());
            contentType = "application/octet-stream";
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        Resource resource;
        try {
            resource = new UrlResource(file.toUri());
        } catch (Exception e) {
            System.err.println("Could not create Resource from file " + processedFilePath + ": " + e.getMessage());
            throw new RuntimeException("Error preparing file for download.", e);
        }

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.parseMediaType(contentType));

        String downloadFileName = image.getOriginalFileName();

        if (downloadFileName != null && downloadFileName.contains(".")) {
            int dotIndex = downloadFileName.lastIndexOf(".");
            downloadFileName = downloadFileName.substring(0, dotIndex) + "_upscaled" + downloadFileName.substring(dotIndex);
        } else {
            downloadFileName = (downloadFileName != null ? downloadFileName : "processed_image") + "_upscaled";
        }

        headers.setContentDispositionFormData("inline", downloadFileName);

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
