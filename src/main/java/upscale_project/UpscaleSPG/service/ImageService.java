package upscale_project.UpscaleSPG.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class ImageService {
    private final ImageRepository imageRepository;
    private final AsyncProcessorService asyncProcessorService;

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${app.scripts.path}")
    private String scriptsPath;

    @Autowired
    public ImageService(ImageRepository imageRepository, AsyncProcessorService asyncProcessorService) {
        this.imageRepository = imageRepository;
        this.asyncProcessorService = asyncProcessorService;
    }

    public Long processImageUpload(MultipartFile file, String model, int scale) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String savedOriginalFilePath = getSavedOriginalFilePath(file);
        long originalFileSize = Files.size(Paths.get(savedOriginalFilePath));
        String originalResolution = getResolution(Paths.get(savedOriginalFilePath));

        Image newImage = new Image(
                originalFilename,
                savedOriginalFilePath,
                "uploaded",
                model,
                scale,
                originalResolution,
                originalFileSize
        );

        Image savedImage = imageRepository.save(newImage);

        asyncProcessorService.startUpscalingProcess(savedImage.getId(),
                savedImage.getOriginalFilePath(),
                savedImage.getOriginalFileName(),
                model, scale);

        return savedImage.getId();
    }

    private String getSavedOriginalFilePath(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(this.uploadPath);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        String fileExtension = getFileExtension(file);
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = uploadPath.resolve(uniqueFileName);

        Files.copy(file.getInputStream(), filePath);

        String savedOriginalFilePath = filePath.toAbsolutePath().toString();
        return savedOriginalFilePath;
    }

    private String getFileExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String fileExtension = "";
        if (filename != null && filename.contains(".")) {
            fileExtension = filename.substring(filename.lastIndexOf("."));
        }
        return fileExtension;
    }

    public void updateImageProcessingResult(Long imageId, String processedFilePath, String status) throws Exception {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new FileNotFoundException("Image not found with ID: " + imageId));

        image.setProcessedFilePath(processedFilePath);
        image.setStatus(status);
        image.setProcessEndTime(LocalDateTime.now());
        image.setUpscaledResolution(getResolution(Paths.get(processedFilePath)));
        image.setUpscaledFileSize(Files.size(Paths.get(processedFilePath)));

        imageRepository.save(image);
    }

    private String getResolution(Path filePath) {
        String resolution = "N/A";
        try {
            BufferedImage bimg = ImageIO.read(filePath.toFile());
            if (bimg != null) {
                resolution = bimg.getWidth() + "x" + bimg.getHeight();
            }
        } catch (IOException e) {
            System.err.println("Could not get image metadata for " + filePath.getFileName() + ": " + e.getMessage());
        }
        return resolution;
    }

    public ImageMetadataResponse getImageStatus(Long imageId) throws FileNotFoundException {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new FileNotFoundException("Image not found with ID: " + imageId));

        return new ImageMetadataResponse(
            image.getStatus(),
            image.getOriginalResolution(),
            image.getUpscaledResolution(),
            image.getOriginalFileSize() != null ? image.getOriginalFileSize() : 0,
            image.getUpscaledFileSize() != null ? image.getUpscaledFileSize() : 0,
            image.getModelUsed(),
            image.getScaleFactor(),
            image.getOriginalFileName()
        );
    }

    public ResponseEntity<Resource> getProcessedImageFile(Long imageId) throws FileNotFoundException, RuntimeException {
        Image image = getImageById(imageId);

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

        String contentType = getContentType(file);
        Resource resource = getResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        String downloadFileName = getDownloadFilename(image.getOriginalFileName());
        headers.setContentDisposition(ContentDisposition.inline().filename(downloadFileName).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    private Image getImageById(Long imageId) throws FileNotFoundException {
        Optional<Image> imageOptional = imageRepository.findById(imageId);

        if (imageOptional.isEmpty()) {
            throw new FileNotFoundException("Image not found with ID: " + imageId);
        }

        return imageOptional.get();
    }

    private String getContentType(Path file) {
        String contentType = null;
        try {
            contentType = Files.probeContentType(file);
        } catch (IOException e) {
            System.err.println("Could not determine file content type for " + file.toString() + ": " + e.getMessage());
            contentType = "application/octet-stream";
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return contentType;
    }

    private Resource getResource(Path file) throws RuntimeException {
        Resource resource;
        try {
            resource = new UrlResource(file.toUri());
        } catch (Exception e) {
            System.err.println("Could not create Resource from file " + file.toString() + ": " + e.getMessage());
            throw new RuntimeException("Error preparing file for download.", e);
        }
        return resource;
    }

    private String getDownloadFilename(String originalFilename) {
        String downloadFileName = originalFilename;
        if (downloadFileName != null && downloadFileName.contains(".")) {
            int dotIndex = downloadFileName.lastIndexOf(".");
            downloadFileName = downloadFileName.substring(0, dotIndex) + "_upscaled" + downloadFileName.substring(dotIndex);
        } else {
            downloadFileName = (downloadFileName != null ? downloadFileName : "processed_image") + "_upscaled";
        }

        return downloadFileName;
    }
}
