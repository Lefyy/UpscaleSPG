package upscale_project.UpscaleSPG.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import upscale_project.UpscaleSPG.exception.ImageNotFoundException;
import upscale_project.UpscaleSPG.exception.ImageProcessingException;
import upscale_project.UpscaleSPG.exception.InvalidImageException;
import upscale_project.UpscaleSPG.model.Image;
import upscale_project.UpscaleSPG.model.ImageStatus;
import upscale_project.UpscaleSPG.repository.ImageRepository;

@Service
public class ImageLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(ImageLifecycleService.class);

    private final ImageRepository imageRepository;

    @Autowired
    public ImageLifecycleService(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    public void updateImageStatus(Long imageId, ImageStatus status) {
        Image imageToUpdate = getImageById(imageId);
        imageToUpdate.setStatus(status);
        imageToUpdate.setProcessStartTime(LocalDateTime.now());
        imageRepository.save(imageToUpdate);
        logger.info("Image {} status updated to '{}'.", imageId, status);
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
            }

            throw new InvalidImageException("File is not a valid image: " + filePath.getFileName());
        } catch (IOException e) {
            logger.error("Could not read image metadata for {}: {}", filePath.getFileName(), e.getMessage());
            throw new InvalidImageException("Could not read image metadata for " + filePath.getFileName(), e);
        }
    }

    private Image getImageById(Long imageId) {
        return imageRepository.findById(imageId)
            .orElseThrow(() -> new ImageNotFoundException("Image not found with ID: " + imageId));
    }
}

