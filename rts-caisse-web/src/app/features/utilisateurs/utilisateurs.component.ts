import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Role, Utilisateur } from '../../core/models/models';
import { UtilisateurService } from '../../core/services/admin.services';
import { UtilisateurDialogComponent } from './dialogs/utilisateur-dialog.component';
import { ModifierLoginDialogComponent } from './dialogs/modifier-login-dialog.component';
import { ModifierRoleDialogComponent } from './dialogs/modifier-role-dialog.component';
import { ReinitialiserMdpDialogComponent } from './dialogs/reinitialiser-mdp-dialog.component';


@Component({
  selector: 'rts-utilisateurs',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    MatTooltipModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './utilisateurs.component.html',
  styleUrls: ['./utilisateurs.component.css']
})
export class UtilisateursComponent implements OnInit {
  private readonly service  = inject(UtilisateurService);
  private readonly dialog   = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly http     = inject(HttpClient);

  readonly utilisateurs = signal<Utilisateur[]>([]);
  readonly isSuperAdmin = signal(false);
  readonly colonnes = ['matricule', 'nom', 'login', 'email', 'role', 'actif', 'actions'];

  // ==================================================================
  //  Recherche + pagination cote client (la liste est rarement enorme)
  // ==================================================================

  /** Texte de recherche libre (matricule, nom, login, email). */
  readonly recherche = signal<string>('');
  /** Page courante (0-based) et taille de page pour mat-paginator. */
  readonly pageIndex = signal<number>(0);
  readonly pageSize  = signal<number>(10);

  /** Liste filtree selon la recherche (insensible a la casse). */
  readonly utilisateursFiltres = computed<Utilisateur[]>(() => {
    const q = this.recherche().trim().toLowerCase();
    const all = this.utilisateurs();
    if (!q) return all;
    return all.filter(u =>
        (u.matricule  ?? '').toLowerCase().includes(q) ||
        (u.prenom     ?? '').toLowerCase().includes(q) ||
        (u.nom        ?? '').toLowerCase().includes(q) ||
        (u.login      ?? '').toLowerCase().includes(q) ||
        (u.email      ?? '').toLowerCase().includes(q));
  });

  /** Page courante a afficher dans le mat-table. */
  readonly utilisateursPage = computed<Utilisateur[]>(() => {
    const start = this.pageIndex() * this.pageSize();
    return this.utilisateursFiltres().slice(start, start + this.pageSize());
  });

  // ==================================================================
  //  Statistiques (calcul reactif depuis la liste complete)
  // ==================================================================

  readonly stats = computed(() => {
    const all = this.utilisateurs();
    const par = (r: Role) => all.filter(u => u.role === r).length;
    return {
      total:       all.length,
      actifs:      all.filter(u => u.actif).length,
      inactifs:    all.filter(u => !u.actif).length,
      admin:       par('ADMIN'),
      superviseur: par('SUPERVISEUR'),
      agent:       par('AGENT_RECETTE'),
      caissier:    par('CAISSIER')
    };
  });

  ngOnInit(): void {
    this.http
        .get<{ superAdmin: boolean }>('/api/utilisateurs/me/super-admin')
        .subscribe(r => this.isSuperAdmin.set(r.superAdmin));

    this.charger();
  }

  charger(): void {
    this.service.lister().subscribe(list => {
      this.utilisateurs.set(list);
      // Si la nouvelle liste est plus courte, on remet la page a 0 pour
      // eviter d'afficher une page vide apres une suppression.
      this.pageIndex.set(0);
    });
  }

  /** Recherche : on revient toujours sur la premiere page. */
  onRechercheChange(value: string): void {
    this.recherche.set(value);
    this.pageIndex.set(0);
  }

  /** Pagination Material : on persiste juste l'index + la taille. */
  changerPage(e: PageEvent): void {
    this.pageIndex.set(e.pageIndex);
    this.pageSize.set(e.pageSize);
  }

  creer(): void {
    this.dialog
        .open(UtilisateurDialogComponent, { width: '620px' })
        .afterClosed()
        .subscribe(created => {
          if (created) this.charger();
        });
  }

  activer(u: Utilisateur, actif: boolean): void {
    this.service.activer(u.id, actif).subscribe(() => {
      this.snackBar.open(
          actif ? 'Utilisateur activé' : 'Utilisateur désactivé',
          'OK',
          { duration: 2500, panelClass: ['snackbar-info'] }
      );
      this.charger();
    });
  }

  supprimer(u: Utilisateur): void {
    if (!confirm(`Désactiver l'utilisateur "${u.login}" ?`)) return;
    this.service.supprimer(u.id).subscribe(() => {
      this.snackBar.open('Utilisateur désactivé', 'OK', {
        duration: 2500,
        panelClass: ['snackbar-info']
      });
      this.charger();
    });
  }

  /**
   * Ouvre un dialog pour modifier le login d'un utilisateur.
   * (réservé super-admin — la garde côté UI affiche déjà le bouton uniquement
   *  si isSuperAdmin() est vrai, et le backend revérifie de toute façon)
   */
  modifierLogin(u: Utilisateur): void {
    this.dialog
        .open(ModifierLoginDialogComponent, {
          width: '460px',
          data: { utilisateur: u },
          autoFocus: 'first-tabbable'
        })
        .afterClosed()
        .subscribe(modifie => {
          if (modifie) this.charger();
        });
  }

  /**
   * Ouvre un dialog pour modifier le role d'un utilisateur. Reserve aux ADMIN.
   * Le backend rejette la modification du role du super admin.
   */
  modifierRole(u: Utilisateur): void {
    this.dialog
        .open(ModifierRoleDialogComponent, {
          width: '480px',
          data: { utilisateur: u },
          autoFocus: 'first-tabbable'
        })
        .afterClosed()
        .subscribe(modifie => {
          if (modifie) this.charger();
        });
  }

  /**
   * Ouvre un dialog pour réinitialiser le mot de passe d'un utilisateur.
   * Appelle PATCH /api/utilisateurs/{id}/mot-de-passe?nouveau=xxx
   */
  reinitialiserMdp(u: Utilisateur): void {
    this.dialog.open(ReinitialiserMdpDialogComponent, {
      width: '480px',
      data: { utilisateur: u },
      autoFocus: 'first-tabbable'
    });
  }

  roleBadge(role: Role): string {
    switch (role) {
      case 'ADMIN':         return 'badge-info';
      case 'SUPERVISEUR':   return 'badge-warning';
      case 'AGENT_RECETTE': return 'badge-rts';
      default:              return 'badge-neutral';
    }
  }

  /** Libellé fr pour l'affichage du rôle dans le tableau. */
  roleLibelle(role: Role): string {
    switch (role) {
      case 'ADMIN':         return 'Administrateur';
      case 'SUPERVISEUR':   return 'Superviseur';
      case 'CAISSIER':      return 'Caissier';
      case 'AGENT_RECETTE': return 'Agent de recette';
      default:              return role;
    }
  }
}
