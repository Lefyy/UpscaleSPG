package upscale_project.UpscaleSPG.exception;

public class ImageProcessingException extends RuntimeException {
    public ImageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImageProcessingException(String message) {
        super(message);
    }
}
