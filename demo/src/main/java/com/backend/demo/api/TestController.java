package com.backend.demo.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/rest/hola")
public class TestController {

    @GetMapping
    public String saludar() {
        return "¡Hola desde Spring Boot!";
    }
}
