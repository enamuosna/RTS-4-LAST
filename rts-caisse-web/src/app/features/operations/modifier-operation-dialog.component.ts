import { CommonModule, CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { OperationCaisse, OperationCaisseRequest } from '../../core/models/models';
import { OperationService } from '../../core/services/caisse.services';

/**
 * Dialog de modification d'une opération de caisse — pour l'agent de
 * recette (ou ADMIN/SUPERVISEUR). Permet de corriger les champs
 * principaux : montant HT, timbre fiscal, motif, référence.
 *
 * <p>Le solde de la caisse est recalculé automatiquement côté backend
 * via {@code PUT /api/operations/{id}}. Les champs catégorie, mode de
 * paiement, banque, client ne sont pas modifiables ici (pour ces cas
 * il faut annuler + resaisir au guichet).</p>
 */
@Component({
  selector: 'rts-modifier-operation-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="header-icon">edit</mat-icon>
      Modifier l'opération {{ data.operation.numeroRecu }}
    </h2>

    <mat-dialog-content class="dialog-content">
      <div class="info-banner">
        <mat-icon>info</mat-icon>
        <span>
          Le solde de la caisse sera recalculé automatiquement.
          Pour changer la catégorie, le mode de paiement, le client ou la
          banque, annulez puis resaisissez au guichet.
        </span>
      </div>

      <div class="form-grid">
        <mat-form-field appearance="outline">
          <mat-label>Montant HT (FCFA)</mat-label>
          <input matInput type="number" min="1" [(ngModel)]="montant" (ngModelChange)="recalculer()" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Timbre fiscal (FCFA, calculé)</mat-label>
          <input matInput type="text" readonly
                 [value]="timbre"
                 style="font-weight: 600;" />
          <mat-hint>1% si montant &ge; 20 000 FCFA, sinon 0</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Montant TTC (calculé)</mat-label>
          <input matInput type="text" readonly
                 [value]="ttcAffiche()"
                 style="font-weight: 600;" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Motif (optionnel)</mat-label>
          <input matInput [(ngModel)]="motif" maxlength="500" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Référence (optionnel)</mat-label>
          <input matInput [(ngModel)]="reference" maxlength="100" />
        </mat-form-field>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-stroked-button (click)="annuler()" [disabled]="saving()">
        Annuler
      </button>
      <button mat-flat-button color="primary"
              (click)="enregistrer()"
              [disabled]="!estValide() || saving()">
        @if (saving()) {
          <mat-spinner diameter="18" class="inline-spinner"></mat-spinner>
          Enregistrement…
        } @else {
          <mat-icon>save</mat-icon>
          Enregistrer
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .header-icon {
      vertical-align: middle;
      margin-right: 6px;
      color: var(--rts-red);
    }
    .dialog-content { min-width: 480px; padding-top: 8px; }
    .info-banner {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      padding: 12px 14px;
      background: var(--rts-info-soft);
      border-left: 3px solid var(--rts-info);
      border-radius: 6px;
      font-size: 12px;
      margin-bottom: 16px;
      line-height: 1.5;
    }
    .info-banner mat-icon { color: var(--rts-info); flex-shrink: 0; }
    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px 14px;
    }
    .form-grid .full { grid-column: 1 / -1; }
    .inline-spinner { display: inline-block; margin-right: 6px; vertical-align: middle; }
  `]
})
export class ModifierOperationDialogComponent {
  private readonly api = inject(OperationService);
  private readonly snack = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<ModifierOperationDialogComponent, OperationCaisse>);

  readonly saving = signal(false);

  // Signaux pour le calcul réactif du TTC
  private readonly montantSig = signal<number>(0);
  private readonly timbreSig  = signal<number>(0);

  montant = 0;
  timbre = 0;
  motif = '';
  reference = '';

  readonly ttcAffiche = computed(() =>
    new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'XOF', maximumFractionDigits: 0 })
      .format(this.montantSig() + this.timbreSig()));

  constructor(@Inject(MAT_DIALOG_DATA) public data: { operation: OperationCaisse }) {
    const op = data.operation;
    this.montant = op.montant;
    this.timbre  = op.timbre || 0;
    this.motif   = op.motif || '';
    this.reference = op.reference || '';
    this.recalculer();
  }

  /**
   * Recalcule le timbre fiscal en local (meme regle que le backend) :
   *   - montant >= 20 000 FCFA -> timbre = 1% du montant, arrondi au FCFA
   *   - montant <  20 000 FCFA -> timbre = 0
   * Le backend recalcule TOUJOURS de toute facon, mais on affiche le bon
   * montant tout de suite a l'utilisateur.
   */
  recalculer(): void {
    const montantNum = Number(this.montant) || 0;
    this.timbre = montantNum >= 20000 ? Math.round(montantNum * 0.01) : 0;
    this.montantSig.set(montantNum);
    this.timbreSig.set(this.timbre);
  }

  estValide(): boolean {
    return this.montant > 0;
  }

  enregistrer(): void {
    if (!this.estValide()) return;
    const op = this.data.operation;
    const req: OperationCaisseRequest = {
      caisseId: op.caisseId,
      categorieId: op.categorieId,
      clientId: op.clientId,
      typeOperation: op.typeOperation,
      montant: Number(this.montant),
      timbre: Number(this.timbre) || 0,
      modePaiement: op.modePaiement,
      motif: this.motif,
      reference: this.reference || undefined,
      banqueId: op.banqueId
    };
    this.saving.set(true);
    this.api.modifier(op.id, req).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.snack.open('Opération modifiée. Solde caisse recalculé.', 'OK',
          { duration: 3000, panelClass: 'snackbar-success' });
        this.dialogRef.close(updated);
      },
      error: (err) => {
        this.saving.set(false);
        const message = err?.error?.message ?? 'Échec de la modification.';
        this.snack.open(message, 'OK',
          { duration: 5000, panelClass: 'snackbar-error' });
      }
    });
  }

  annuler(): void {
    this.dialogRef.close();
  }
}
