package com.shivendra.ecommerce.dtos;

import java.time.Instant;

public record LoginResponseDto(String token, String email, String name, Instant expiresAt) {
}
