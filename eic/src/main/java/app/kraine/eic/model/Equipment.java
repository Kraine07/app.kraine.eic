package app.kraine.eic.model;

import java.time.YearMonth;



import com.fasterxml.jackson.annotation.JsonIgnore;

import app.kraine.eic.config.YearMonthConveter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true, unique = true)
    private String serialNumber;

    @Column(nullable = true)
    @Convert(converter = YearMonthConveter.class)
    private YearMonth manufacturedDate;


    @ManyToOne
    @NotNull(message = "Model cannot be null")
    @JoinColumns({
        @JoinColumn(name = "model_manufacturer_id", referencedColumnName = "manufacturer_id"),
        @JoinColumn(name = "model_description", referencedColumnName = "description")
    })
    private Model model;



    @ManyToOne
    @NotNull(message = "Location cannot be null")
    @JoinColumns({
        @JoinColumn(name = "location_property_id", referencedColumnName = "property_id"),
        @JoinColumn(name = "location_name", referencedColumnName = "name")
    })
    private Location location;



    @JsonIgnore
    @OneToOne(mappedBy = "equipment", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private EquipmentImage image;

}