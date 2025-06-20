package upscale_project.UpscaleSPG.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import upscale_project.UpscaleSPG.repository.ImageRepository;
import upscale_project.UpscaleSPG.service.ImageService;

import java.io.FileNotFoundException;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {
    private final ImageService imageService;

    @Autowired
    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("model") String processingMethod,
            @RequestParam("scale") int scaleFactor
    ) {
        if (file.isEmpty()) {
            return new ResponseEntity<>("Пожалуйста, выберите файл для загрузки", HttpStatus.BAD_REQUEST);
        }

        try {
            Long savedImageId = imageService.processImageUpload(file, processingMethod, scaleFactor);

            return ResponseEntity.status(HttpStatus.CREATED).body(savedImageId);

        } catch (Exception e) {
            return new ResponseEntity<>("Ошибка при загрузке файла: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{imageId}/result")
    public ResponseEntity<Resource> getProcessedImage(@PathVariable Long imageId) {
        try {
            ResponseEntity<Resource> fileResponse = imageService.getProcessedImageFile(imageId);

            return fileResponse;

        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{imageId}/status")
    public ResponseEntity<String> getImageStatus(@PathVariable Long imageId) {
        try {
            String status = imageService.getImageStatus(imageId);
            return ResponseEntity.ok(status);
        } catch (FileNotFoundException e) {
            return new ResponseEntity<>("Image not found: " + e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>("Error retrieving status.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
