package app.kraine.eic.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import app.kraine.eic.model.Manufacturer;

public interface ManufacturerRepoInterface extends JpaRepository<Manufacturer, Long>{

    // ManufacturerRepoInterface extends JpaRepository<Manufacturer, Long>
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}


