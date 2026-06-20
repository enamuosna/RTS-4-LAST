import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'admin/audit',
    loadComponent: () =>
        import('./features/audit/audit-list.component')
            .then(m => m.AuditListComponent),
    canActivate: [roleGuard(['ADMIN'])]
  },
  {
    path: '',
    loadComponent: () =>
      import('./layout/main-layout/main-layout.component').then((m) => m.MainLayoutComponent),
    canActivate: [authGuard],
    children: [
      // Route par defaut : /dashboard est accessible a TOUS les roles
      // (ADMIN, SUPERVISEUR, CAISSIER, AGENT_RECETTE), donc aucun risque
      // de boucle "acces refuse" peu importe le role connecte.
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
        // CAISSIER autorisé : le backend filtre automatiquement sur ses propres opérations.
        // CONTROLEUR + chefs validateurs : vue d'ensemble en lecture seule
        // (sert aussi de page de repli pour /unauthorized).
        canActivate: [roleGuard([
          'ADMIN', 'SUPERVISEUR', 'CAISSIER', 'AGENT_RECETTE', 'CONTROLEUR',
          'CHEF_UNITE_FINANCES', 'CHEF_DEPARTEMENT'
        ])]
      },
      {
        path: 'utilisateurs',
        loadComponent: () =>
          import('./features/utilisateurs/utilisateurs.component').then(
            (m) => m.UtilisateursComponent
          ),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'caisses',
        loadComponent: () =>
          import('./features/caisses/caisses.component').then((m) => m.CaissesComponent),
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR'])]
      },
      {
        path: 'categories',
        loadComponent: () =>
          import('./features/categories/categories.component').then((m) => m.CategoriesComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'banques',
        loadComponent: () => import('./features/banques/banques.component')
            .then(m => m.BanquesComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'clients',
        loadComponent: () =>
          import('./features/clients/clients.component').then((m) => m.ClientsComponent),
        // CONTROLEUR exclu : il est en lecture seule et ne gère pas le référentiel clients.
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR', 'CAISSIER', 'AGENT_RECETTE'])]
      },
      {
        path: 'operations',
        loadComponent: () =>
          import('./features/operations/operations.component').then((m) => m.OperationsComponent),
        // CONTROLEUR autorisé : consultation en lecture seule (aucune action de mutation).
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR', 'CAISSIER', 'AGENT_RECETTE', 'CONTROLEUR'])]
      },
      {
        path: 'journaux',
        loadComponent: () =>
          import('./features/journaux/journaux.component').then((m) => m.JournauxComponent),
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR', 'AGENT_RECETTE', 'CONTROLEUR'])]
      },
      {
        path: 'recettes',
        loadComponent: () =>
          import('./features/recettes/recettes.component').then((m) => m.RecettesComponent),
        canActivate: [roleGuard([
          'ADMIN', 'SUPERVISEUR', 'CONTROLEUR', 'CHEF_UNITE_FINANCES', 'CHEF_DEPARTEMENT'
        ])]
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./features/audit/audit-list.component').then((m) => m.AuditListComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'parametres',
        loadComponent: () =>
          import('./features/parametres/parametres.component').then((m) => m.ParametresComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'timbre',
        loadComponent: () =>
          import('./features/timbre/timbre.component').then((m) => m.TimbreComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'langues',
        loadComponent: () =>
          import('./features/langues/langues.component').then((m) => m.LanguesComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'supervision',
        loadComponent: () =>
          import('./features/supervision/supervision.component').then((m) => m.SupervisionComponent),
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR', 'AGENT_RECETTE', 'CONTROLEUR'])]
      },
      {
        path: 'supervision/caisse/:id',
        loadComponent: () =>
          import('./features/supervision/caisse-detail/caisse-detail.component')
            .then((m) => m.CaisseDetailComponent),
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR', 'AGENT_RECETTE', 'CONTROLEUR'])]
      },
      {
        path: 'backup',
        loadComponent: () =>
          import('./features/backup/backup.component').then((m) => m.BackupComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      {
        path: 'versements',
        loadComponent: () =>
          import('./features/versements/versements.component').then((m) => m.VersementsComponent),
        canActivate: [roleGuard(['ADMIN', 'SUPERVISEUR', 'CAISSIER', 'AGENT_RECETTE', 'CONTROLEUR'])]
      },
      {
        path: 'maintenance/purge-operations',
        loadComponent: () =>
          import('./features/maintenance/purge-operations.component')
            .then((m) => m.PurgeOperationsComponent),
        canActivate: [roleGuard(['ADMIN'])]
      },
      // Si un guard refuse un acces, on renvoie sur /dashboard (page accessible
      // a TOUS les roles) plutot que /caisses qui exclut maintenant AGENT_RECETTE.
      { path: 'unauthorized', redirectTo: 'dashboard' }
    ]
  },
  { path: '**', redirectTo: '' }
];
