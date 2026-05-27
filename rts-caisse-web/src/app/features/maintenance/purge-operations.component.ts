import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CaisseService } from '../../core/services/admin.services';
import {
  OperationService, PurgeFilter, PurgePreviewResponse,
  PurgeResult, PurgeStatut
} from '../../core/services/caisse.services';
import { Caisse } from '../../core/models/models';

/**
 * Écran de purge définitive des opérations de caisse — réservé aux ADMIN.
 *
 * Flux UX :
 *  1. L'admin choisit les filtres (caisse, date butoir, statut).
 *  2. Bouton "Prévisualiser" → affiche compteur + sommes impactées.
 *  3. Bouton "Exporter CSV" → snapshot avant suppression (traçabilité).
 *  4. Bouton "Purger" → modal de confirmation à 2 étapes avec saisie du
 *     mot "SUPPRIMER" pour confirmer.
 */
@Component({
  selector: 'rts-purge-operations',
  standalone: true,
  providers: [provideNativeDateAdapter()],
  imports: [
    CommonModule, FormsModule, DatePipe, DecimalPipe,
    MatButtonModule, MatDatepickerModule, MatDialogModule, MatDividerModule,
    MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, MatSelectModule
  ],
  template: `
    <div class="page">
      <div class="header">
        <mat-icon class="header-icon">delete_sweep</mat-icon>
        <div>
          <h1>Purge des opérations de caisse</h1>
          <p class="subtitle">
            Suppression définitive d'opérations pour alléger la base.
            Le solde caisse est contre-passé automatiquement pour les opérations
            actives ; les opérations dans un journal clôturé sont supprimées sans
            modifier le snapshot historique.
          </p>
        </div>
      </div>

      <div class="warning-banner">
        <mat-icon>warning</mat-icon>
        <div>
          <strong>Action irréversible.</strong> Chaque suppression est tracée dans
          le journal d'audit. Pensez à exporter le CSV avant de purger.
        </div>
      </div>

      <!-- Filtres -->
      <section class="card">
        <h2>Filtres</h2>
        <div class="filters">
          <mat-form-field appearance="outline">
            <mat-label>Caisse</mat-label>
            <mat-select [(ngModel)]="filter.caisseId">
              <mat-option [value]="null">Toutes les caisses</mat-option>
              @for (c of caisses(); track c.id) {
                <mat-option [value]="c.id">{{ c.code }} — {{ c.libelle }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Supprimer les opérations antérieures à</mat-label>
            <input matInput [matDatepicker]="picker" [(ngModel)]="avantDateLocal"
                   (dateChange)="onAvantDateChange()" required>
            <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
            <mat-datepicker #picker></mat-datepicker>
            <mat-hint>Strictement antérieures (le jour J n'est pas inclus)</mat-hint>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Statut</mat-label>
            <mat-select [(ngModel)]="filter.statut">
              <mat-option value="SEULEMENT_ANNULEES">
                Seulement les opérations annulées (recommandé)
              </mat-option>
              <mat-option value="SEULEMENT_ACTIVES">
                Seulement les opérations actives
              </mat-option>
              <mat-option value="TOUS">
                Toutes (annulées + actives)
              </mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <div class="actions">
          <button mat-stroked-button color="primary"
                  [disabled]="!filter.avantDate || loading()"
                  (click)="previewPurge()">
            <mat-icon>visibility</mat-icon> Prévisualiser
          </button>

          <button mat-stroked-button
                  [disabled]="!preview() || preview()!.total === 0 || loading()"
                  (click)="exporterCsv()">
            <mat-icon>download</mat-icon> Exporter CSV
          </button>

          <button mat-flat-button color="warn"
                  [disabled]="!preview() || preview()!.total === 0 || loading()"
                  (click)="confirmerEtPurger()">
            <mat-icon>delete_forever</mat-icon> Purger définitivement
          </button>
        </div>
      </section>

      <!-- Prévisualisation -->
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
      }

      @if (preview(); as p) {
        <section class="card preview">
          <h2>Impact de la purge</h2>
          @if (p.total === 0) {
            <p class="empty">Aucune opération ne correspond à ces filtres.</p>
          } @else {
            <div class="stats">
              <div class="stat big">
                <div class="value">{{ p.total }}</div>
                <div class="label">Opération(s) à supprimer</div>
              </div>
              <div class="stat">
                <div class="value">{{ p.nbAnnulees }}</div>
                <div class="label">Déjà annulées</div>
              </div>
              <div class="stat">
                <div class="value">{{ p.nbActives }}</div>
                <div class="label">Actives (contre-pass auto)</div>
              </div>
              <div class="stat">
                <div class="value">{{ p.nbDansCloture }}</div>
                <div class="label">Dans journal clôturé</div>
              </div>
              <div class="stat">
                <div class="value text-green">
                  {{ p.sommeEntrees | number:'1.0-0' }} F
                </div>
                <div class="label">∑ Entrées actives</div>
              </div>
              <div class="stat">
                <div class="value text-red">
                  {{ p.sommeSorties | number:'1.0-0' }} F
                </div>
                <div class="label">∑ Sorties actives</div>
              </div>
            </div>
            <p class="period">
              Période : du <strong>{{ p.plusAncienne | date:'dd/MM/yyyy HH:mm' }}</strong>
              au <strong>{{ p.plusRecente | date:'dd/MM/yyyy HH:mm' }}</strong>
            </p>
          }
        </section>
      }

      <!-- Dernier résultat -->
      @if (lastResult(); as r) {
        <section class="card result">
          <h2>Dernière purge exécutée</h2>
          <div class="result-row">
            <mat-icon class="text-green">check_circle</mat-icon>
            {{ r.nbSupprimees }} opération(s) supprimée(s)
          </div>
          <div class="result-row">
            <mat-icon>swap_horiz</mat-icon>
            {{ r.nbContrepassees }} solde(s) caisse contre-passé(s)
          </div>
          @if (r.nbEchecs > 0) {
            <div class="result-row text-red">
              <mat-icon>error</mat-icon>
              {{ r.nbEchecs }} échec(s)
            </div>
            @for (e of r.erreurs; track e) {
              <div class="error-detail">{{ e }}</div>
            }
          }
        </section>
      }
    </div>
  `,
  styles: [`
    .page { padding: 24px; max-width: 1200px; }
    .header { display: flex; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
    .header-icon { color: #b00020; font-size: 40px; width: 40px; height: 40px; }
    h1 { margin: 0; font-size: 24px; }
    .subtitle { color: #555; margin: 4px 0 0; max-width: 800px; }
    .warning-banner {
      background: #fff8e1; border-left: 4px solid #f57c00;
      padding: 12px 16px; display: flex; gap: 12px; align-items: center;
      border-radius: 4px; margin-bottom: 16px;
    }
    .warning-banner mat-icon { color: #f57c00; }
    .card {
      background: #fff; border-radius: 8px; padding: 16px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.08); margin-bottom: 16px;
    }
    .card h2 { margin: 0 0 12px; font-size: 16px; font-weight: 600; }
    .filters {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 12px;
    }
    .actions {
      display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap;
    }
    .actions button mat-icon { margin-right: 4px; }
    .loading { display: flex; justify-content: center; padding: 24px; }
    .empty { color: #888; font-style: italic; }
    .stats {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 12px;
    }
    .stat {
      background: #f8f9fa; padding: 12px; border-radius: 6px;
      border-left: 3px solid #ccc;
    }
    .stat.big { border-left-color: #b00020; background: #fff5f5; }
    .stat .value { font-size: 20px; font-weight: 700; }
    .stat.big .value { font-size: 32px; color: #b00020; }
    .stat .label { font-size: 12px; color: #666; margin-top: 4px; }
    .text-green { color: #2e7d32; }
    .text-red { color: #c62828; }
    .period { margin-top: 12px; color: #555; font-size: 14px; }
    .result-row {
      display: flex; gap: 8px; align-items: center; margin: 4px 0;
    }
    .error-detail {
      padding: 4px 8px; margin: 2px 0 2px 32px; background: #ffebee;
      border-radius: 4px; font-family: monospace; font-size: 12px;
    }
  `]
})
export class PurgeOperationsComponent implements OnInit {

