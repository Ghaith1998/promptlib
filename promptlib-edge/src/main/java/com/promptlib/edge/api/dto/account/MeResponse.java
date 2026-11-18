package com.promptlib.edge.api.dto.account;

import java.util.List;

public record MeResponse(String id, String username, String email, String displayName, List<String> roles) {
}
