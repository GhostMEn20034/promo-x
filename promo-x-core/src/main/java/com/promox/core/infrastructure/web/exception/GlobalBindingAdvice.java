package com.promox.core.infrastructure.web.exception;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalBindingAdvice {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        var stringTrimmerEditor = new StringTrimmerEditor(false);

        binder.registerCustomEditor(String.class, stringTrimmerEditor);
    }
}
