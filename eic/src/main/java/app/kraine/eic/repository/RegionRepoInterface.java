package app.kraine.eic.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import app.kraine.eic.model.Region;


public interface RegionRepoInterface extends JpaRepository<Region, Long> {


    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);


}
