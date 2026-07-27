package br.com.ice.ebd.dto;

public record LoginResponse(
        String token,
        String username,
        long expiresInSeconds,
        boolean precisaTrocarSenha,
        boolean ehAdmin,
        boolean ehProfessor,
        boolean ehAluno,
        boolean ehTesoureiro,
        boolean ehLider) {
}
