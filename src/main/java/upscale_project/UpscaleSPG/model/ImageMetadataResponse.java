package upscale_project.UpscaleSPG.model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public class ImageMetadataResponse {
    @Enumerated(EnumType.STRING)
    private ImageStatus status;
    private String originalResolution;
    private String upscaledResolution;
    private long originalFileSize;
    private long upscaledFileSize;
    @Enumerated(EnumType.STRING)
    private UpscalingMethod model;
    private int scale;
    private String originalFileName;

    public ImageMetadataResponse(ImageStatus status, String originalResolution, String upscaledResolution,
                                 long originalFileSize, long upscaledFileSize, UpscalingMethod model, int scale, String originalFileName) {
        this.status = status;
        this.originalResolution = originalResolution;
        this.upscaledResolution = upscaledResolution;
        this.originalFileSize = originalFileSize;
        this.upscaledFileSize = upscaledFileSize;
        this.model = model;
        this.scale = scale;
        this.originalFileName = originalFileName;
    }

    public ImageStatus getStatus() {
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

    public UpscalingMethod getModel() {
        return model;
    }

    public int getScale() {
        return scale;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }
}
