package app.kraine.eic.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import app.kraine.eic.model.Region;


public interface RegionRepoInterface extends JpaRepository<Region, Long> {
    
}
