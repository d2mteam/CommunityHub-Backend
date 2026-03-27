package com.app.communityhub.media.reservation;

import com.app.communityhub.common.AppException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ImageMetadataInspector {

    public Dimensions inspect(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid image payload");
            }
            return new Dimensions(image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Could not inspect uploaded image");
        }
    }

    public record Dimensions(int width, int height) {
    }
}
