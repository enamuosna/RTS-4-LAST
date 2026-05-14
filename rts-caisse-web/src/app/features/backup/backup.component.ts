import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BackupService, RapportImportBackup } from '../../core/services/caisse.services';

/**
 * Page Sauvegarde — réservée ADMIN.
 *
 * <p>Deux fonctionnalités :
 * <ul>
 *   <li><b>Export</b> : un clic, on télécharge un fichier {@code .sql}
 *       horodaté contenant l'intégralité de la BDD (pg_dump).</li>
 *   <li><b>Import</b> : restaure depuis un fichier {@code .sql} précédemment
 *       exporté. <b>Écrase</b> les données existantes — une saisie explicite
 *       du mot {@code RESTORE} est exigée avant l'envoi.</li>
 * </ul>
 */
@Component({
  selector: 'rts-backup',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatSnackBarModule,
    MatTooltipModule
  ],
  templateUrl: './backup.component.html',
  styleUrls: ['./backup.component.css']
})
export class BackupComponent {
  private readonly api   = inject(BackupService);
  private readonly snack = inject(MatSnackBar);

  readonly exporting = signal(false);
  readonly importing = signal(false);

  /** Fichier .sql sélectionné par l'utilisateur, pas encore envoyé. */
  selectedFile: File | null = null;

  /** Mot tapé par l'utilisateur ; doit valoir "RESTORE" pour déverrouiller l'import. */
  confirmText = '';

  /** Dernier rapport d'import affiché à l'utilisateur. */
  rapport: RapportImportBackup | null = null;

  // ==================================================================
  //  EXPORT
  // ==================================================================

  telecharger(): void {
    this.exporting.set(true);
    this.api.exporter().subscribe({
      next: (blob) => {
        this.exporting.set(false);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = this.nomFichierExport();
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1500);
        this.snack.open('Sauvegarde téléchargée.', 'OK',
          { duration: 2500, panelClass: 'snackbar-success' });
      },
      error: (err) => {
        this.exporting.set(false);
        const message = err?.error?.message
            ?? "Échec de l'export de la BDD.";
        this.snack.open(message, 'OK',
          { duration: 5000, panelClass: 'snackbar-error' });
      }
    });
  }

  private nomFichierExport(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `rts-caisse-backup-${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
         + `-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}.sql`;
  }

  // ==================================================================
  //  IMPORT
  // ==================================================================

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (file && !file.name.toLowerCase().endsWith('.sql')) {
      this.snack.open('Le fichier doit avoir l\'extension .sql', 'OK',
        { duration: 3500, panelClass: 'snackbar-error' });
      input.value = '';
      this.selectedFile = null;
      return;
    }
    this.selectedFile = file;
    this.rapport = null;
  }

  /** Activé seulement si fichier sélectionné ET confirmation tapée correctement. */
  get peutImporter(): boolean {
    return !!this.selectedFile
        && this.confirmText.trim().toUpperCase() === 'RESTORE'
        && !this.importing();
  }

  restaurer(): void {
    if (!this.peutImporter || !this.selectedFile) return;

    this.importing.set(true);
    this.rapport = null;

    this.api.importer(this.selectedFile).subscribe({
      next: (r) => {
        this.importing.set(false);
        this.rapport = r;
        this.confirmText = '';
        this.snack.open('Restauration terminée. Redémarrage du backend recommandé.',
          'OK', { duration: 5000, panelClass: 'snackbar-success' });
      },
      error: (err) => {
        this.importing.set(false);
        const message = err?.error?.message
            ?? "Échec de la restauration.";
        this.rapport = {
          succes: false,
          tailleOctets: this.selectedFile?.size ?? 0,
          message,
          dernieresLignesPsql: err?.error?.dernieresLignesPsql ?? []
        };
        this.snack.open(message, 'OK',
          { duration: 6000, panelClass: 'snackbar-error' });
      }
    });
  }

  reinitialiserImport(): void {
    this.selectedFile = null;
    this.confirmText = '';
    this.rapport = null;
  }

  // ==================================================================
  //  Format
  // ==================================================================

  formaterTaille(octets: number): string {
    if (octets < 1024) return `${octets} o`;
    if (octets < 1024 * 1024) return `${(octets / 1024).toFixed(1)} Ko`;
    return `${(octets / 1024 / 1024).toFixed(2)} Mo`;
  }
}
