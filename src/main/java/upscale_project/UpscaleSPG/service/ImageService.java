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
import upscale_project.UpscaleSPG.repository.ImageRepository;

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

    public String getImageStatus(Long imageId) throws FileNotFoundException {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new FileNotFoundException("Image not found with ID: " + imageId));
        return image.getStatus();
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

        // <-- Вызываем асинхронный метод через новый сервис
        asyncProcessorService.startPythonProcessing(savedImage.getId(),
                savedImage.getOriginalFilePath(),
                savedImage.getOriginalFileName(),
                processingMethod, scaleFactor);

        return savedImage.getId();
    }

    // <-- Метод startPythonProcessing полностью удален из ImageService
    // Он теперь находится в AsyncProcessorService

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
