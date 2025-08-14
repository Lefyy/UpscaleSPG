package upscale_project.UpscaleSPG.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async; // Это здесь больше не нужно, если только нет других @Async методов
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import upscale_project.UpscaleSPG.model.Image;
import upscale_project.UpscaleSPG.model.ImageMetadataResponse;
import upscale_project.UpscaleSPG.repository.ImageRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Service
public class ImageService {
    private final ImageRepository imageRepository;
    private final Environment env;
    private final AsyncProcessorService asyncProcessorService; // <-- Внедряем новый сервис

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${app.scripts.path}")
    private String scriptsPath;

    // Этот путь теперь используется только в AsyncProcessorService
    // private String pythonExecutablePath = Paths.get(".venv", "Scripts", "python.exe").toString();

    @Autowired
    public ImageService(ImageRepository imageRepository, Environment env, AsyncProcessorService asyncProcessorService) {
        this.imageRepository = imageRepository;
        this.env = env;
        this.asyncProcessorService = asyncProcessorService; // <-- Инициализируем
    }

    public ImageMetadataResponse getImageStatus(Long imageId) throws FileNotFoundException {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new FileNotFoundException("Image not found with ID: " + imageId));

        // Создаем и возвращаем DTO
        return new ImageMetadataResponse(
                image.getStatus(),
                image.getOriginalResolution(),
                image.getUpscaledResolution(),
                image.getOriginalFileSize() != null ? image.getOriginalFileSize() : 0, // Учитываем null
                image.getUpscaledFileSize() != null ? image.getUpscaledFileSize() : 0,   // Учитываем null
                image.getModelUsed(),
                image.getScaleFactor(),
                image.getOriginalFileName()
        );
    }

    public Long processImageUpload(MultipartFile file, String model, int scale) throws Exception {
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

        // --- Получаем метаданные исходного изображения ---
        String originalResolution = "N/A";
        long originalFileSize = 0;
        try {
            originalFileSize = Files.size(filePath);
            BufferedImage bimg = ImageIO.read(filePath.toFile());
            if (bimg != null) {
                originalResolution = bimg.getWidth() + "x" + bimg.getHeight();
            }
        } catch (IOException e) {
            System.err.println("Could not get original image metadata for " + originalFilename + ": " + e.getMessage());
        }
        // --- Конец получения метаданных исходного изображения ---

        Image newImage = new Image(
                originalFilename,
                savedOriginalFilePath,
                "uploaded",
                model,
                scale
        );
        newImage.setOriginalResolution(originalResolution);
        newImage.setOriginalFileSize(originalFileSize);

        Image savedImage = imageRepository.save(newImage);

        asyncProcessorService.startUpscalingProcess(savedImage.getId(),
                savedImage.getOriginalFilePath(),
                savedImage.getOriginalFileName(),
                model, scale);

        return savedImage.getId();
    }

    public void updateImageProcessingResult(Long imageId, String processedFilePath, String status) throws FileNotFoundException {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new FileNotFoundException("Image not found with ID: " + imageId));

        image.setProcessedFilePath(processedFilePath);
        image.setStatus(status);
        image.setProcessEndTime(java.time.LocalDateTime.now());

        // --- Получаем метаданные обработанного изображения ---
        String upscaledResolution = "N/A";
        long upscaledFileSize = 0;
        if (processedFilePath != null) {
            Path processedFile = Paths.get(processedFilePath);
            try {
                if (Files.exists(processedFile)) {
                    upscaledFileSize = Files.size(processedFile);
                    BufferedImage bimg = ImageIO.read(processedFile.toFile());
                    if (bimg != null) {
                        upscaledResolution = bimg.getWidth() + "x" + bimg.getHeight();
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not get upscaled image metadata for " + processedFilePath + ": " + e.getMessage());
            }
        }
        image.setUpscaledResolution(upscaledResolution);
        image.setUpscaledFileSize(upscaledFileSize);
        // --- Конец получения метаданных обработанного изображения ---

        imageRepository.save(image);
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

        headers.setContentDisposition(ContentDisposition.inline().filename(downloadFileName).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
