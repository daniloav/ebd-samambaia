package br.com.ice.ebd.model;

/**
 * Perfil BASE do usuário. Tesoureiro e líder NÃO são roles base — são
 * capacidades (flags eh_tesoureiro/eh_lider em Usuario) que qualquer perfil
 * pode acumular. Os nomes "TESOUREIRO"/"LIDER" seguem existindo como grupos
 * do JWT (emitidos pelo TokenService) para os @RolesAllowed do módulo.
 */
public enum Role {
    ADMIN,
    PROFESSOR,
    ALUNO
}
