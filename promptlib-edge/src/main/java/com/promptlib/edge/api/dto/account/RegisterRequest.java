package com.promptlib.edge.api.dto.account;

public record RegisterRequest(String username, String email, String password, String displayName) {
}
