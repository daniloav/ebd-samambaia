package br.com.icev.ebd.dto;

public record LoginResponse(
        String token,
        String username,
        String role,
        long expiresInSeconds) {
}
