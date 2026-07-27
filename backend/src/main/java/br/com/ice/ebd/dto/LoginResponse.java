package br.com.ice.ebd.dto;

public record LoginResponse(
        String token,
        String username,
        String role,
        long expiresInSeconds,
        boolean precisaTrocarSenha,
        boolean ehTesoureiro,
        boolean ehLider) {
}
