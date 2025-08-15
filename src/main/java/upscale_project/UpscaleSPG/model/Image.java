package upscale_project.UpscaleSPG.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String originalFileName;
    private String originalFilePath;
    private String processedFilePath;
    @Enumerated(EnumType.STRING)
    private ImageStatus status;
    private LocalDateTime uploadTime;
    private LocalDateTime processStartTime;
    private LocalDateTime processEndTime;
    @Enumerated(EnumType.STRING)
    private UpscalingMethod modelUsed;
    private int scaleFactor;
    private String originalResolution;
    private String upscaledResolution;
    private Long originalFileSize;
    private Long upscaledFileSize;


    public Image() {
    }

    public Image(String originalFileName, String originalFilePath, ImageStatus status, UpscalingMethod modelUsed, int scaleFactor, String originalResolution, Long originalFileSize) {
        this.originalFileName = originalFileName;
        this.originalFilePath = originalFilePath;
        this.status = status;
        this.uploadTime = LocalDateTime.now();
        this.modelUsed = modelUsed;
        this.scaleFactor = scaleFactor;
        this.originalResolution = originalResolution;
        this.originalFileSize = originalFileSize;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getOriginalFilePath() {
        return originalFilePath;
    }

    public void setOriginalFilePath(String originalFilePath) {
        this.originalFilePath = originalFilePath;
    }

    public String getProcessedFilePath() {
        return processedFilePath;
    }

    public void setProcessedFilePath(String processedFilePath) {
        this.processedFilePath = processedFilePath;
    }

    public ImageStatus getStatus() {
        return status;
    }

    public void setStatus(ImageStatus status) {
        this.status = status;
    }

    public LocalDateTime getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
    }

    public LocalDateTime getProcessStartTime() {
        return processStartTime;
    }

    public void setProcessStartTime(LocalDateTime processStartTime) {
        this.processStartTime = processStartTime;
    }

    public LocalDateTime getProcessEndTime() {
        return processEndTime;
    }

    public void setProcessEndTime(LocalDateTime processEndTime) {
        this.processEndTime = processEndTime;
    }

    public UpscalingMethod getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(UpscalingMethod modelUsed) {
        this.modelUsed = modelUsed;
    }

    public int getScaleFactor() {
        return scaleFactor;
    }

    public void setScaleFactor(int scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public String getOriginalResolution() {
        return originalResolution;
    }

    public void setOriginalResolution(String originalResolution) {
        this.originalResolution = originalResolution;
    }

    public String getUpscaledResolution() {
        return upscaledResolution;
    }

    public void setUpscaledResolution(String upscaledResolution) {
        this.upscaledResolution = upscaledResolution;
    }

    public Long getOriginalFileSize() {
        return originalFileSize;
    }

    public void setOriginalFileSize(Long originalFileSize) {
        this.originalFileSize = originalFileSize;
    }

    public Long getUpscaledFileSize() {
        return upscaledFileSize;
    }

    public void setUpscaledFileSize(Long upscaledFileSize) {
        this.upscaledFileSize = upscaledFileSize;
    }
}