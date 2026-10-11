package app.kraine.eic.service;

import java.util.List;

import app.kraine.eic.model.Role;
import app.kraine.eic.model.RoleType;
import org.springframework.stereotype.Service;
import app.kraine.eic.repository.RoleRepositoryInterface;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class RoleService {

    final RoleRepositoryInterface roleRepositoryInterface = null;

    public List<Role> getRoles() {
        return roleRepositoryInterface.findAll();
    }


    public Role getRole(RoleType roleType){
        return roleRepositoryInterface.findByRoleType(roleType);
    }
}