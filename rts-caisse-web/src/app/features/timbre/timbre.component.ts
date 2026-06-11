import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CategorieOperation, ModePaiement, TimbreConfig } from '../../core/models/models';
import { CategorieService, TimbreService } from '../../core/services/admin.services';

@Component({
  selector: 'rts-timbre',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatButtonModule,
    MatIconModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './timbre.component.html',
  styleUrls: ['./timbre.component.css']
})
export class TimbreComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(TimbreService);
  private readonly categorieService = inject(CategorieService);
  private readonly snackBar = inject(MatSnackBar);

  readonly categories = signal<CategorieOperation[]>([]);
  readonly chargement = signal(true);
  readonly enregistrement = signal(false);

  /** Modes de paiement sélectionnables (libellés lisibles). */
  readonly modesDisponibles: ReadonlyArray<{ value: ModePaiement; label: string }> = [
    { value: 'ESPECES', label: 'Espèces' },
    { value: 'CHEQUE', label: 'Chèque' },
    { value: 'VIREMENT', label: 'Virement bancaire' },
    { value: 'CARTE_BANCAIRE', label: 'Carte bancaire' },
    { value: 'WAVE', label: 'Wave' },
    { value: 'ORANGE_MONEY', label: 'Orange Money' },
    { value: 'FREE_MONEY', label: 'Free Money' }
  ];

  readonly form = this.fb.nonNullable.group({
    actif: [true],
    seuil: [20000, [Validators.required, Validators.min(0)]],
    pourcentage: [1, [Validators.required, Validators.min(0), Validators.max(100)]],
    modesPaiement: [[] as ModePaiement[]],
    categorieIds: [[] as number[]]
  });

  ngOnInit(): void {
    this.categorieService.lister().subscribe((cats) => this.categories.set(cats));
    this.service.obtenir().subscribe({
      next: (cfg) => {
        this.form.patchValue({
          actif: cfg.actif,
          seuil: cfg.seuil,
          pourcentage: cfg.pourcentage,
          modesPaiement: cfg.modesPaiement ?? [],
          categorieIds: cfg.categorieIds ?? []
        });
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false)
    });
  }

  enregistrer(): void {
    if (this.form.invalid) return;
    this.enregistrement.set(true);
    const config = this.form.getRawValue() as TimbreConfig;
    this.service.mettreAJour(config).subscribe({
      next: () => {
        this.snackBar.open('Configuration du timbre enregistrée', 'OK', {
          duration: 2500,
          panelClass: ['snackbar-success']
        });
        this.enregistrement.set(false);
      },
      error: (err) => {
        this.snackBar.open(
          err?.error?.message ?? "Échec de l'enregistrement de la configuration",
          'OK',
          { duration: 4000, panelClass: ['snackbar-error'] });
        this.enregistrement.set(false);
      }
    });
  }
}
