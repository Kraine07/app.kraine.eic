package app.kraine.eic.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequestMapping("/dashboard")
public class EquipmentController {


    @GetMapping
    public String getEquipmentPage() {
        return "dashboard";
    }
}
