package br.com.ice.ebd.dto;

/** Redefinição via token + nova senha. */
public record RedefinirSenhaRequest(String token, String novaSenha) {}
