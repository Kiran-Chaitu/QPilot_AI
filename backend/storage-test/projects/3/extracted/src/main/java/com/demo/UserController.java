package com.demo;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private static final String API_KEY = "sk_live_abcdef1234567890secret";

    @GetMapping("/api/users")
    public String listUsers() { return "[]"; }

    @GetMapping("/api/users/{id}")
    public String getUser(@PathVariable String id) {
        String sql = "SELECT * FROM users WHERE id = '" + id + "'";
        return jdbc.executeQuery("SELECT * FROM users WHERE id = '" + id + "'");
    }

    @PostMapping("/api/users")
    public String createUser(@RequestBody String body) {
        try { return process(body); } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    @DeleteMapping("/api/admin/purge")
    public String purge() { return "ok"; }

    public String process(String input) { return input; }
}
