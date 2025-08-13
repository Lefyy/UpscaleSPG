package upscale_project.UpscaleSPG.model;

import jakarta.persistence.Entity;
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
    private String status;
    private LocalDateTime uploadTime;
    private LocalDateTime processStartTime;
    private LocalDateTime processEndTime;
    private String modelUsed;
    private int scaleFactor;
    private String originalResolution;
    private String upscaledResolution;
    private Long originalFileSize;
    private Long upscaledFileSize;


    public Image() {
    }

    public Image(String originalFileName, String originalFilePath, String status, String modelUsed, int scaleFactor) {
        this.originalFileName = originalFileName;
        this.originalFilePath = originalFilePath;
        this.status = status;
        this.uploadTime = LocalDateTime.now();
        this.modelUsed = modelUsed;
        this.scaleFactor = scaleFactor;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
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