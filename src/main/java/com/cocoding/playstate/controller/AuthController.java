package com.cocoding.playstate.controller;

import com.cocoding.playstate.service.UserRegistrationService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRegistrationService userRegistrationService;

    public AuthController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @GetMapping("/login")
    public String loginPage(Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("title", "Sign in");
        return "pages/login";
    }

    @GetMapping("/register")
    public String registerPage(Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("title", "Create account");
        return "pages/register";
    }

    @PostMapping("/register")
    public String registerSubmit(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes) {
        return userRegistrationService
                .validateAndRegister(username, email, password, confirmPassword)
                .map(
                        error -> {
                            redirectAttributes.addFlashAttribute("registerError", error);
                            redirectAttributes.addFlashAttribute("registerUsername", username);
                            redirectAttributes.addFlashAttribute("registerEmail", email);
                            return "redirect:/register";
                        })
                .orElse("redirect:/login?registered");
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
