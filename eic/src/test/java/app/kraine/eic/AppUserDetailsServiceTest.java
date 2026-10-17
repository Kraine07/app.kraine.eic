package app.kraine.eic;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import app.kraine.eic.model.User;
import app.kraine.eic.repository.UserRepoInterface;
import app.kraine.eic.service.AppUserDetailsService;

import java.util.Optional;
 
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
 
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {
 
    @Mock private UserRepoInterface userRepository;
    @InjectMocks private AppUserDetailsService service;
 
    
}
 