  private readonly opService = inject(OperationService);
  private readonly caisseService = inject(CaisseService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly caisses = signal<Caisse[]>([]);
  readonly loading = signal(false);
  readonly preview = signal<PurgePreviewResponse | null>(null);
  readonly lastResult = signal<PurgeResult | null>(null);

  filter: PurgeFilter = {
    caisseId: null,
    avantDate: '',
    statut: 'SEULEMENT_ANNULEES' as PurgeStatut
  };
  avantDateLocal: Date | null = null;

  ngOnInit(): void {
    this.caisseService.lister().subscribe(cs => this.caisses.set(cs));
  }

  onAvantDateChange(): void {
    if (this.avantDateLocal) {
      // ISO date sans tz : on prend l'année/mois/jour locaux
      const d = this.avantDateLocal;
      const pad = (n: number) => String(n).padStart(2, '0');
      this.filter.avantDate = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    } else {
      this.filter.avantDate = '';
    }
    // Invalide la dernière prévisualisation si on change le filtre
    this.preview.set(null);
  }

  previewPurge(): void {
    if (!this.filter.avantDate) return;
    this.loading.set(true);
    this.opService.previewPurge(this.filter).subscribe({
      next: p => { this.preview.set(p); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.snack.open(
          'Erreur prévisualisation : ' + (e?.error?.message || e?.message || 'inconnue'),
          'OK', { duration: 5000, panelClass: 'snackbar-error' });
      }
    });
  }

  exporterCsv(): void {
    if (!this.filter.avantDate) return;
    this.loading.set(true);
    this.opService.exporterPurgeCsv(this.filter).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
        a.href = url;
        a.download = `operations-a-purger-${stamp}.csv`;
        a.click();
        URL.revokeObjectURL(url);
        this.loading.set(false);
        this.snack.open('CSV téléchargé.', 'OK',
          { duration: 3000, panelClass: 'snackbar-success' });
      },
      error: e => {
        this.loading.set(false);
        this.snack.open(
          'Erreur export CSV : ' + (e?.message || 'inconnue'),
          'OK', { duration: 5000, panelClass: 'snackbar-error' });
      }
    });
  }

  confirmerEtPurger(): void {
    const p = this.preview();
    if (!p || p.total === 0) return;
    const ref = this.dialog.open(ConfirmPurgeDialog, {
      width: '460px',
      data: { preview: p, filter: this.filter },
      disableClose: true
    });
    ref.afterClosed().subscribe(ok => {
      if (ok) this.executerPurge();
    });
  }

  private executerPurge(): void {
    this.loading.set(true);
    this.opService.purgerEnMasse(this.filter).subscribe({
      next: r => {
        this.lastResult.set(r);
        this.preview.set(null);
        this.loading.set(false);
        this.snack.open(
          `Purge terminée : ${r.nbSupprimees} supprimée(s), ${r.nbEchecs} échec(s).`,
          'OK', { duration: 6000,
            panelClass: r.nbEchecs > 0 ? 'snackbar-warning' : 'snackbar-success' });
      },
      error: e => {
        this.loading.set(false);
        this.snack.open(
          'Échec purge : ' + (e?.error?.message || e?.message || 'inconnue'),
          'OK', { duration: 6000, panelClass: 'snackbar-error' });
      }
    });
  }
}

