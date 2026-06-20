import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { Langue } from '../../core/models/models';
import { LangueService } from '../../core/services/admin.services';

@Component({
  selector: 'rts-langues',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSlideToggleModule, MatTooltipModule
  ],
  template: `
    <div class="page">
      <h1>Langues de diffusion</h1>
      <p class="intro">
        Référentiel des langues de diffusion à l'antenne (français, wolof, pulaar…).
        Elles sont proposées au guichet pour les produits qui acceptent une langue.
      </p>

      <mat-card class="card add-card">
        <h2>Ajouter une langue</h2>
        <div class="row">
          <mat-form-field appearance="outline" class="f-code">
            <mat-label>Code</mat-label>
            <input matInput [(ngModel)]="nCode" maxlength="20" placeholder="ex. WO" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="f-lib">
            <mat-label>Libellé</mat-label>
            <input matInput [(ngModel)]="nLibelle" maxlength="60" placeholder="ex. Wolof" />
          </mat-form-field>
          <button mat-flat-button color="primary" (click)="ajouter()"
                  [disabled]="!nCode.trim() || !nLibelle.trim()">
            <mat-icon>add</mat-icon> Ajouter
          </button>
        </div>
      </mat-card>

      <mat-card class="card">
        <table class="grid">
          <thead>
            <tr><th>Code</th><th class="left">Libellé</th><th>Active</th><th>Actions</th></tr>
          </thead>
          <tbody>
            @for (l of langues(); track l.id) {
              <tr>
                <td>{{ l.code }}</td>
                <td class="left">
                  <input class="edit" [(ngModel)]="l.libelle" maxlength="60" />
                </td>
                <td>
                  <mat-slide-toggle [(ngModel)]="l.actif" (change)="enregistrer(l)"></mat-slide-toggle>
                </td>
                <td class="actions">
                  <button mat-icon-button color="primary" (click)="enregistrer(l)" matTooltip="Enregistrer">
                    <mat-icon>save</mat-icon>
                  </button>
                  <button mat-icon-button color="warn" (click)="supprimer(l)" matTooltip="Supprimer">
                    <mat-icon>delete</mat-icon>
                  </button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="4" class="vide">Aucune langue. Ajoutez-en une ci-dessus.</td></tr>
            }
          </tbody>
        </table>
      </mat-card>
    </div>
  `,
  styles: [`
    .page { padding: 8px 4px 32px; }
    h1 { font-size: 22px; font-weight: 600; margin: 0 0 4px; }
    .intro { color: rgba(0,0,0,.55); font-size: 13px; margin: 0 0 16px; }
    h2 { font-size: 15px; font-weight: 600; margin: 0 0 12px; }
    .card { background:#fff; border:1px solid #e6e8ee; border-radius:12px; padding:16px 18px; margin-bottom:16px; }
    .row { display:flex; gap:12px; align-items:center; flex-wrap:wrap; }
    .f-code { width:140px; } .f-lib { flex:1 1 240px; }
    table.grid { width:100%; border-collapse:collapse; font-size:13.5px; }
    table.grid th, table.grid td { border:1px solid #eef0f4; padding:6px 10px; text-align:center; }
    table.grid th { background:#faf2f3; color:#b30510; font-weight:600; }
    .left { text-align:left; }
    .actions { white-space:nowrap; }
    .edit { width:100%; border:1px solid #dfe3ea; border-radius:6px; padding:6px 8px; font:inherit; }
    .vide { color:rgba(0,0,0,.45); font-style:italic; }
  `]
})
export class LanguesComponent implements OnInit {
  private readonly service = inject(LangueService);
  private readonly snack = inject(MatSnackBar);

  readonly langues = signal<Langue[]>([]);
  nCode = '';
  nLibelle = '';

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.service.lister(false).subscribe((l) => this.langues.set(l));
  }

  ajouter(): void {
    this.service.creer({ code: this.nCode.trim(), libelle: this.nLibelle.trim(), actif: true })
      .subscribe({
        next: () => { this.nCode = ''; this.nLibelle = ''; this.charger();
          this.snack.open('Langue ajoutée', 'OK', { duration: 2000, panelClass: ['snackbar-success'] }); },
        error: (e) => this.snack.open(e?.error?.message ?? 'Ajout impossible', 'OK',
          { duration: 4000, panelClass: ['snackbar-error'] })
      });
  }

  enregistrer(l: Langue): void {
    if (l.id == null) return;
    this.service.modifier(l.id, l).subscribe({
      next: () => this.snack.open('Langue enregistrée', 'OK', { duration: 1800, panelClass: ['snackbar-success'] }),
      error: (e) => this.snack.open(e?.error?.message ?? 'Échec', 'OK',
        { duration: 4000, panelClass: ['snackbar-error'] })
    });
  }

  supprimer(l: Langue): void {
    if (l.id == null) return;
    if (!confirm(`Supprimer la langue « ${l.libelle} » ?`)) return;
    this.service.supprimer(l.id).subscribe({
      next: () => this.charger(),
      error: (e) => this.snack.open(e?.error?.message ?? 'Suppression impossible', 'OK',
        { duration: 4000, panelClass: ['snackbar-error'] })
    });
  }
}
