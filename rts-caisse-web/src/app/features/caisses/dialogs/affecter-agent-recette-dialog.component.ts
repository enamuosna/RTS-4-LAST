import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { Inject } from '@angular/core';
import { Utilisateur } from '../../../core/models/models';
import { UtilisateurService } from '../../../core/services/admin.services';

/**
 * Dialog d'affectation (ou de détachement) d'un agent de recette à une
 * caisse. Liste les utilisateurs ayant le rôle AGENT_RECETTE.
 *
 * <p>Le bouton « Aucun agent » détache l'agent éventuellement déjà affecté
 * (utile en cas de mutation).</p>
 */
@Component({
  selector: 'rts-affecter-agent-recette-dialog',
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
    <h2 mat-dialog-title>Affecter un agent de recette</h2>

    <mat-dialog-content class="dialog-content">
      <p class="hint">
        L'agent de recette pourra modifier les opérations et réactiver
        celles annulées par erreur sur cette caisse.
      </p>
      @if (agents().length === 0) {
        <div class="empty">
          Aucun utilisateur AGENT_RECETTE n'est enregistré.
          Créez-en un dans la page Utilisateurs.
        </div>
      } @else {
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Agent de recette</mat-label>
          <mat-select [(value)]="agentId">
            @for (u of agents(); track u.id) {
              <mat-option [value]="u.id">{{ u.prenom }} {{ u.nom }} ({{ u.matricule }})</mat-option>
            }
          </mat-select>
        </mat-form-field>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">Annuler</button>
      @if (data?.agentDejaAffecte) {
        <button mat-stroked-button color="warn"
                (click)="dialogRef.close({ detacher: true })">
          Détacher l'agent
        </button>
      }
      <button
        mat-flat-button
        color="primary"
        [disabled]="!agentId"
        (click)="dialogRef.close({ agentId })"
      >
        Affecter
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dialog-content { min-width: 380px; }
    .full-width { width: 100%; }
    .hint {
      font-size: 12px;
      color: var(--rts-gray-500);
      margin: 0 0 14px 0;
      line-height: 1.5;
    }
    .empty {
      padding: 14px;
      background: var(--rts-warning-soft);
      border-left: 3px solid var(--rts-warning);
      border-radius: 6px;
      font-size: 13px;
      color: var(--rts-gray-900);
    }
  `]
})
export class AffecterAgentRecetteDialogComponent implements OnInit {
  readonly dialogRef = inject(MatDialogRef<AffecterAgentRecetteDialogComponent>);
  private readonly service = inject(UtilisateurService);

  readonly agents = signal<Utilisateur[]>([]);
  agentId: number | null = null;

  constructor(@Inject(MAT_DIALOG_DATA) public data: { agentDejaAffecte?: boolean } | null) {}

  ngOnInit(): void {
    this.service.lister().subscribe((list) =>
      this.agents.set(list.filter((u) => u.role === 'AGENT_RECETTE' && u.actif))
    );
  }
}
