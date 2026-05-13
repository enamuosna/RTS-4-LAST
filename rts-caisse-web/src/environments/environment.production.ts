// =============================================================
//  Environnement Angular - PRODUCTION
// =============================================================
//  Activé par fileReplacements dans angular.json :
//    environment.ts -> environment.production.ts lors d'un
//    `ng build --configuration=production` (defaut du builder).
//
//  L'URL de l'API est RELATIVE : "/api". Le gateway (Caddy ou
//  nginx) route /api/* vers le backend sur la même origine,
//  donc pas de CORS et pas de mixed-content en HTTPS.
// =============================================================

export const environment = {
  production: true,
  apiUrl: '/api',
  appName: 'RTS Caisse',
  tokenKey: 'rts_caisse_token',
  userKey: 'rts_caisse_user'
};
