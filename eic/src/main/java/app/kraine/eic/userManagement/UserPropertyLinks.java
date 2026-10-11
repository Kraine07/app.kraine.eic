package app.kraine.eic.userManagement;

/**
 * UC-06 step 5: tells the user screens whether a user is still assigned to a property, so the
 * user cannot be deleted while they are. {@link PropertyUserLinks} is the implementation.
 */
public interface UserPropertyLinks {

    boolean isLinkedToProperty(Long userId);
}