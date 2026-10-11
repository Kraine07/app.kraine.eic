package app.kraine.eic.userManagement;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import app.kraine.eic.repository.PropertyRepoInterface;
import lombok.RequiredArgsConstructor;

/**
 * A user is linked to a property when a Property row points at them through its {@code user} field.
 *
 * <p>Needs this method on your property repository:
 * {@code boolean existsByUserId(Long userId);}
 */
@Component
@RequiredArgsConstructor
public class PropertyUserLinks implements UserPropertyLinks {

    private final PropertyRepoInterface propertyRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isLinkedToProperty(Long userId) {
        return propertyRepository.existsByUserId(userId);
    }
}