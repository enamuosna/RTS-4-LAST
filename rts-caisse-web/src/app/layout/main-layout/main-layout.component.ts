import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Role } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';

/** Cle de persistence de l'etat reduit/etendu de la sidebar dans le localStorage. */
const SIDEBAR_COLLAPSED_KEY = 'rts-sidebar-collapsed';

/** Une entree de menu, regroupee par section. */
interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles: Role[];
}

/** Un groupe d'entrees de menu sous un meme titre de section. */
interface NavSection {
  /** Titre court (4-12 lettres) affiche en uppercase quand la sidebar est etendue. */
  label: string;
  items: NavItem[];
}

@Component({
  selector: 'rts-main-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatListModule,
    MatDividerModule,
    MatMenuModule,
    MatTooltipModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.css']
})
export class MainLayoutComponent {
  private readonly auth = inject(AuthService);

  readonly currentUser = this.auth.currentUser;

  readonly initiales = computed(() => {
    const name = this.currentUser()?.nomComplet ?? '';
    return name
      .split(' ')
      .filter((p) => p.length > 0)
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join('');
  });

  /** Sidebar reduite (icones uniquement) ou etendue (icones + libelles).
   *  Persiste dans le localStorage pour respecter la preference de l'utilisateur. */
  readonly collapsed = signal<boolean>(this.loadCollapsedState());

  /** Sections de navigation, regroupees pour une meilleure lisibilite.
   *  Chaque section est filtree selon les roles autorises sur ses items. */
  private readonly navSections = signal<NavSection[]>([
    {
      label: 'Vue d\'ensemble',
      items: [
        { label: 'Tableau de bord', icon: 'dashboard',      route: '/dashboard',    roles: ['ADMIN', 'SUPERVISEUR', 'CAISSIER', 'AGENT_RECETTE'] },
        { label: 'Supervision',     icon: 'monitor_heart',  route: '/supervision',  roles: ['ADMIN', 'SUPERVISEUR', 'AGENT_RECETTE'] }
      ]
    },
    {
      label: 'Gestion caisse',
      items: [
        { label: 'Caisses',    icon: 'point_of_sale', route: '/caisses',    roles: ['ADMIN', 'SUPERVISEUR'] },
        { label: 'Opérations', icon: 'receipt_long',  route: '/operations', roles: ['ADMIN', 'SUPERVISEUR', 'CAISSIER', 'AGENT_RECETTE'] },
        { label: 'Journaux',   icon: 'event_note',    route: '/journaux',   roles: ['ADMIN', 'SUPERVISEUR', 'AGENT_RECETTE'] },
        { label: 'Clients',    icon: 'business',      route: '/clients',    roles: ['ADMIN', 'SUPERVISEUR', 'CAISSIER', 'AGENT_RECETTE'] }
      ]
    },
    {
      label: 'Configuration',
      items: [
        { label: 'Catégories',   icon: 'category',        route: '/categories',   roles: ['ADMIN'] },
        { label: 'Utilisateurs', icon: 'people',          route: '/utilisateurs', roles: ['ADMIN'] },
        { label: 'Banques',      icon: 'account_balance', route: '/banques',      roles: ['ADMIN'] },
        { label: 'Paramètres',   icon: 'tune',            route: '/parametres',   roles: ['ADMIN'] }
      ]
    },
    {
      label: 'Système',
      items: [
        { label: 'Journal d\'audit', icon: 'fact_check', route: '/audit',  roles: ['ADMIN'] },
        { label: 'Sauvegarde',       icon: 'backup',     route: '/backup', roles: ['ADMIN'] }
      ]
    }
  ]);

  /** Sections visibles pour le role courant : les sections vides sont retirees,
   *  et a l'interieur de chaque section seuls les items autorises restent. */
  readonly visibleSections = computed<NavSection[]>(() => {
    return this.navSections()
      .map(section => ({
        label: section.label,
        items: section.items.filter(item => this.auth.hasRole(...item.roles))
      }))
      .filter(section => section.items.length > 0);
  });

  toggleSidebar(): void {
    const next = !this.collapsed();
    this.collapsed.set(next);
    try {
      localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? '1' : '0');
    } catch { /* ignore quota / private mode */ }
  }

  logout(): void {
    this.auth.logout();
  }

  private loadCollapsedState(): boolean {
    try {
      return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1';
    } catch {
      return false;
    }
  }
}
