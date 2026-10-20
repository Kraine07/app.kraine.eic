package app.kraine.eic.service;

import app.kraine.eic.config.EquipmentImageProperties;
import app.kraine.eic.exception.EquipmentImageAlreadyExistsException;
import app.kraine.eic.exception.EquipmentNotFoundException;
import app.kraine.eic.exception.InvalidImageException;
import app.kraine.eic.model.Equipment;
import app.kraine.eic.model.EquipmentImage;
import app.kraine.eic.repository.EquipmentImageRepository;
import app.kraine.eic.repository.EquipmentRepoInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentImageService {

    private static final int MAX_NAME_LENGTH = 255;

    private final EquipmentRepoInterface equipmentRepository;
    private final EquipmentImageRepository imageRepository;
    private final EquipmentImageProperties properties;

    /** Preconditions of the use case: the equipment exists and does not have an image yet. */
    @Transactional(readOnly = true)
    public void assertCanUpload(Long equipmentId) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw new EquipmentNotFoundException(equipmentId);
        }
        if (imageRepository.existsByEquipmentId(equipmentId)) {
            throw new EquipmentImageAlreadyExistsException(equipmentId);
        }
    }

    /** Steps 7 and 8: validate the file, then save its name and data linked to the equipment. */
    @Transactional
    public EquipmentImage uploadImage(Long equipmentId, MultipartFile file) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));
        if (imageRepository.existsByEquipmentId(equipmentId)) {
            throw new EquipmentImageAlreadyExistsException(equipmentId);
        }

        byte[] data = validate(file);
        EquipmentImage image = new EquipmentImage(null, equipment, sanitizeName(file.getOriginalFilename()), data);
        try {
            return imageRepository.saveAndFlush(image);
        } catch (DataIntegrityViolationException ex) {
            // Two uploads raced past the check above; the unique equipment_id constraint decided.
            throw new EquipmentImageAlreadyExistsException(equipmentId);
        }
    }

    /** Id and name of the equipment's image, if it has one. Does not load the image bytes. */
    @Transactional(readOnly = true)
    public Optional<EquipmentImageRepository.ImageInfo> findImageInfo(Long equipmentId) {
        if (!equipmentRepository.existsById(equipmentId)) {
            throw new EquipmentNotFoundException(equipmentId);
        }
        return imageRepository.findInfoByEquipmentId(equipmentId);
    }

    /** Text shown on the form and in every validation error. */
    public String limitsDescription() {
        String types = properties.getAllowedContentTypes().stream()
                .map(type -> type.substring(type.indexOf('/') + 1).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        return "Allowed file types: " + types + ". Maximum file size: " + formatSize(properties.getMaxSize()) + ".";
    }

    /** Value for the file input's accept attribute. */
    public String acceptAttribute() {
        return String.join(",", properties.getAllowedContentTypes());
    }

    // ---------------------------------------------------------------- validation

    private byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("No image file was selected.");
        }
        if (file.getSize() > properties.getMaxSize().toBytes()) {
            throw invalid("The selected file is too large.");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw invalid("The selected file could not be read.");
        }

        // The Content-Type header and file extension are chosen by the client, so the type is
        // taken from the file's own signature instead.
        String detectedType = detectContentType(data);
        if (detectedType == null || !properties.getAllowedContentTypes().contains(detectedType)) {
            throw invalid("The selected file is not an allowed image type.");
        }
        return data;
    }

    private InvalidImageException invalid(String reason) {
        return new InvalidImageException(reason + " " + limitsDescription());
    }

    private static String detectContentType(byte[] d) {
        if (startsWith(d, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(d, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(d, 'G', 'I', 'F', '8') && d.length > 5 && (d[4] == '7' || d[4] == '9') && d[5] == 'a') {
            return "image/gif";
        }
        if (d.length >= 12 && startsWith(d, 'R', 'I', 'F', 'F')
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- helpers

    /** Keeps only the file name (no directories) and fits it into the imageName column. */
    private static String sanitizeName(String originalFilename) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(originalFilename == null ? "" : originalFilename));
        if (!StringUtils.hasText(name)) {
            name = "image";
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    private static String formatSize(DataSize size) {
        long megabyte = DataSize.ofMegabytes(1).toBytes();
        return size.toBytes() % megabyte == 0 ? size.toMegabytes() + " MB" : size.toKilobytes() + " KB";
    }
}
