package br.com.ice.ebd.dto;

/**
 * Evento de uso de funcionalidade enviado pelo front (page view ou clique notável).
 * {@code acao} é "ABRIR" (default) ou "CLICAR".
 */
public record UsoEventoRequest(String recurso, String acao) {}
