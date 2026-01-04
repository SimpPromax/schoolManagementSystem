package com.system.SchoolManagementSystem;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class Testcontroller {

    @GetMapping("/ping")
    public String ping() {
        return "pong - " + System.currentTimeMillis();
    }
}