package app.kraine.eic.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import app.kraine.eic.config.EquipmentImageProperties;
import app.kraine.eic.exception.EquipmentImageAlreadyExistsException;
import app.kraine.eic.exception.EquipmentNotFoundException;
import app.kraine.eic.exception.InvalidImageException;
import app.kraine.eic.model.Equipment;
import app.kraine.eic.model.EquipmentImage;
import app.kraine.eic.repository.EquipmentImageRepository;
import app.kraine.eic.repository.EquipmentRepoInterface;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentImageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Mock private EquipmentRepoInterface equipmentRepository;
    @Mock private EquipmentImageRepository imageRepository;

    private final EquipmentImageProperties properties = new EquipmentImageProperties();
    private EquipmentImageService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentImageService(equipmentRepository, imageRepository, properties);
    }

    private Equipment givenEquipmentWithoutImage() {
        Equipment equipment = mock(Equipment.class);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(imageRepository.existsByEquipmentId(1L)).thenReturn(false);
        return equipment;
    }

    @Test
    void savesPngLinkedToEquipment() {
        Equipment equipment = givenEquipmentWithoutImage();
        when(imageRepository.saveAndFlush(any(EquipmentImage.class))).thenAnswer(i -> i.getArgument(0));

        EquipmentImage saved = service.uploadImage(1L,
                new MockMultipartFile("image", "front view.png", "image/png", PNG));

        assertSame(equipment, saved.getEquipment());
        assertEquals("front view.png", saved.getImageName());
        assertArrayEquals(PNG, saved.getImageData());
    }

    @Test
    void savesJpeg() {
        givenEquipmentWithoutImage();
        when(imageRepository.saveAndFlush(any(EquipmentImage.class))).thenAnswer(i -> i.getArgument(0));

        EquipmentImage saved = service.uploadImage(1L,
                new MockMultipartFile("image", "unit.jpg", "image/jpeg", JPEG));

        assertArrayEquals(JPEG, saved.getImageData());
    }

    @Test
    void stripsDirectoriesFromFileName() {
        givenEquipmentWithoutImage();
        when(imageRepository.saveAndFlush(any(EquipmentImage.class))).thenAnswer(i -> i.getArgument(0));

        EquipmentImage saved = service.uploadImage(1L,
                new MockMultipartFile("image", "C:\\Users\\me\\unit.png", "image/png", PNG));

        assertEquals("unit.png", saved.getImageName());
    }

    @Test
    void rejectsUnknownEquipment() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EquipmentNotFoundException.class, () -> service.uploadImage(1L,
                new MockMultipartFile("image", "unit.png", "image/png", PNG)));
    }

    @Test
    void rejectsEquipmentThatAlreadyHasImage() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(mock(Equipment.class)));
        when(imageRepository.existsByEquipmentId(1L)).thenReturn(true);

        assertThrows(EquipmentImageAlreadyExistsException.class, () -> service.uploadImage(1L,
                new MockMultipartFile("image", "unit.png", "image/png", PNG)));
        verify(imageRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsEmptyFileAndNamesTheLimits() {
        givenEquipmentWithoutImage();

        InvalidImageException ex = assertThrows(InvalidImageException.class, () -> service.uploadImage(1L,
                new MockMultipartFile("image", "", "application/octet-stream", new byte[0])));

        assertTrue(ex.getMessage().contains("Allowed file types: JPEG, PNG"));
        assertTrue(ex.getMessage().contains("Maximum file size: 5 MB"));
    }

    @Test
    void rejectsFileOverSizeLimit() {
        properties.setMaxSize(DataSize.ofBytes(10));
        givenEquipmentWithoutImage();

        InvalidImageException ex = assertThrows(InvalidImageException.class, () -> service.uploadImage(1L,
                new MockMultipartFile("image", "unit.png", "image/png", PNG)));

        assertTrue(ex.getMessage().contains("too large"));
        verify(imageRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonImageEvenWhenNamedAndTypedAsPng() {
        givenEquipmentWithoutImage();

        InvalidImageException ex = assertThrows(InvalidImageException.class, () -> service.uploadImage(1L,
                new MockMultipartFile("image", "evil.png", "image/png", "not an image".getBytes())));

        assertTrue(ex.getMessage().contains("Allowed file types: JPEG, PNG"));
        verify(imageRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsGifWhenNotAllowed() {
        givenEquipmentWithoutImage();
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0};

        assertThrows(InvalidImageException.class, () -> service.uploadImage(1L,
                new MockMultipartFile("image", "anim.gif", "image/gif", gif)));
    }

    @Test
    void findImageInfoThrowsWhenEquipmentMissing() {
        when(equipmentRepository.existsById(1L)).thenReturn(false);

        assertThrows(EquipmentNotFoundException.class, () -> service.findImageInfo(1L));
    }

    @Test
    void findImageInfoIsEmptyWhenEquipmentHasNoImage() {
        when(equipmentRepository.existsById(1L)).thenReturn(true);
        when(imageRepository.findInfoByEquipmentId(1L)).thenReturn(Optional.empty());

        assertTrue(service.findImageInfo(1L).isEmpty());
    }

    @Test
    void findImageReturnsTheStoredImageWithItsBytes() {
        EquipmentImage image = new EquipmentImage();
        image.setImageData(PNG);
        when(imageRepository.findByEquipmentId(1L)).thenReturn(Optional.of(image));

        assertArrayEquals(PNG, service.findImage(1L).orElseThrow().getImageData());
    }

    @Test
    void detectsTheTypeOfAnImageFromItsBytes() {
        assertEquals("image/png", EquipmentImageService.detectContentType(PNG));
        assertEquals("image/jpeg", EquipmentImageService.detectContentType(JPEG));
        assertNull(EquipmentImageService.detectContentType("not an image".getBytes()));
    }
}
