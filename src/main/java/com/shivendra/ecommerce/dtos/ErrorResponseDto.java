package com.shivendra.ecommerce.dtos;

import java.time.Instant;

public record ErrorResponseDto(int status, String error, String message, Instant timestamp) {
}
