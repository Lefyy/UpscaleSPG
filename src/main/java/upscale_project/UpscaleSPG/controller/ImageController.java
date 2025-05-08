package upscale_project.UpscaleSPG.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import upscale_project.UpscaleSPG.repository.ImageRepository;
import upscale_project.UpscaleSPG.service.ImageService;

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
            @RequestParam("method") String processingMethod,
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
}
