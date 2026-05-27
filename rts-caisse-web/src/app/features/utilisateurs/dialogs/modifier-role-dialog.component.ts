import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Role, Utilisateur } from '@core/models/models';
import { UtilisateurService } from '@core/services/admin.services';

/**
 * Dialog de modification du role d'un utilisateur. Reserve aux ADMIN.
 *
 * <p>Affiche un select avec les 4 roles RTS et leur description metier
 * pour aider l'admin a faire le bon choix. Refuse silencieusement les
 * changements idempotents (meme role). Le backend rejette les tentatives
 * de modification du role du super-admin avec un message clair.</p>
 */
@Component({
  selector: 'rts-modifier-role-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Modifier le rôle</h2>

    <form [formGroup]="form" (ngSubmit)="valider()">
      <mat-dialog-content class="dialog-content">
        <p class="info">
          Utilisateur :
          <strong>{{ data.utilisateur.prenom }} {{ data.utilisateur.nom }}</strong>
          ({{ data.utilisateur.matricule }})
        </p>

        <div class="ancien">
          <span class="ancien-label">Rôle actuel :</span>
          <span class="ancien-value">{{ roleLibelle(data.utilisateur.role) }}</span>
        </div>

        <mat-form-field appearance="outline">
          <mat-label>Nouveau rôle</mat-label>
          <mat-select formControlName="nouveau">
            <mat-option value="ADMIN">
              Administrateur
              <small class="role-desc"> — accès total au système</small>
            </mat-option>
            <mat-option value="SUPERVISEUR">
              Superviseur
              <small class="role-desc"> — vue d'ensemble, valide les clôtures</small>
            </mat-option>
            <mat-option value="AGENT_RECETTE">
              Agent de recette
              <small class="role-desc"> — contrôle + saisie sur sa caisse</small>
            </mat-option>
            <mat-option value="CAISSIER">
              Caissier
              <small class="role-desc"> — saisie d'opérations sur sa caisse</small>
            </mat-option>
          </mat-select>
        </mat-form-field>

        <div class="avertissement">
          <mat-icon>info</mat-icon>
          <span>
            Le changement de rôle est <strong>immédiat</strong>. L'utilisateur
            verra ses droits mis à jour à sa prochaine connexion.
          </span>
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" (click)="dialogRef.close()">Annuler</button>
        <button
          mat-flat-button
          color="primary"
          type="submit"
          [disabled]="form.invalid || loading() || nouveauEgalAncien()"
        >
          <mat-icon>check</mat-icon>
          Enregistrer
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .dialog-content {
      display: flex;
      flex-direction: column;
      gap: 12px;
      min-width: 420px;
      padding-top: 8px;
    }
    .info {
      margin: 0 0 4px;
      color: rgba(0, 0, 0, 0.6);
      font-size: 14px;
    }
    .ancien {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      background: #f5f5f7;
      border-radius: 8px;
      font-size: 14px;
    }
    .ancien-label {
      color: rgba(0, 0, 0, 0.6);
    }
    .ancien-value {
      font-weight: 600;
      color: #1a1a1a;
    }
    .role-desc {
      color: rgba(0, 0, 0, 0.5);
      font-weight: normal;
    }
    .avertissement {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      padding: 10px 12px;
      background: rgba(227, 6, 19, 0.06);
      border-left: 3px solid #E30613;
      border-radius: 6px;
      font-size: 13px;
      color: rgba(0, 0, 0, 0.75);
      line-height: 1.4;
    }
    .avertissement mat-icon {
      color: #E30613;
      font-size: 18px;
      width: 18px;
      height: 18px;
      flex-shrink: 0;
      margin-top: 1px;
    }
  `]
})
export class ModifierRoleDialogComponent {
  readonly dialogRef = inject(MatDialogRef<ModifierRoleDialogComponent>);
  readonly data = inject<{ utilisateur: Utilisateur }>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(UtilisateurService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(false);

  readonly form = this.fb.nonNullable.group({
    nouveau: [this.data.utilisateur.role as Role, Validators.required]
  });

  nouveauEgalAncien(): boolean {
    return this.form.controls.nouveau.value === this.data.utilisateur.role;
  }

  roleLibelle(role: Role): string {
    switch (role) {
      case 'ADMIN':         return 'Administrateur';
      case 'SUPERVISEUR':   return 'Superviseur';
      case 'CAISSIER':      return 'Caissier';
      case 'AGENT_RECETTE': return 'Agent de recette';
      default:              return role;
    }
  }

  valider(): void {
    if (this.form.invalid || this.nouveauEgalAncien()) return;
    const nouveau = this.form.controls.nouveau.value;

    this.loading.set(true);
    this.service.modifierRole(this.data.utilisateur.id, nouveau).subscribe({
      next: (u) => {
        this.snackBar.open(
          `Rôle modifié : ${u.login} -> ${this.roleLibelle(u.role)}`,
          'OK',
          { duration: 3500, panelClass: ['snackbar-success'] });
        this.dialogRef.close(u);
      },
      error: () => this.loading.set(false)
    });
  }
}
