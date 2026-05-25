// =====================================================================
//  Composant de consultation du journal d'audit RTS Caisse (admin)
//
//  À placer dans :
//    rts-caisse-web/src/app/pages/audit/audit-list.component.ts
//    rts-caisse-web/src/app/pages/audit/audit-list.component.html
//    rts-caisse-web/src/app/pages/audit/audit-list.component.css
//
//  Route à ajouter dans app.routes.ts :
//
//    {
//      path: 'admin/audit',
//      loadComponent: () =>
//        import('./pages/audit/audit-list.component')
//          .then(m => m.AuditListComponent),
//      canActivate: [adminGuard]
//    }
// =====================================================================

import { CommonModule, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  AUDIT_ACTIONS_LIST,
  AuditAction,
  AuditFiltres,
  AuditLog,
  AuditService
} from '../../core/services/audit.service';
import { AuditDetailDialogComponent } from './audit-detail-dialog.component';

type FiltreSucces = 'all' | 'success' | 'failure';

@Component({
  selector: 'rts-audit-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter()],
  imports: [
    CommonModule,
    FormsModule,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    MatDatepickerModule,
    MatDialogModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './audit-list.component.html',
  styleUrls: ['./audit-list.component.css']
})
export class AuditListComponent implements OnInit {

  private readonly auditService = inject(AuditService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  // ----- État affichage -----
  readonly logs = signal<AuditLog[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);

  readonly colonnes = [
    'date', 'action', 'utilisateur', 'entite', 'ip', 'statut', 'actions'
  ];

  readonly actionsList = AUDIT_ACTIONS_LIST;

  // ----- Filtres (modèle 2-way) -----
  filtreAction: AuditAction | '' = '';
  filtreUserId: number | null = null;
  filtreEntityType: string = '';
  filtreEntityId: number | null = null;
  filtreSucces: FiltreSucces = 'all';
  filtreDateFrom: Date | null = null;
  filtreDateTo: Date | null = null;

  // ----- Pagination -----
  pageIndex = 0;
  pageSize = 25;
  readonly pageSizes = [10, 25, 50, 100, 200];

  ngOnInit(): void {
    this.charger();
  }

  // ------------------------------------------------------------------
  //  Chargement
  // ------------------------------------------------------------------

  charger(): void {
    this.loading.set(true);

    const filtres: AuditFiltres = {
      action: this.filtreAction || undefined,
      userId: this.filtreUserId ?? undefined,
      entityType: this.filtreEntityType?.trim() || undefined,
      entityId: this.filtreEntityId ?? undefined,
      success: this.filtreSucces === 'all'
        ? undefined
        : this.filtreSucces === 'success',
      dateFrom: this.filtreDateFrom ?? undefined,
      dateTo: this.filtreDateTo ?? undefined
    };

    this.auditService
      .rechercher(filtres, { page: this.pageIndex, size: this.pageSize })
      .subscribe({
        next: (page) => {
          this.logs.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.snackBar.open(
            'Erreur lors du chargement : ' + (err?.error?.message ?? err.message),
            'OK',
            { duration: 4000, panelClass: ['snackbar-error'] });
        }
      });
  }

  appliquerFiltres(): void {
    this.pageIndex = 0;
    this.charger();
  }

  reinitialiserFiltres(): void {
    this.filtreAction = '';
    this.filtreUserId = null;
    this.filtreEntityType = '';
    this.filtreEntityId = null;
    this.filtreSucces = 'all';
    this.filtreDateFrom = null;
    this.filtreDateTo = null;
    this.pageIndex = 0;
    this.charger();
  }

  rafraichir(): void {
    this.charger();
  }

  changerPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.charger();
  }

  /**
   * Construit l'objet AuditFiltres a partir de l'etat actuel des filtres
   * du formulaire. Reutilise pour la consultation paginee, l'export CSV
   * et la purge (qui n'utilise pas les filtres, juste le nombre de jours).
   */
  private filtresCourants(): AuditFiltres {
    return {
      action: this.filtreAction || undefined,
      userId: this.filtreUserId ?? undefined,
      entityType: this.filtreEntityType?.trim() || undefined,
      entityId: this.filtreEntityId ?? undefined,
      success: this.filtreSucces === 'all'
        ? undefined
        : this.filtreSucces === 'success',
      dateFrom: this.filtreDateFrom ?? undefined,
      dateTo: this.filtreDateTo ?? undefined
    };
  }

  /**
   * Telecharge un export CSV du journal d'audit, en respectant les
   * filtres actuellement actifs. Le CSV est encode UTF-8 + BOM pour
   * Excel et utilise le point-virgule comme separateur.
   */
  exporterCsv(): void {
    this.auditService.exporterCsv(this.filtresCourants()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const ymd = new Date().toISOString().slice(0, 10);
        a.download = `journal-audit-${ymd}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1500);
        this.snackBar.open('Export CSV telecharge.', 'OK', {
          duration: 2500, panelClass: ['snackbar-success']
        });
      },
      error: (err) => this.snackBar.open(
        'Echec export : ' + (err?.error?.message ?? err.message),
        'OK', { duration: 4000, panelClass: ['snackbar-error'] }
      )
    });
  }

  /**
   * Demande confirmation puis purge les logs plus anciens que N jours.
   * N est saisi par l'admin (defaut 90, minimum cote serveur 7).
   */
  purgerLogs(): void {
    const raw = window.prompt(
      'Nombre de jours d\'historique a CONSERVER ? '
      + '(tout ce qui est plus ancien sera supprime definitivement)\n\n'
      + 'Par defaut : 90 jours. Minimum autorise : 7 jours.',
      '90'
    );
    if (raw === null) return; // annule
    const jours = parseInt(raw, 10);
    if (isNaN(jours) || jours < 1) {
      this.snackBar.open('Saisie invalide (nombre entier > 0 attendu).', 'OK',
        { duration: 3500, panelClass: ['snackbar-error'] });
      return;
    }
    if (!confirm(
        `Confirmer la SUPPRESSION DEFINITIVE de tous les logs d'audit `
        + `plus anciens que ${jours} jours ?\n\nCette action est irreversible.`)) {
      return;
    }
    this.auditService.purger(jours).subscribe({
      next: (res) => {
        this.snackBar.open(
          `Purge effectuee : ${res.supprimees} entrees supprimees `
          + `(seuil : ${res.joursConservation} jours).`,
          'OK', { duration: 5000, panelClass: ['snackbar-success'] });
        this.charger();
      },
      error: (err) => this.snackBar.open(
        'Echec purge : ' + (err?.error?.message ?? err.message),
        'OK', { duration: 4500, panelClass: ['snackbar-error'] }
      )
    });
  }

  // ------------------------------------------------------------------
  //  Détail
  // ------------------------------------------------------------------

  voirDetail(log: AuditLog): void {
    this.dialog.open(AuditDetailDialogComponent, {
      data: log,
      width: '720px',
      maxWidth: '95vw',
      autoFocus: false
    });
  }

  // ------------------------------------------------------------------
  //  Helpers d'affichage
  // ------------------------------------------------------------------

  /** Couleur de chip selon le type d'action (succès/échec/sécurité). */
  classeAction(log: AuditLog): string {
    if (!log.success) return 'chip-echec';
    switch (log.action) {
      case 'LOGIN_SUCCESS':
      case 'LOGOUT':
        return 'chip-auth';
      case 'OUVRIR_CAISSE':
      case 'CLOTURER_CAISSE':
      case 'VALIDER_JOURNAL':
        return 'chip-caisse';
      case 'CREER_OPERATION':
      case 'ANNULER_OPERATION':
        return 'chip-operation';
      case 'EXPORTER_JOURNAL_EXCEL':
      case 'IMPRIMER_RECU':
      case 'TELECHARGER_RECU_PDF':
      case 'ENVOYER_WHATSAPP':
        return 'chip-export';
      case 'CREER_UTILISATEUR':
      case 'MODIFIER_UTILISATEUR':
      case 'DESACTIVER_UTILISATEUR':
      case 'REACTIVER_UTILISATEUR':
      case 'REINITIALISER_MOT_DE_PASSE':
      case 'CHANGER_MOT_DE_PASSE':
        return 'chip-admin';
      default:
        return 'chip-default';
    }
  }

  /** Icône Material selon l'action. */
  iconeAction(log: AuditLog): string {
    if (!log.success) return 'error_outline';
    switch (log.action) {
      case 'LOGIN_SUCCESS':         return 'login';
      case 'LOGOUT':                return 'logout';
      case 'OUVRIR_CAISSE':         return 'lock_open';
      case 'CLOTURER_CAISSE':       return 'lock';
      case 'VALIDER_JOURNAL':       return 'verified';
      case 'CREER_OPERATION':       return 'add_circle';
      case 'ANNULER_OPERATION':     return 'cancel';
      case 'EXPORTER_JOURNAL_EXCEL': return 'file_download';
      case 'ENVOYER_WHATSAPP':      return 'chat';
      case 'IMPRIMER_RECU':         return 'print';
      case 'TELECHARGER_RECU_PDF':  return 'picture_as_pdf';
      case 'CREER_UTILISATEUR':     return 'person_add';
      case 'DESACTIVER_UTILISATEUR': return 'person_off';
      case 'REACTIVER_UTILISATEUR': return 'person';
      case 'REINITIALISER_MOT_DE_PASSE':
      case 'CHANGER_MOT_DE_PASSE':  return 'password';
      default:                       return 'info';
    }
  }
}
