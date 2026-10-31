package app.kraine.eic.equipment;

import app.kraine.eic.equipmentManagement.EquipmentLinkConfig;
import app.kraine.eic.repository.EquipmentRepoInterface;
import app.kraine.eic.repository.ModelRepoInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentLinkConfigTest {

    @Mock private EquipmentRepoInterface equipmentRepository;
    @Mock private ModelRepoInterface modelRepository;

    private final EquipmentLinkConfig config = new EquipmentLinkConfig();

    @Test
    void aPropertyIsInUseWhenEquipmentSitsAtOneOfItsLocations() {
        when(equipmentRepository.existsByLocationPropertyId(10L)).thenReturn(true);

        assertTrue(config.propertyEquipmentLinks(equipmentRepository).isLinkedToEquipment(10L));
        assertFalse(config.propertyEquipmentLinks(equipmentRepository).isLinkedToEquipment(11L));
    }

    @Test
    void aLocationIsInUseWhenEquipmentSitsThere() {
        when(equipmentRepository.existsByLocationPropertyIdAndLocationName(10L, "Store")).thenReturn(true);

        assertTrue(config.locationEquipmentLinks(equipmentRepository).isLinkedToEquipment(10L, "Store"));
        assertFalse(config.locationEquipmentLinks(equipmentRepository).isLinkedToEquipment(10L, "Yard"));
    }

    @Test
    void aModelIsInUseWhenEquipmentUsesIt() {
        when(equipmentRepository.existsByModelManufacturerIdAndModelDescription(1L, "X100")).thenReturn(true);

        assertTrue(config.modelEquipmentLinks(equipmentRepository).isLinkedToEquipment(1L, "X100"));
        assertFalse(config.modelEquipmentLinks(equipmentRepository).isLinkedToEquipment(1L, "Y200"));
    }

    @Test
    void aManufacturerIsInUseWhenItHasModels() {
        when(modelRepository.existsByManufacturerId(1L)).thenReturn(true);

        assertTrue(config.manufacturerEquipmentLinks(equipmentRepository, modelRepository).isLinked(1L));
        verifyNoInteractions(equipmentRepository);
    }

    @Test
    void aManufacturerIsInUseWhenEquipmentUsesOneOfItsModels() {
        when(modelRepository.existsByManufacturerId(1L)).thenReturn(false);
        when(equipmentRepository.existsByModelManufacturerId(1L)).thenReturn(true);

        assertTrue(config.manufacturerEquipmentLinks(equipmentRepository, modelRepository).isLinked(1L));
        verify(equipmentRepository).existsByModelManufacturerId(1L);
    }

    @Test
    void aManufacturerWithNeitherIsFree() {
        when(modelRepository.existsByManufacturerId(1L)).thenReturn(false);
        when(equipmentRepository.existsByModelManufacturerId(1L)).thenReturn(false);

        assertFalse(config.manufacturerEquipmentLinks(equipmentRepository, modelRepository).isLinked(1L));
    }
}
