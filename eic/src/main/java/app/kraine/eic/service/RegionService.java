package app.kraine.eic.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.kraine.eic.audit.AuditEventType;
import app.kraine.eic.audit.AuditService;
import app.kraine.eic.exception.DuplicateRegionNameException;
import app.kraine.eic.exception.RegionInUseException;
import app.kraine.eic.exception.RegionNotFoundException;
import app.kraine.eic.form.RegionForm;
import app.kraine.eic.model.Region;
import app.kraine.eic.regionManagement.RegionViews.RegionEditData;
import app.kraine.eic.regionManagement.RegionViews.RegionRow;
import app.kraine.eic.repository.PropertyRepoInterface;
import app.kraine.eic.repository.RegionRepoInterface;

import java.util.Comparator;
import java.util.List;
 
/**
 * RM-2 to RM-4 / UC-14 to UC-16: creating, renaming and deleting regions. Every change is audited with the
 * acting Administrator (UA-25).
 *
 * <p>Needs {@code RegionRepoInterface.existsByNameIgnoreCase} and {@code existsByNameIgnoreCaseAndIdNot}, and
 * {@code PropertyRepoInterface.existsByRegionId} (see REPOSITORY-ADDITIONS.md). RM-4 also mentions users, but a
 * user has no region in your model, so properties are the only link to check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionService {
 
    private final RegionRepoInterface regionRepository;
    private final PropertyRepoInterface propertyRepository;
    private final AuditService auditService;
 
    // ------------------------------------------------------------------ read
 
    @Transactional(readOnly = true)
    public List<RegionRow> list() {
        return regionRepository.findAll().stream()
                .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER))
                .map(region -> new RegionRow(region.getId(), region.getName()))
                .toList();
    }
 
    @Transactional(readOnly = true)
    public RegionEditData findForEdit(Long id) {
        Region region = find(id);
        RegionForm form = new RegionForm();
        form.setName(region.getName());
        return new RegionEditData(region.getId(), form);
    }
 
    // ------------------------------------------------------------------ UC-14 create
 
    @Transactional
    public Region createRegion(RegionForm form, String actor) {
        String name = form.getName().trim();
        if (regionRepository.existsByNameIgnoreCase(name)) {                       // step 7
            throw new DuplicateRegionNameException(name);
        }
        Region region = new Region();
        region.setName(name);
        Region saved;
        try {
            saved = regionRepository.saveAndFlush(region);                         // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateRegionNameException(name); // another request used the name a moment ago
        }
        auditService.record(AuditEventType.REGION_CREATED, actor, saved.getName(), null);
        log.info("Region {} created by {}", saved.getName(), actor);
        return saved;
    }
 
    // ------------------------------------------------------------------ UC-15 update
 
    @Transactional
    public void updateRegion(Long id, RegionForm form, String actor) {
        Region region = find(id);
        String oldName = region.getName();
        String newName = form.getName().trim();
        if (oldName.equals(newName)) {
            return; // nothing to change
        }
        // The check leaves out this region itself, so a change of letter case only is fine.
        if (regionRepository.existsByNameIgnoreCaseAndIdNot(newName, id)) {        // step 7
            throw new DuplicateRegionNameException(newName);
        }
        region.setName(newName);
        try {
            regionRepository.saveAndFlush(region);                                 // step 8
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateRegionNameException(newName);
        }
        auditService.record(AuditEventType.REGION_UPDATED, actor, newName, "Renamed from " + oldName);
        log.info("Region {} renamed to {} by {}", oldName, newName, actor);
    }
 
    // ------------------------------------------------------------------ UC-16 delete
 
    @Transactional
    public void deleteRegion(Long id, String actor) {
        Region region = find(id);
        if (propertyRepository.existsByRegionId(id)) {                             // step 5
            throw new RegionInUseException();
        }
        String name = region.getName();
        try {
            regionRepository.delete(region);                                       // step 6
            regionRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new RegionInUseException(); // some other record still points at it
        }
        auditService.record(AuditEventType.REGION_DELETED, actor, name, null);
        log.info("Region {} deleted by {}", name, actor);
    }
 
    private Region find(Long id) {
        return regionRepository.findById(id).orElseThrow(() -> new RegionNotFoundException(id));
    }
}
 