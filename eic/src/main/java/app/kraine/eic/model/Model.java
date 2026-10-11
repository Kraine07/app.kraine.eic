package app.kraine.eic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(ModelId.class) // Composite key class


public class Model {

    @Id
    @ManyToOne
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;



    @Id
    @NotBlank(message = "Model name cannot be blank")
    @Column(nullable = false)
    private String description;



    public ModelId getModelId() {
        return new ModelId(manufacturer.getId(),description);
    }

}