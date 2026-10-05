package com.zerotap.controller;

import com.zerotap.dto.UserDto;
import com.zerotap.model.TrustedContact;
import com.zerotap.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserProfile(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }

    @GetMapping("/{id}/contacts")
    public ResponseEntity<List<TrustedContact>> getTrustedContacts(@PathVariable String id) {
        return ResponseEntity.ok(userService.getTrustedContacts(id));
    }

    @PostMapping("/{id}/contacts")
    public ResponseEntity<TrustedContact> addTrustedContact(@PathVariable String id, @RequestBody TrustedContact contact) {
        return ResponseEntity.ok(userService.addTrustedContact(id, contact));
    }
}
