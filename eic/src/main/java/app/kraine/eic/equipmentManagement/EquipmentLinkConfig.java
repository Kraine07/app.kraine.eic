package app.kraine.eic.equipmentManagement;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import app.kraine.eic.locationManagement.LocationEquipmentLinks;
import app.kraine.eic.manufacturerManagement.ManufacturerEquipmentLinks;
import app.kraine.eic.modelManagement.ModelEquipmentLinks;
import app.kraine.eic.propertyManagement.PropertyEquipmentLinks;
import app.kraine.eic.repository.EquipmentRepoInterface;
import app.kraine.eic.repository.ModelRepoInterface;
 
/**
 * Now that equipment exists, these supply the "is anything still using it?" checks that the property,
 * location, model and manufacturer screens left optional. Each screen then refuses a delete (or a
 * rename) with a clear message before it touches the database, instead of relying on a foreign key.
 */
@Configuration
public class EquipmentLinkConfig {
 
    @Bean
    PropertyEquipmentLinks propertyEquipmentLinks(EquipmentRepoInterface equipment) {
        return equipment::existsByLocationPropertyId;
    }
 
    @Bean
    LocationEquipmentLinks locationEquipmentLinks(EquipmentRepoInterface equipment) {
        return equipment::existsByLocationPropertyIdAndLocationName;
    }
 
    @Bean
    ModelEquipmentLinks modelEquipmentLinks(EquipmentRepoInterface equipment) {
        return equipment::existsByModelManufacturerIdAndModelDescription;
    }
 
    /** A manufacturer is in use when it has models, or when equipment uses one of its models. */
    @Bean
    ManufacturerEquipmentLinks manufacturerEquipmentLinks(EquipmentRepoInterface equipment, ModelRepoInterface models) {
        return manufacturerId -> models.existsByManufacturerId(manufacturerId)
                || equipment.existsByModelManufacturerId(manufacturerId);
    }
}