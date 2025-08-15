package upscale_project.UpscaleSPG.controller;

import org.antlr.v4.runtime.misc.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import upscale_project.UpscaleSPG.exception.ImageNotFoundException;
import upscale_project.UpscaleSPG.exception.ImageProcessingException;
import upscale_project.UpscaleSPG.exception.InvalidImageException;
import upscale_project.UpscaleSPG.model.ImageMetadataResponse;
import upscale_project.UpscaleSPG.model.UpscalingMethod;
import upscale_project.UpscaleSPG.model.UploadResponse;
import upscale_project.UpscaleSPG.service.ImageService;

import java.io.FileNotFoundException;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    private final ImageService imageService;

    @Autowired
    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") @NonNull MultipartFile file,
            @RequestParam("model") String model,
            @RequestParam("scale") int scale
    ) {
        logger.info("Received image upload request: model={}, scale={}", model, scale);
        
        try {
            UpscalingMethod upscalingMethod = UpscalingMethod.valueOf(model.toUpperCase());
            Long savedImageId = imageService.processImageUpload(file, upscalingMethod, scale);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UploadResponse(savedImageId));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid upscaling model: {}", model);
            throw new InvalidImageException("Invalid upscaling model: " + model);
        } catch (ImageProcessingException | ImageNotFoundException e) {
            logger.error("Failed to upload image: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getImageStatus(@PathVariable("id") Long id) {
        logger.info("Fetching status for image ID: {}", id);
        ImageMetadataResponse response = imageService.getImageStatus(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<Resource> getProcessedImage(@PathVariable("id") Long id) {
        logger.info("Fetching processed image for ID: {}", id);
        return imageService.getProcessedImageFile(id);
    
    }
}
