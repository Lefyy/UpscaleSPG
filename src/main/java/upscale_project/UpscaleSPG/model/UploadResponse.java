package upscale_project.UpscaleSPG.model;

public class UploadResponse {
    private final Long imageId;

    public UploadResponse(Long imageId) {
        this.imageId = imageId;
    }

    public Long getImageId() {
        return imageId;
    }
}
