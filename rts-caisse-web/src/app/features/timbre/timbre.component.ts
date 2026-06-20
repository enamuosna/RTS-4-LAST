import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Caisse, CategorieOperation, ModePaiement, ModeTimbre, TimbreConfig } from '../../core/models/models';
import { CaisseService, CategorieService, TimbreService } from '../../core/services/admin.services';

@Component({
  selector: 'rts-timbre',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatButtonModule,
    MatButtonToggleModule,
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
  private readonly caisseService = inject(CaisseService);
  private readonly snackBar = inject(MatSnackBar);

  readonly categories = signal<CategorieOperation[]>([]);
  readonly caisses = signal<Caisse[]>([]);
  readonly chargement = signal(true);
  readonly enregistrement = signal(false);

  /** Caisse sélectionnée (la config s'applique uniquement à elle). */
  caisseSelectionneeId: number | null = null;

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
    mode: ['AUTO' as ModeTimbre],
    actif: [true],
    seuil: [20000, [Validators.required, Validators.min(0)]],
    pourcentage: [1, [Validators.required, Validators.min(0), Validators.max(100)]],
    modesPaiement: [[] as ModePaiement[]],
    categorieIds: [[] as number[]]
  });

  /** True si le mode courant est MANUEL (le caissier saisira le timbre). */
  get estManuel(): boolean {
    return this.form.controls.mode.value === 'MANUEL';
  }

  ngOnInit(): void {
    this.categorieService.lister().subscribe((cats) => this.categories.set(cats));
    this.caisseService.lister().subscribe((cs) => {
      this.caisses.set(cs);
      this.chargement.set(false);
    });
    // En mode MANUEL, les règles de calcul n'ont pas de sens : on les désactive.
    this.form.controls.mode.valueChanges.subscribe(() => this.appliquerEtatMode());
  }

  /** Charge la config de la caisse sélectionnée. */
  chargerCaisse(): void {
    if (this.caisseSelectionneeId == null) return;
    this.chargement.set(true);
    this.service.obtenir(this.caisseSelectionneeId).subscribe({
      next: (cfg) => {
        this.form.patchValue({
          mode: cfg.mode ?? 'AUTO',
          actif: cfg.actif,
          seuil: cfg.seuil,
          pourcentage: cfg.pourcentage,
          modesPaiement: cfg.modesPaiement ?? [],
          categorieIds: cfg.categorieIds ?? []
        });
        this.appliquerEtatMode();
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false)
    });
  }

  private appliquerEtatMode(): void {
    const ctrls = [this.form.controls.actif, this.form.controls.seuil,
      this.form.controls.pourcentage, this.form.controls.modesPaiement,
      this.form.controls.categorieIds];
    if (this.estManuel) {
      ctrls.forEach((c) => c.disable({ emitEvent: false }));
    } else {
      ctrls.forEach((c) => c.enable({ emitEvent: false }));
    }
  }

  enregistrer(): void {
    if (this.caisseSelectionneeId == null) {
      this.snackBar.open('Sélectionnez d\'abord une caisse.', 'OK', { duration: 3000 });
      return;
    }
    if (this.form.invalid) return;
    this.enregistrement.set(true);
    const config = this.form.getRawValue() as TimbreConfig;
    this.service.mettreAJour(config, this.caisseSelectionneeId).subscribe({
      next: () => {
        this.snackBar.open('Configuration du timbre enregistrée pour la caisse', 'OK', {
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
