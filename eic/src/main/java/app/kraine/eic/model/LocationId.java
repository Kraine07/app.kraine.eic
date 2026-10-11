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
public class LocationId implements Serializable{

    private Long property;
    private String name;




    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LocationId))
            return false;
        LocationId that = (LocationId) o;
        return Objects.equals(property, that.property) &&
                Objects.equals(name, that.name);
    }



    @Override
    public int hashCode() {
        return Objects.hash(property, name);
    }
}
