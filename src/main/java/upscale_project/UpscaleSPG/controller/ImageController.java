package upscale_project.UpscaleSPG.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import upscale_project.UpscaleSPG.model.ImageMetadataResponse;
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
            @RequestParam("model") String model,
            @RequestParam("scale") int scale
    ) {
        if (file.isEmpty()) {
            return new ResponseEntity<>("Пожалуйста, выберите файл для загрузки", HttpStatus.BAD_REQUEST);
        }

        try {
            Long savedImageId = imageService.processImageUpload(file, model, scale);

            return ResponseEntity.status(HttpStatus.CREATED).body(savedImageId);

        } catch (Exception e) {
            return new ResponseEntity<>("Ошибка при загрузке файла: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getImageStatus(@PathVariable("id") Long id) {
        try {
            ImageMetadataResponse response = imageService.getImageStatus(id);
            return ResponseEntity.ok(response);
        } catch (FileNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>("Ошибка при получении статуса: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<?> getProcessedImage(@PathVariable("id") Long id) {
        try {
            return imageService.getProcessedImageFile(id);
        } catch (FileNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("Ошибка при получении обработанного изображения: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
