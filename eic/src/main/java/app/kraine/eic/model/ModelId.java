package app.kraine.eic.model;

import java.io.Serializable;
import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelId implements Serializable{
    private Long manufacturer;
    private String description;



    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ModelId))
            return false;
        ModelId that = (ModelId) o;
        return Objects.equals(manufacturer, that.manufacturer) &&
                Objects.equals(description, that.description);
    }



    @Override
    public int hashCode() {
        return Objects.hash(manufacturer, description);
    }
}