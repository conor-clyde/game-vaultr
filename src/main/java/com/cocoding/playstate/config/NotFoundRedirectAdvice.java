package com.cocoding.playstate.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class NotFoundRedirectAdvice {

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public String notFound() {
        return "redirect:/";
    }
}
