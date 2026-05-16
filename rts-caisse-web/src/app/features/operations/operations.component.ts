import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Caisse, OperationCaisse } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CaisseService } from '../../core/services/admin.services';
import { OperationService } from '../../core/services/caisse.services';
import { ModifierOperationDialogComponent } from './modifier-operation-dialog.component';

@Component({
  selector: 'rts-operations',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSelectModule,
    MatTooltipModule,
    MatDialogModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './operations.component.html',
  styleUrls: ['./operations.component.css']
})
export class OperationsComponent implements OnInit {
  private readonly caisseService = inject(CaisseService);
  private readonly operationService = inject(OperationService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  readonly caisses = signal<Caisse[]>([]);
  readonly operations = signal<OperationCaisse[]>([]);
  readonly total = signal(0);

  /** Caisse actuellement affichée (pour vérifier l'agent rattaché). */
  readonly caisseCourante = computed<Caisse | undefined>(() =>
    this.caisses().find(c => c.id === this.caisseSelectionneeId));

  /**
   * L'utilisateur courant peut-il modifier/réactiver les opérations
   * de la caisse actuellement affichée ?
   * - ADMIN, SUPERVISEUR : oui sur toutes
   * - AGENT_RECETTE : oui uniquement si affecté à cette caisse
   * - CAISSIER : non
   */
  readonly peutCorriger = computed<boolean>(() => {
    const role = this.auth.currentRole();
    if (role === 'ADMIN' || role === 'SUPERVISEUR') return true;
    if (role === 'AGENT_RECETTE') {
      const caisse = this.caisseCourante();
      const userId = this.auth.currentUser()?.utilisateurId;
      return !!caisse && caisse.agentRecetteId === userId;
    }
    return false;
  });

  readonly colonnes = [
    'date',
    'numero',
    'type',
    'categorie',
    'motif',
    'mode',
    'caissier',
    'montant',
    'timbre',
    'montantTtc',
    'actions'
  ];

  caisseSelectionneeId: number | null = null;
  pageIndex = 0;
  pageSize = 20;

  /** Plage de dates (incluses) pour le filtrage. Par defaut : 30 derniers jours. */
  dateDebut: Date = (() => { const d = new Date(); d.setDate(d.getDate() - 30); return d; })();
  dateFin:   Date = new Date();

  ngOnInit(): void {
    this.caisseService.lister().subscribe((list) => {
      // AGENT_RECETTE ne voit QUE la (les) caisse(s) qui lui sont affectees.
      const role = this.auth.currentRole();
      if (role === 'AGENT_RECETTE') {
        const userId = this.auth.currentUser()?.utilisateurId;
        list = list.filter(c => c.agentRecetteId === userId);
      }
      this.caisses.set(list);
      // Si une seule caisse, on la pre-selectionne et on charge directement.
      if (list.length === 1) {
        this.caisseSelectionneeId = list[0].id;
        this.charger();
      }
    });
  }

  charger(): void {
    if (!this.caisseSelectionneeId) return;
    let debut = this.dateDebut, fin = this.dateFin;
    if (debut && fin && fin < debut) [debut, fin] = [fin, debut];
    this.operationService
      .historiqueParCaisse(this.caisseSelectionneeId, this.pageIndex, this.pageSize,
                            this.toIso(debut), this.toIso(fin))
      .subscribe((page) => {
        this.operations.set(page.content);
        this.total.set(page.totalElements);
      });
  }

  /** Relance le chargement en revenant a la premiere page (clic "Appliquer"). */
  appliquerFiltre(): void {
    this.pageIndex = 0;
    this.charger();
  }

  changerPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.charger();
  }

  private toIso(d: Date | null | undefined): string | undefined {
    if (!d) return undefined;
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const j = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${j}`;
  }

  annuler(operation: OperationCaisse): void {
    const motif = prompt("Motif de l'annulation :");
    if (!motif) return;
    this.operationService.annuler(operation.id, motif).subscribe(() => {
      this.snackBar.open('Opération annulée (contre-passation effectuée)', 'OK', {
        duration: 3000,
        panelClass: ['snackbar-info']
      });
      this.charger();
    });
  }

  /** Ouvre le dialog de correction (montant, timbre, motif, référence). */
  modifier(operation: OperationCaisse): void {
    const ref = this.dialog.open(ModifierOperationDialogComponent, {
      data: { operation },
      autoFocus: 'first-tabbable',
      restoreFocus: true
    });
    ref.afterClosed().subscribe((result) => {
      if (result) this.charger();
    });
  }

  /** Réactive une opération annulée par erreur (annule la contre-passation). */
  reactiver(operation: OperationCaisse): void {
    if (!confirm(`Réactiver l'opération ${operation.numeroRecu} ?\n\n`
        + `Le montant TTC sera ré-appliqué au solde de la caisse comme si `
        + `l'annulation n'avait jamais eu lieu.`)) return;

    this.operationService.reactiver(operation.id).subscribe({
      next: () => {
        this.snackBar.open('Opération réactivée. Solde caisse recalculé.', 'OK', {
          duration: 3000,
          panelClass: ['snackbar-success']
        });
        this.charger();
      },
      error: (err) => {
        const message = err?.error?.message ?? 'Échec de la réactivation.';
        this.snackBar.open(message, 'OK', {
          duration: 5000,
          panelClass: ['snackbar-error']
        });
      }
    });
  }
}