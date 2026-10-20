package app.kraine.eic.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import app.kraine.eic.exception.EquipmentImageAlreadyExistsException;
import app.kraine.eic.exception.InvalidImageException;
import app.kraine.eic.service.EquipmentImageService;
 
@Controller
@RequestMapping("/equipment/{equipmentId}/image")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'EDITOR')")
@RequiredArgsConstructor
public class EquipmentImageController {
 
    private static final String UPLOAD_VIEW = "equipment/upload-image";
 
    private final EquipmentImageService imageService;
 



    /** Steps 3-4: the 'Upload Image' button opens this form. */
    @GetMapping("/upload")
    public String showUploadForm(@PathVariable Long equipmentId, Model model, RedirectAttributes redirect) {
        try {
            imageService.assertCanUpload(equipmentId);
        } catch (EquipmentImageAlreadyExistsException ex) {
            return backToEquipment(equipmentId, redirect, ex.getMessage());
        }
        populateForm(model, equipmentId);
        return UPLOAD_VIEW;
    }
 




    /** Steps 6-9: 'Submit' validates and saves the image, then confirms. */
    @PostMapping
    public String uploadImage(@PathVariable Long equipmentId,
                            @RequestParam(value = "image", required = false) MultipartFile image,
                            Model model,
                            RedirectAttributes redirect) {
        try {
            imageService.uploadImage(equipmentId, image);
        } catch (InvalidImageException ex) {
            // Alternative flow: show the error and let the user pick another file.
            populateForm(model, equipmentId);
            model.addAttribute("errorMessage", ex.getMessage());
            return UPLOAD_VIEW;
        } catch (EquipmentImageAlreadyExistsException ex) {
            return backToEquipment(equipmentId, redirect, ex.getMessage());
        }
        redirect.addFlashAttribute("successMessage", "Image uploaded successfully.");
        return "redirect:/equipment/" + equipmentId;
    }




    private void populateForm(Model model, Long equipmentId) {
        model.addAttribute("equipmentId", equipmentId);
        model.addAttribute("limits", imageService.limitsDescription());
        model.addAttribute("accept", imageService.acceptAttribute());
    }






    private String backToEquipment(Long equipmentId, RedirectAttributes redirect, String message) {
        redirect.addFlashAttribute("errorMessage", message);
        return "redirect:/equipment/" + equipmentId;
    }
}