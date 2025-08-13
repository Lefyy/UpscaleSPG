package upscale_project.UpscaleSPG.model;

public class ImageMetadataResponse {
    private String status;
    private String originalResolution; // e.g., "1920x1080"
    private String upscaledResolution; // e.g., "3840x2160"
    private long originalFileSize;     // in bytes
    private long upscaledFileSize;     // in bytes
    private String model;              // e.g., "SRGAN"
    private int scale;                 // e.g., 2
    private String originalFileName;   // original filename for download

    // Конструктор
    public ImageMetadataResponse(String status, String originalResolution, String upscaledResolution,
                                 long originalFileSize, long upscaledFileSize, String model, int scale, String originalFileName) {
        this.status = status;
        this.originalResolution = originalResolution;
        this.upscaledResolution = upscaledResolution;
        this.originalFileSize = originalFileSize;
        this.upscaledFileSize = upscaledFileSize;
        this.model = model;
        this.scale = scale;
        this.originalFileName = originalFileName;
    }

    // Геттеры (сеттеры не нужны, так как мы будем создавать объект один раз)
    public String getStatus() {
        return status;
    }

    public String getOriginalResolution() {
        return originalResolution;
    }

    public String getUpscaledResolution() {
        return upscaledResolution;
    }

    public long getOriginalFileSize() {
        return originalFileSize;
    }

    public long getUpscaledFileSize() {
        return upscaledFileSize;
    }

    public String getModel() {
        return model;
    }

    public int getScale() {
        return scale;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }
}
