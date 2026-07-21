package br.com.ice.ebd.dto;

/** Troca da própria senha pelo usuário autenticado. */
public record TrocarSenhaRequest(String senhaAtual, String novaSenha) {}
