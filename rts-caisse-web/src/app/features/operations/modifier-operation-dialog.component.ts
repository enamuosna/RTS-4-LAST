import { CommonModule, CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
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
import { CategorieService } from '../../core/services/admin.services';
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
    MatCheckboxModule,
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
          Pour changer le produit, le mode de paiement, le client ou la
          banque, annulez puis resaisissez au guichet.
        </span>
      </div>

      <div class="form-grid">
        <mat-form-field appearance="outline">
          <mat-label>Montant HT (FCFA)</mat-label>
          <input matInput type="number" min="1" [(ngModel)]="montant" (ngModelChange)="recalculer()" />
        </mat-form-field>

        <div class="full">
          <mat-checkbox [(ngModel)]="timbreManuel" (ngModelChange)="recalculer()">
            Saisie manuelle du timbre
          </mat-checkbox>
        </div>

        @if (timbreManuel) {
          <mat-form-field appearance="outline">
            <mat-label>Timbre (FCFA)</mat-label>
            <input matInput type="number" min="0"
                   [(ngModel)]="timbre" (ngModelChange)="recalculer()" />
            <mat-hint>Vide ou 0 = aucun timbre</mat-hint>
          </mat-form-field>
        } @else if (estEspeces()) {
          <mat-form-field appearance="outline">
            <mat-label>Timbre (FCFA, calculé)</mat-label>
            <input matInput type="text" readonly
                   [value]="timbre"
                   style="font-weight: 600;" />
            <mat-hint>1% si montant &ge; 20 000 FCFA, sinon 0</mat-hint>
          </mat-form-field>
        }

        <mat-form-field appearance="outline" [class.full]="!estEspeces()"
                        [class]="estEspeces() ? '' : 'full'">
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

        <mat-form-field appearance="outline" class="full">
          <mat-label>Heure de diffusion *</mat-label>
          <input matInput type="datetime-local" [(ngModel)]="dateDiffusion" required />
          <mat-hint>Date et heure prevues de diffusion a l'antenne (obligatoire)</mat-hint>
          @if (!dateDiffusion) {
            <mat-error>La date et l'heure de diffusion sont obligatoires.</mat-error>
          }
        </mat-form-field>

        @if (accepteJustificatif()) {
          <div class="upload-zone full" [class.has-file]="!!fichier || justificatifExistant()">
            <input #fileInput type="file"
                   accept="application/pdf,image/jpeg,image/jpg,image/png"
                   (change)="onJustificatifSelected($event)" hidden />
            @if (fichier) {
              <div class="file-info">
                <mat-icon class="file-icon">{{ iconePourFichier(fichier.type) }}</mat-icon>
                <div>
                  <div class="file-name">{{ fichier.name }}</div>
                  <div class="file-meta">{{ tailleLisible(fichier.size) }} - nouveau</div>
                </div>
                <button mat-icon-button (click)="fichier = null"
                        matTooltip="Retirer le nouveau fichier">
                  <mat-icon>close</mat-icon>
                </button>
              </div>
            } @else if (justificatifExistant()) {
              <div class="file-info">
                <mat-icon class="file-icon">{{ iconePourFichier(data.operation.justificatifTypeMime) }}</mat-icon>
                <div>
                  <div class="file-name">{{ data.operation.justificatifNomFichier }}</div>
                  <div class="file-meta">{{ tailleLisible(data.operation.justificatifTailleFichier ?? 0) }} - deja attache</div>
                </div>
                <button mat-icon-button color="primary"
                        (click)="telechargerJustificatif()"
                        matTooltip="Telecharger le justificatif">
                  <mat-icon>download</mat-icon>
                </button>
                <button mat-stroked-button type="button" (click)="fileInput.click()">
                  <mat-icon>upload_file</mat-icon> Remplacer
                </button>
              </div>
            } @else {
              <button mat-stroked-button type="button" (click)="fileInput.click()">
                <mat-icon>upload_file</mat-icon> Joindre un justificatif de paiement (PDF/JPG/PNG)
              </button>
              <p class="hint">Optionnel - taille max : 5 Mo</p>
            }
          </div>
        }
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
    .upload-zone { border: 2px dashed var(--rts-gray-300); border-radius: 8px;
                   padding: 18px; text-align: center; background: var(--rts-gray-50); }
    .upload-zone.has-file { border-style: solid; background: white; padding: 12px; }
    .upload-zone .hint { font-size: 12px; color: var(--rts-text-muted);
                         margin: 6px 0 0; }
    .file-info { display: flex; align-items: center; gap: 14px; text-align: left;
                 flex-wrap: wrap; }
    .file-icon { font-size: 32px; width: 32px; height: 32px; color: var(--rts-red); }
    .file-name { font-weight: 600; word-break: break-all; }
    .file-meta { font-size: 12px; color: var(--rts-text-muted); }
    .file-info > :nth-child(2) { flex-grow: 1; min-width: 150px; }
  `]
})
export class ModifierOperationDialogComponent {
  private readonly api = inject(OperationService);
  private readonly categorieService = inject(CategorieService);
  private readonly snack = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<ModifierOperationDialogComponent, OperationCaisse>);

  readonly saving = signal(false);
  /** True si la catégorie de l'opération accepte un justificatif. */
  readonly accepteJustificatif = signal(false);

  // Signaux pour le calcul réactif du TTC
  private readonly montantSig = signal<number>(0);
  private readonly timbreSig  = signal<number>(0);

  montant = 0;
  timbre = 0;
  /** Saisie manuelle du timbre (sinon calcul automatique). */
  timbreManuel = false;
  motif = '';
  reference = '';
  /** Au format "YYYY-MM-DDTHH:mm" attendu par <input type="datetime-local">. */
  dateDiffusion = '';
  /** Nouveau fichier sélectionné par l'utilisateur (sera uploadé après update). */
  fichier: File | null = null;

  readonly ttcAffiche = computed(() =>
    new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'XOF', maximumFractionDigits: 0 })
      .format(this.montantSig() + this.timbreSig()));

  constructor(@Inject(MAT_DIALOG_DATA) public data: { operation: OperationCaisse }) {
    const op = data.operation;
    this.montant = op.montant;
    this.timbre  = op.timbre || 0;
    this.motif   = op.motif || '';
    this.reference = op.reference || '';
    // L'input datetime-local attend "YYYY-MM-DDTHH:mm". Le backend renvoie
    // une string ISO 8601 (avec secondes/millis et eventuellement timezone).
    // On tronque a la minute pour coller au format de l'input.
    this.dateDiffusion = op.dateDiffusion
      ? op.dateDiffusion.substring(0, 16)
      : '';
    this.recalculer();
    // La zone d'upload est visible si :
    //  - la categorie de l'operation a accepteJustificatif=true, OU
    //  - le mode de paiement n'est pas ESPECES (justificatif de paiement
    //    electronique : cheque, virement, wave, orange money, etc.).
    const modeNonEspeces = op.modePaiement && op.modePaiement !== 'ESPECES';
    if (modeNonEspeces) {
      this.accepteJustificatif.set(true);
    } else {
      // Pour ESPECES, on regarde le flag categorie.
      this.categorieService.lister().subscribe((cats) => {
        const cat = cats.find(c => c.id === op.categorieId);
        this.accepteJustificatif.set(cat?.accepteJustificatif === true);
      });
    }
  }

  /** True si un justificatif est deja attache cote backend. */
  justificatifExistant(): boolean {
    return !!this.data.operation.justificatifPresent;
  }

  onJustificatifSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return;
    const types = ['application/pdf', 'image/jpeg', 'image/jpg', 'image/png'];
    if (!types.includes(file.type)) {
      this.snack.open('Format non supporte. PDF, JPG ou PNG uniquement.', 'OK',
        { duration: 4000, panelClass: 'snackbar-error' });
      input.value = '';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.snack.open('Fichier trop volumineux (max 5 Mo).', 'OK',
        { duration: 4000, panelClass: 'snackbar-error' });
      input.value = '';
      return;
    }
    this.fichier = file;
  }

  iconePourFichier(typeMime?: string): string {
    if (!typeMime) return 'description';
    return typeMime === 'application/pdf' ? 'picture_as_pdf' : 'image';
  }

  tailleLisible(octets: number): string {
    const ko = octets / 1024;
    return ko > 1024
      ? (ko / 1024).toFixed(2) + ' Mo'
      : ko.toFixed(0) + ' Ko';
  }

  telechargerJustificatif(): void {
    const op = this.data.operation;
    this.api.telechargerJustificatif(op.id).subscribe({
      next: (res) => {
        const url = URL.createObjectURL(res.blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = res.nomFichier || op.justificatifNomFichier || 'justificatif';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1500);
      },
      error: () => this.snack.open('Telechargement impossible.', 'OK',
        { duration: 3000, panelClass: 'snackbar-error' })
    });
  }

  /**
   * Recalcule le timbre fiscal en local (meme regle que le backend) :
   *   - mode ESPECES + montant >= 20 000 FCFA -> timbre = 1%, arrondi
   *   - tout autre mode (cheque, virement, mobile money, carte) -> timbre = 0
   *   - mode ESPECES + montant < 20 000 -> timbre = 0
   * Le backend recalcule TOUJOURS de toute facon (autoritatif). Ici le
   * dialog de modification ne permet pas de changer le mode (verrouille
   * sur celui de l'operation d'origine), donc on regarde simplement la
   * valeur de l'operation existante.
   */
  recalculer(): void {
    const montantNum = Number(this.montant) || 0;
    // En mode AUTO uniquement : recalcul du timbre selon la règle ESPECES 1%.
    // En mode MANUEL, on conserve la valeur saisie par l'utilisateur.
    if (!this.timbreManuel) {
      const especes = this.data.operation.modePaiement === 'ESPECES';
      this.timbre = (especes && montantNum >= 20000)
          ? Math.round(montantNum * 0.01)
          : 0;
    }
    this.montantSig.set(montantNum);
    this.timbreSig.set(Number(this.timbre) || 0);
  }

  estValide(): boolean {
    // Le montant doit etre positif ET la date de diffusion est desormais
    // obligatoire (regle metier RTS : toute operation = un creneau d'antenne).
    return this.montant > 0 && !!this.dateDiffusion;
  }

  /**
   * Le timbre fiscal ne concerne que les paiements en ESPECES. Pour tous
   * les autres modes (cheque, virement, mobile money, carte), le champ
   * Timbre est masque (et la valeur reste a 0).
   */
  estEspeces(): boolean {
    return this.data.operation.modePaiement === 'ESPECES';
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
      timbreManuel: this.timbreManuel,
      modePaiement: op.modePaiement,
      motif: this.motif,
      reference: this.reference || undefined,
      banqueId: op.banqueId,
      // Date de diffusion : desormais obligatoire (validee par estValide()).
      dateDiffusion: this.dateDiffusion
    };
    this.saving.set(true);
    this.api.modifier(op.id, req).subscribe({
      next: (updated) => {
        // Si l'utilisateur a selectionne un nouveau justificatif, on l'envoie
        // dans la foulee (POST multipart). Sinon, on termine immediatement.
        if (this.fichier) {
          this.api.uploaderJustificatif(updated.id, this.fichier).subscribe({
            next: (avecFichier) => {
              this.saving.set(false);
              this.snack.open('Opération modifiée et justificatif joint.', 'OK',
                { duration: 3000, panelClass: 'snackbar-success' });
              this.dialogRef.close(avecFichier);
            },
            error: (err) => {
              this.saving.set(false);
              const msg = err?.error?.message ?? 'Operation modifiee mais upload du justificatif refuse.';
              this.snack.open(msg, 'OK',
                { duration: 5000, panelClass: 'snackbar-error' });
              // L'operation a ete sauvee, on retourne le resultat partiel
              this.dialogRef.close(updated);
            }
          });
        } else {
          this.saving.set(false);
          this.snack.open('Opération modifiée. Solde caisse recalculé.', 'OK',
            { duration: 3000, panelClass: 'snackbar-success' });
          this.dialogRef.close(updated);
        }
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
