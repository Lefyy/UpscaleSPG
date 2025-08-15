package upscale_project.UpscaleSPG.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import upscale_project.UpscaleSPG.exception.ImageNotFoundException;
import upscale_project.UpscaleSPG.exception.ImageNotProcessedException;
import upscale_project.UpscaleSPG.exception.ImageProcessingException;
import upscale_project.UpscaleSPG.exception.InvalidImageException;
import upscale_project.UpscaleSPG.model.Image;
import upscale_project.UpscaleSPG.model.ImageMetadataResponse;
import upscale_project.UpscaleSPG.model.ImageStatus;
import upscale_project.UpscaleSPG.model.UpscalingMethod;
import upscale_project.UpscaleSPG.repository.ImageRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ImageService {
    private final ImageRepository imageRepository;
    private final AsyncProcessorService asyncProcessorService;
    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    @Value("${app.upload.path}")
    private String uploadPath;

    @Value("${app.scripts.path}")
    private String scriptsPath;

    @Autowired
    public ImageService(ImageRepository imageRepository, AsyncProcessorService asyncProcessorService) {
        this.imageRepository = imageRepository;
        this.asyncProcessorService = asyncProcessorService;
    }

    public Long processImageUpload(MultipartFile file, UpscalingMethod model, int scale) {
        try {
            String originalFilename = file.getOriginalFilename();

            if (file.isEmpty() || originalFilename == null || originalFilename.isBlank()) {
                throw new InvalidImageException("Uploaded file is empty or has no name.");
            }

            String savedOriginalFilePath = getSavedOriginalFilePath(file);
            long originalFileSize = Files.size(Paths.get(savedOriginalFilePath));
            String originalResolution = getResolution(Paths.get(savedOriginalFilePath));

            Image newImage = new Image(
                    originalFilename,
                    savedOriginalFilePath,
                    ImageStatus.UPLOADED,
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
        } catch (IOException e) {
            logger.error("Failed to process image upload: {}", e.getMessage());
            throw new ImageProcessingException("Failed to save or read uploaded image.", e);
        }
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

    public void updateImageProcessingResult(Long imageId, String processedFilePath, ImageStatus status) {
        Image image = getImageById(imageId);
        image.setProcessedFilePath(processedFilePath);
        image.setStatus(status);
        image.setProcessEndTime(LocalDateTime.now());

        if (processedFilePath != null) {
            try {
                Path filePath = Paths.get(processedFilePath);
                image.setUpscaledResolution(getResolution(filePath));
                image.setUpscaledFileSize(Files.size(filePath));
            } catch (IOException e) {
                logger.error("Could not get metadata for processed file {}: {}", processedFilePath, e.getMessage());
                throw new ImageProcessingException("Could not get metadata for processed file.", e);
            }
        }

        imageRepository.save(image);
    }

    private String getResolution(Path filePath) {
        try {
            BufferedImage bimg = ImageIO.read(filePath.toFile());
            if (bimg != null) {
                return bimg.getWidth() + "x" + bimg.getHeight();
            } else {
                throw new InvalidImageException("File is not a valid image: " + filePath.getFileName());
            }
        } catch (IOException e) {
            logger.error("Could not read image metadata for {}: {}", filePath.getFileName(), e.getMessage());
            throw new InvalidImageException("Could not read image metadata for " + filePath.getFileName(), e);
        }
    }

    public ImageMetadataResponse getImageStatus(Long imageId) {
        Image image = getImageById(imageId);

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

    public ResponseEntity<Resource> getProcessedImageFile(Long imageId) {
        Image image = getImageById(imageId);

        if (!ImageStatus.PROCESSED.equals(image.getStatus()) || image.getProcessedFilePath() == null) {
            throw new ImageNotProcessedException("Image with ID " + imageId + " is not processed yet. Current status: " + image.getStatus());
        }

        try {
            Path filePath = Paths.get(image.getProcessedFilePath());
            if (!Files.exists(filePath)) {
                logger.error("File not found on disk for image ID {}, path: {}", imageId, filePath);
                throw new ImageProcessingException("Processed image file is missing on the server.");
            }

            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            String downloadFileName = getDownloadFilename(image.getOriginalFileName());
            headers.setContentDisposition(ContentDisposition.inline().filename(downloadFileName).build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (IOException e) {
            logger.error("Error preparing file for download for image ID {}: {}", imageId, e.getMessage());
            throw new ImageProcessingException("Error reading processed file from disk.", e);
        }
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

    private Image getImageById(Long imageId) {
        return imageRepository.findById(imageId)
            .orElseThrow(() -> new ImageNotFoundException("Image not found with ID: " + imageId));
    }
}
