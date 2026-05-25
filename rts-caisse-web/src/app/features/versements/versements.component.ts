import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
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
import { Caisse, Versement } from '../../core/models/models';
import { CaisseService } from '../../core/services/admin.services';
import { VersementService } from '../../core/services/caisse.services';
import { VersementDialogComponent } from './versement-dialog.component';

/**
 * Page admin de gestion des versements bancaires.
 *
 * <p>Vue paginée listant les versements, filtrable par caisse et plage de
 * dates. Permet de créer un nouveau versement (avec upload du bordereau)
 * et de télécharger un bordereau existant.</p>
 */
@Component({
  selector: 'rts-versements',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, FormsModule, DatePipe,
    MatTableModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatPaginatorModule, MatProgressSpinnerModule, MatDialogModule,
    MatTooltipModule
  ],
  template: `
    <div class="page-header">
      <div>
        <h1><mat-icon class="title-icon">account_balance_wallet</mat-icon> Versements bancaires</h1>
        <p class="subtitle">Dépôts d'espèces et virements internes effectués depuis les caisses RTS.</p>
      </div>
      <button mat-flat-button color="primary" (click)="ouvrirDialog()">
        <mat-icon>add</mat-icon> Nouveau versement
      </button>
    </div>

    <div class="filtres">
      <mat-form-field appearance="outline">
        <mat-label>Caisse</mat-label>
        <mat-select [(ngModel)]="caisseFiltre" (selectionChange)="recharger()">
          <mat-option [value]="null">Toutes les caisses</mat-option>
          @for (c of caisses(); track c.id) {
            <mat-option [value]="c.id">{{ c.code }} — {{ c.libelle }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Du</mat-label>
        <input matInput type="date" [(ngModel)]="dateDebut" (change)="recharger()" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Au</mat-label>
        <input matInput type="date" [(ngModel)]="dateFin" (change)="recharger()" />
      </mat-form-field>
      <button mat-stroked-button (click)="reset()">
        <mat-icon>refresh</mat-icon> Réinitialiser
      </button>
    </div>

    @if (loading()) {
      <div class="loader"><mat-spinner diameter="40"></mat-spinner></div>
    } @else {
      <div class="recap">
        <span class="recap-label">Total affiché :</span>
        <span class="recap-value">{{ formatMontant(totalAffiche()) }}</span>
        <span class="recap-count">({{ versements().length }} versement(s) sur cette page)</span>
      </div>

      <table mat-table [dataSource]="versements()" class="table-versements">
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let v">{{ v.dateVersement | date:'dd/MM/yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="caisse">
          <th mat-header-cell *matHeaderCellDef>Caisse</th>
          <td mat-cell *matCellDef="let v">{{ v.caisseLibelle }}</td>
        </ng-container>
        <ng-container matColumnDef="banque">
          <th mat-header-cell *matHeaderCellDef>Banque</th>
          <td mat-cell *matCellDef="let v">{{ v.banqueCode }} — {{ v.banqueLibelle }}</td>
        </ng-container>
        <ng-container matColumnDef="bordereau">
          <th mat-header-cell *matHeaderCellDef>N° bordereau</th>
          <td mat-cell *matCellDef="let v">{{ v.numeroBordereau }}</td>
        </ng-container>
        <ng-container matColumnDef="montant">
          <th mat-header-cell *matHeaderCellDef class="right">Montant</th>
          <td mat-cell *matCellDef="let v" class="right montant">{{ formatMontant(v.montant) }}</td>
        </ng-container>
        <ng-container matColumnDef="par">
          <th mat-header-cell *matHeaderCellDef>Saisi par</th>
          <td mat-cell *matCellDef="let v">{{ v.createdByNom }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef class="right">Bordereau</th>
          <td mat-cell *matCellDef="let v" class="right">
            <button mat-icon-button color="primary"
                    (click)="telecharger(v)"
                    matTooltip="Télécharger {{ v.nomFichier }}">
              <mat-icon>download</mat-icon>
            </button>
            <button mat-icon-button color="warn"
                    (click)="supprimer(v)"
                    matTooltip="Supprimer">
              <mat-icon>delete_outline</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="colonnes"></tr>
        <tr mat-row *matRowDef="let row; columns: colonnes"></tr>
      </table>

      @if (versements().length === 0) {
        <div class="empty">
          <mat-icon>inbox</mat-icon>
          <p>Aucun versement enregistré sur cette période.</p>
        </div>
      }

      <mat-paginator
        [length]="totalElements()"
        [pageSize]="pageSize"
        [pageIndex]="pageIndex"
        [pageSizeOptions]="[10, 20, 50, 100]"
        (page)="changerPage($event)"
        showFirstLastButtons>
      </mat-paginator>
    }
  `,
  styles: [`
    .page-header { display: flex; align-items: flex-start; justify-content: space-between;
                   gap: 20px; margin-bottom: 22px; }
    .page-header h1 { margin: 0; color: var(--rts-red); font-size: 24px; display: flex;
                      align-items: center; gap: 10px; }
    .page-header .title-icon { font-size: 26px; width: 26px; height: 26px; }
    .page-header .subtitle { margin: 4px 0 0; color: var(--rts-text-muted); font-size: 13px; }

    .filtres { display: flex; gap: 12px; align-items: center; flex-wrap: wrap;
               margin-bottom: 18px; }
    .filtres mat-form-field { min-width: 200px; }

    .loader { display: flex; justify-content: center; padding: 60px; }

    .recap { background: var(--rts-gray-50); border-radius: 8px; padding: 14px 18px;
             margin-bottom: 12px; display: flex; align-items: baseline; gap: 12px; }
    .recap-label { font-weight: 600; color: var(--rts-text-muted); font-size: 13px; }
    .recap-value { font-size: 20px; font-weight: 700; color: var(--rts-red); }
    .recap-count { font-size: 12px; color: var(--rts-text-muted); }

    .table-versements { width: 100%; background: white; }
    .right { text-align: right; }
    .montant { font-weight: 600; color: var(--rts-text); }

    .empty { text-align: center; padding: 60px 20px; color: var(--rts-text-muted); }
    .empty mat-icon { font-size: 56px; width: 56px; height: 56px; opacity: 0.4; }
    .empty p { margin: 12px 0 0; font-size: 14px; }
  `]
})
export class VersementsComponent implements OnInit {
  private readonly service = inject(VersementService);
  private readonly caisseService = inject(CaisseService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly versements = signal<Versement[]>([]);
  readonly caisses = signal<Caisse[]>([]);
  readonly loading = signal(false);
  readonly totalElements = signal(0);
  readonly totalAffiche = computed(() =>
    this.versements().reduce((acc, v) => acc + (v.montant || 0), 0));

  readonly colonnes = ['date', 'caisse', 'banque', 'bordereau', 'montant', 'par', 'actions'];

  caisseFiltre: number | null = null;
  dateDebut = '';
  dateFin = '';
  pageIndex = 0;
  pageSize = 20;

  ngOnInit(): void {
    this.caisseService.lister().subscribe((cs) => this.caisses.set(cs));
    this.recharger();
  }

  recharger(): void {
    this.loading.set(true);
    const obs$ = this.caisseFiltre
      ? this.service.listerParCaisse(this.caisseFiltre, {
          dateDebut: this.dateDebut || undefined,
          dateFin:   this.dateFin   || undefined,
          page: this.pageIndex,
          size: this.pageSize
        })
      : this.service.listerTous(this.pageIndex, this.pageSize);
    obs$.subscribe({
      next: (p) => {
        this.versements.set(p.content);
        this.totalElements.set(p.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Impossible de charger les versements.', 'OK',
          { duration: 4000, panelClass: 'snackbar-error' });
      }
    });
  }

  reset(): void {
    this.caisseFiltre = null;
    this.dateDebut = '';
    this.dateFin = '';
    this.pageIndex = 0;
    this.recharger();
  }

  changerPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.recharger();
  }

