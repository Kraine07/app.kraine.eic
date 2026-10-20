package app.kraine.eic.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * Upload rules for equipment images. Override in application.properties:
 * <pre>
 * app.equipment-image.max-size=5MB
 * app.equipment-image.allowed-content-types=image/jpeg,image/png
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.equipment-image")
public class EquipmentImageProperties {

    /** Maximum size of an uploaded image. */
    private DataSize maxSize = DataSize.ofMegabytes(5);

    /** Accepted image types. Supported by the service: image/jpeg, image/png, image/gif, image/webp. */
    private List<String> allowedContentTypes = List.of("image/jpeg", "image/png");
}
