package com.testforge.backend.auth.dto;

import com.testforge.backend.auth.entity.Role;

public record UserSummary(Long id, String fullName, String email, Role role) {
}