  ouvrirDialog(): void {
    this.dialog.open(VersementDialogComponent, {
      width: '600px',
      data: { caisses: this.caisses() }
    }).afterClosed().subscribe((cree) => {
      if (cree) this.recharger();
    });
  }

  telecharger(v: Versement): void {
    this.service.telechargerFichier(v.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = v.nomFichier;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1500);
      },
      error: () => this.snack.open('Téléchargement impossible.', 'OK',
        { duration: 3000, panelClass: 'snackbar-error' })
    });
  }

  supprimer(v: Versement): void {
    if (!confirm(`Supprimer le versement de ${this.formatMontant(v.montant)} `
        + `(bord. ${v.numeroBordereau}) ? Cette action est irreversible.`)) {
      return;
    }
    this.service.supprimer(v.id).subscribe({
      next: () => {
        this.snack.open('Versement supprime.', 'OK',
          { duration: 2500, panelClass: 'snackbar-success' });
        this.recharger();
      },
      error: (e) => {
        const msg = e?.error?.message ?? 'Suppression refusee.';
        this.snack.open(msg, 'OK',
          { duration: 4000, panelClass: 'snackbar-error' });
      }
    });
  }

  formatMontant(v: number): string {
    return new Intl.NumberFormat('fr-FR',
      { style: 'currency', currency: 'XOF', maximumFractionDigits: 0 }).format(v || 0);
  }
}
