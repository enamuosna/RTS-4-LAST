import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Banque, Caisse, Versement } from '../../core/models/models';
import { BanqueService } from '../../core/services/admin.services';
import { VersementService } from '../../core/services/caisse.services';

/**
 * Dialog de création d'un versement bancaire. Saisie de la caisse, banque,
 * montant, n° bordereau + upload d'un PDF/JPG/PNG (max 5 Mo).
 */
@Component({
  selector: 'rts-versement-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, FormsModule,
    MatDialogModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatProgressSpinnerModule, MatTooltipModule
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="header-icon">account_balance_wallet</mat-icon>
      Nouveau versement bancaire
    </h2>

    <mat-dialog-content class="dialog-content">
      <div class="grille">
        <mat-form-field appearance="outline">
          <mat-label>Caisse *</mat-label>
          <mat-select [(ngModel)]="caisseId" required>
            @for (c of data.caisses; track c.id) {
              <mat-option [value]="c.id">{{ c.code }} — {{ c.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Banque destinataire *</mat-label>
          <mat-select [(ngModel)]="banqueId" required>
            @for (b of banques(); track b.id) {
              <mat-option [value]="b.id">{{ b.code }} — {{ b.libelle }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Montant versé (FCFA) *</mat-label>
          <input matInput type="number" min="1" [(ngModel)]="montant" required />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>N° bordereau *</mat-label>
          <input matInput [(ngModel)]="numeroBordereau" maxlength="100" required
                 placeholder="ex: BORD-2026-00457" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Date et heure du versement</mat-label>
          <input matInput type="datetime-local" [(ngModel)]="dateVersement" />
          <mat-hint>Optionnel — par défaut : maintenant</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full">
          <mat-label>Notes (optionnel)</mat-label>
          <input matInput [(ngModel)]="notes" maxlength="500"
                 placeholder="Commentaire libre" />
        </mat-form-field>

        <div class="upload-zone full" [class.has-file]="!!fichier">
          <input #fileInput type="file"
                 accept="application/pdf,image/jpeg,image/jpg,image/png"
                 (change)="onFileSelected($event)" hidden />
          @if (!fichier) {
            <button mat-stroked-button type="button" (click)="fileInput.click()">
              <mat-icon>upload_file</mat-icon> Choisir le bordereau (PDF, JPG, PNG)
            </button>
            <p class="hint">Taille max : 5 Mo</p>
          } @else {
            <div class="file-info">
              <mat-icon class="file-icon">{{ fichierIcone() }}</mat-icon>
              <div>
                <div class="file-name">{{ fichier.name }}</div>
                <div class="file-meta">{{ tailleLisible() }}</div>
              </div>
              <button mat-icon-button (click)="fichier = null" matTooltip="Retirer">
                <mat-icon>close</mat-icon>
              </button>
            </div>
          }
        </div>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-stroked-button (click)="annuler()" [disabled]="saving()">Annuler</button>
      <button mat-flat-button color="primary"
              (click)="enregistrer()"
              [disabled]="!estValide() || saving()">
        @if (saving()) {
          <mat-spinner diameter="18" class="inline-spinner"></mat-spinner>
          Upload en cours...
        } @else {
          <mat-icon>save</mat-icon> Enregistrer le versement
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .header-icon { vertical-align: middle; margin-right: 6px; color: var(--rts-red); }
    .dialog-content { min-width: 500px; padding-top: 8px; }
    .grille { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 14px; }
    .grille .full { grid-column: 1 / -1; }

    .upload-zone { border: 2px dashed var(--rts-gray-300); border-radius: 8px;
                    padding: 22px; text-align: center; background: var(--rts-gray-50); }
    .upload-zone.has-file { border-style: solid; background: white; padding: 14px; }
    .upload-zone .hint { font-size: 12px; color: var(--rts-text-muted);
                         margin: 8px 0 0; }
    .file-info { display: flex; align-items: center; gap: 14px; text-align: left; }
    .file-icon { font-size: 36px; width: 36px; height: 36px; color: var(--rts-red); }
    .file-name { font-weight: 600; word-break: break-all; }
    .file-meta { font-size: 12px; color: var(--rts-text-muted); }
    .file-info > :nth-child(2) { flex-grow: 1; }

    .inline-spinner { display: inline-block; margin-right: 6px; vertical-align: middle; }
  `]
})
export class VersementDialogComponent implements OnInit {
  private readonly service = inject(VersementService);
  private readonly banqueService = inject(BanqueService);
  private readonly snack = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<VersementDialogComponent, Versement>);

  readonly banques = signal<Banque[]>([]);
  readonly saving = signal(false);

  caisseId: number | null = null;
  banqueId: number | null = null;
  montant: number | null = null;
  numeroBordereau = '';
  dateVersement = '';
  notes = '';
  fichier: File | null = null;

  constructor(@Inject(MAT_DIALOG_DATA) public data: { caisses: Caisse[] }) {}

  ngOnInit(): void {
    this.banqueService.lister().subscribe((bs) => this.banques.set(bs.filter(b => b.actif)));
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return;
    // Validation cote client (le backend revalide).
    const types = ['application/pdf', 'image/jpeg', 'image/jpg', 'image/png'];
    if (!types.includes(file.type)) {
      this.snack.open('Format non supporte. PDF, JPG ou PNG uniquement.', 'OK',
        { duration: 4000, panelClass: 'snackbar-error' });
      input.value = '';
      return;
    }
    const max = 5 * 1024 * 1024;
    if (file.size > max) {
      this.snack.open('Fichier trop volumineux (max 5 Mo).', 'OK',
        { duration: 4000, panelClass: 'snackbar-error' });
      input.value = '';
      return;
    }
    this.fichier = file;
  }

  estValide(): boolean {
    return !!this.caisseId && !!this.banqueId
        && !!this.montant && this.montant > 0
        && this.numeroBordereau.trim().length > 0
        && !!this.fichier;
  }

  fichierIcone(): string {
    if (!this.fichier) return 'description';
    return this.fichier.type === 'application/pdf'
      ? 'picture_as_pdf' : 'image';
  }

  tailleLisible(): string {
    if (!this.fichier) return '';
    const ko = this.fichier.size / 1024;
    return ko > 1024
      ? (ko / 1024).toFixed(2) + ' Mo'
      : ko.toFixed(0) + ' Ko';
  }

  enregistrer(): void {
    if (!this.estValide()) return;
    this.saving.set(true);
    this.service.creer({
      caisseId: this.caisseId!,
      banqueId: this.banqueId!,
      montant:  Number(this.montant),
      numeroBordereau: this.numeroBordereau.trim(),
      dateVersement: this.dateVersement || undefined,
      notes: this.notes || undefined,
      fichier: this.fichier!
    }).subscribe({
      next: (v) => {
        this.saving.set(false);
        this.snack.open('Versement enregistre avec succes.', 'OK',
          { duration: 3000, panelClass: 'snackbar-success' });
        this.dialogRef.close(v);
      },
      error: (err) => {
        this.saving.set(false);
        const msg = err?.error?.message ?? 'Echec de l\'enregistrement.';
        this.snack.open(msg, 'OK',
          { duration: 5000, panelClass: 'snackbar-error' });
      }
    });
  }

  annuler(): void {
    this.dialogRef.close();
  }
}
