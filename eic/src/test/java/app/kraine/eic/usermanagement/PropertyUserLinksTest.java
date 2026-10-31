package app.kraine.eic.usermanagement;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.kraine.eic.repository.PropertyRepoInterface;
import app.kraine.eic.userManagement.PropertyUserLinks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyUserLinksTest {

    @Mock private PropertyRepoInterface propertyRepository;
    @InjectMocks private PropertyUserLinks links;

    @Test
    void aUserAssignedToAPropertyIsLinked() {
        when(propertyRepository.existsByUserId(7L)).thenReturn(true);

        assertTrue(links.isLinkedToProperty(7L));
    }

    @Test
    void aUserWithoutAPropertyIsNotLinked() {
        when(propertyRepository.existsByUserId(8L)).thenReturn(false);

        assertFalse(links.isLinkedToProperty(8L));
    }
}
