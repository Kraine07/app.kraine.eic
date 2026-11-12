package app.kraine.eic.api;

import app.kraine.eic.config.EquipmentImageProperties;
import app.kraine.eic.model.EquipmentImage;
import app.kraine.eic.service.EquipmentImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** JSON version of the Upload Equipment Image use case, used by the Flutter app. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class EquipmentImageApiController {

    private final EquipmentImageService imageService;
    private final EquipmentImageProperties properties;

    public record ImageRules(List<String> allowedContentTypes, long maxSizeBytes, String description) {
    }

    public record ImageInfoResponse(Long id, Long equipmentId, String imageName) {
    }

    /** What the client may upload, so it can check a file before sending it. */
    @GetMapping("/equipment-images/rules")
    public ImageRules rules() {
        return new ImageRules(
                properties.getAllowedContentTypes(),
                properties.getMaxSize().toBytes(),
                imageService.limitsDescription());
    }

    /** 200 with the image's id and name, or 404 when the equipment has no image. */
    @GetMapping("/equipment/{equipmentId}/image")
    public ResponseEntity<ImageInfoResponse> getImage(@PathVariable Long equipmentId) {
        return imageService.findImageInfo(equipmentId)
                .map(info -> ResponseEntity.ok(new ImageInfoResponse(info.getId(), equipmentId, info.getImageName())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Steps 6-9 of the use case: validate, save, and answer 201 Created. */
    @PostMapping(path = "/equipment/{equipmentId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageInfoResponse> upload(@PathVariable Long equipmentId,
                                                    @RequestParam(value = "image", required = false) MultipartFile image) {
        EquipmentImage saved = imageService.uploadImage(equipmentId, image);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location)
                .body(new ImageInfoResponse(saved.getId(), equipmentId, saved.getImageName()));
    }
}
