/**
 * Versão do app (SemVer) exibida no login e no cabeçalho da área logada.
 * GERIDA AUTOMATICAMENTE pela Action de deploy (scripts/bump-version.sh): a cada
 * deploy na main, o CD calcula a próxima versão pelos commits (feat->MINOR,
 * fix->PATCH, BREAKING->MAJOR), grava aqui, commita ([skip ci]) e cria a tag vX.Y.Z.
 * Não edite manualmente — em dev pode ficar defasada; produção mostra a versão do release.
 */
export const APP_VERSION = '1.8.0';
