package com.cocoding.playstate.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {

  @GetMapping("/privacy")
  public String privacy(Model model) {
    model.addAttribute("title", "Privacy");
    return "pages/privacy";
  }

  @GetMapping("/contact")
  public String contact(Model model) {
    model.addAttribute("title", "Contact");
    return "pages/contact";
  }
}