// ============================================================================
//  Dialog de confirmation — saisie obligatoire du mot "SUPPRIMER"
// ============================================================================
@Component({
  selector: 'rts-confirm-purge-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatButtonModule, MatDialogModule, MatFormFieldModule,
    MatIconModule, MatInputModule
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="warn-icon">warning</mat-icon>
      Confirmer la suppression définitive
    </h2>
    <mat-dialog-content>
      <p>
        Vous êtes sur le point de supprimer définitivement
        <strong>{{ data.preview.total }}</strong> opération(s) de caisse.
      </p>
      <p>
        Cette action est <strong>irréversible</strong>.
        Le solde des caisses concernées sera ajusté automatiquement pour
        les opérations actives. L'historique d'audit conservera la trace
        de cette purge.
      </p>
      <p>
        Pour confirmer, tapez le mot <strong>SUPPRIMER</strong> ci-dessous :
      </p>
      <mat-form-field appearance="outline" class="full">
        <input matInput [(ngModel)]="motSaisi" placeholder="SUPPRIMER"
               autocomplete="off">
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Annuler</button>
      <button mat-flat-button color="warn"
              [disabled]="motSaisi !== 'SUPPRIMER'"
              [mat-dialog-close]="true">
        Supprimer définitivement
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    h2 { display: flex; align-items: center; gap: 8px; }
    .warn-icon { color: #f57c00; }
    .full { width: 100%; margin-top: 8px; }
  `]
})
export class ConfirmPurgeDialog {
  protected readonly data = inject<{
    preview: PurgePreviewResponse;
    filter: PurgeFilter;
  }>(MAT_DIALOG_DATA);
  motSaisi = '';
}
