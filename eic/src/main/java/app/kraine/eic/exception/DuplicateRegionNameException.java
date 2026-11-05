package app.kraine.eic.exception;


/** UC-14 step 7 / UC-15 step 7 (RM-2, RM-3): another region already has this name. */
public class DuplicateRegionNameException extends RuntimeException {

    public DuplicateRegionNameException(String name) {
        super("The region name " + name + " is already in use.");
    }
}