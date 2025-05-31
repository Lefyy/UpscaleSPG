package upscale_project.UpscaleSPG.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
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

        startPythonProcessing(savedImage.getId(),
                savedImage.getOriginalFilePath(),
                savedImage.getOriginalFileName(),
                processingMethod, scaleFactor);

        return savedImage.getId();
    }

    @Async
    public void startPythonProcessing(Long imageId, String originalFilePathStr, String originalFileName, String method, int scale) {
        Path originalFilePath = Paths.get(originalFilePathStr);
        String processedFileName = "processed_" + UUID.randomUUID().toString() + "_" + originalFileName;
        Path processedFilePath = Paths.get(uploadPath + File.separator + "processed").resolve(processedFileName);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    Paths.get(scriptsPath, "upscale_image.py").toString(),
                    originalFilePath.toString(),  // Путь к входному файлу
                    processedFilePath.toString(), // Путь к выходному файлу
                    method,                       // Метод
                    String.valueOf(scale)         // Масштаб
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
