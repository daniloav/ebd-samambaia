// Produção: as chamadas vão para /api no mesmo host (o nginx faz o proxy para o backend).
export const environment = {
  production: true,
  apiUrl: '/api',
};
