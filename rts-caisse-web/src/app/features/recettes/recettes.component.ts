import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { Caisse, ConsolidationRecette, VentilationRecette } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CaisseService } from '../../core/services/admin.services';
import { RecetteService } from '../../core/services/recette.service';

@Component({
  selector: 'rts-recettes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSelectModule,
    MatTooltipModule,
    MatProgressSpinnerModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './recettes.component.html',
  styleUrls: ['./recettes.component.css']
})
export class RecettesComponent implements OnInit {
  private readonly service   = inject(RecetteService);
  private readonly auth      = inject(AuthService);
  private readonly caisseApi = inject(CaisseService);
  private readonly snackBar  = inject(MatSnackBar);

  readonly caisses = signal<Caisse[]>([]);
  caisseSelectionneeId: number | null = null;

  /** Mode d'affichage : par caisse (avec validation) ou consolidé toutes caisses. */
  readonly mode = signal<'caisse' | 'consolide'>('caisse');
  readonly consolidation = signal<ConsolidationRecette | null>(null);

  /** Plage DU / AU. Par défaut : semaine courante (lundi → dimanche). */
  dateDebut: Date = this.lundiCetteSemaine();
  dateFin: Date = (() => { const d = this.lundiCetteSemaine(); d.setDate(d.getDate() + 6); return d; })();

  readonly ventilation = signal<VentilationRecette | null>(null);
  readonly chargement = signal(false);
  readonly actionEnCours = signal(false);

  /** Boutons de validation selon le rôle + l'état courant. */
  readonly peutControle1 = computed(() =>
    this.auth.hasRole('CHEF_UNITE_FINANCES') && this.ventilation()?.statut === 'BROUILLON');
  readonly peutControle2 = computed(() =>
    this.auth.hasRole('CHEF_DEPARTEMENT') && this.ventilation()?.statut === 'CONTROLE_1');

  ngOnInit(): void {
    this.caisseApi.lister().subscribe({
      next: (toutes) => {
        this.caisses.set(toutes);
        if (toutes.length === 1) {
          this.caisseSelectionneeId = toutes[0].id;
          this.generer();
        }
      }
    });
  }

  changerMode(): void {
    this.ventilation.set(null);
    this.consolidation.set(null);
  }

  generer(): void {
    let debut = this.dateDebut, fin = this.dateFin;
    if (debut && fin && fin < debut) [debut, fin] = [fin, debut];

    if (this.mode() === 'consolide') {
      this.chargement.set(true);
      this.service.consolidation(this.toIso(debut), this.toIso(fin)).subscribe({
        next: (c) => { this.consolidation.set(c); this.ventilation.set(null); this.chargement.set(false); },
        error: () => this.chargement.set(false)
      });
      return;
    }

    if (!this.caisseSelectionneeId) {
      this.snackBar.open('Sélectionnez une caisse.', 'OK', { duration: 3000 });
      return;
    }
    this.chargement.set(true);
    this.service.ventilation(this.caisseSelectionneeId, this.toIso(debut), this.toIso(fin)).subscribe({
      next: (v) => { this.ventilation.set(v); this.consolidation.set(null); this.chargement.set(false); },
      error: () => { this.chargement.set(false); }
    });
  }

  controle1(): void {
    const v = this.ventilation();
    if (!v) return;
    this.actionEnCours.set(true);
    this.service.controle1(v.recetteId).subscribe({
      next: (maj) => {
        this.ventilation.set(maj);
        this.actionEnCours.set(false);
        this.snackBar.open('Contrôle 1 validé (Chef Unité Finances).', 'OK',
          { duration: 3000, panelClass: ['snackbar-success'] });
      },
      error: () => this.actionEnCours.set(false)
    });
  }

  controle2(): void {
    const v = this.ventilation();
    if (!v) return;
    this.actionEnCours.set(true);
    this.service.controle2(v.recetteId).subscribe({
      next: (maj) => {
        this.ventilation.set(maj);
        this.actionEnCours.set(false);
        this.snackBar.open('Contrôle 2 validé — ventilation validée.', 'OK',
          { duration: 3000, panelClass: ['snackbar-success'] });
      },
      error: () => this.actionEnCours.set(false)
    });
  }

  telechargerPdf(): void {
    const v = this.ventilation();
    if (!v) return;
    this.service.pdf(v.recetteId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `ventilation-${v.caisseCode}-${v.dateDebut}_${v.dateFin}.pdf`;
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => window.URL.revokeObjectURL(url), 100);
      },
      error: () => this.snackBar.open('Échec du téléchargement du PDF.', 'Fermer',
        { duration: 4000, panelClass: ['snackbar-error'] })
    });
  }

  libelleStatut(statut: string | undefined): string {
    switch (statut) {
      case 'BROUILLON':  return 'Brouillon';
      case 'CONTROLE_1': return 'Contrôle 1 effectué';
      case 'VALIDEE':    return 'Validée';
      default:           return statut ?? '';
    }
  }

  classeStatut(statut: string | undefined): string {
    switch (statut) {
      case 'VALIDEE':    return 'badge-ok';
      case 'CONTROLE_1': return 'badge-mid';
      default:           return 'badge-draft';
    }
  }

  private lundiCetteSemaine(): Date {
    const d = new Date();
    const offset = (d.getDay() + 6) % 7; // 0 = lundi … 6 = dimanche
    d.setDate(d.getDate() - offset);
    d.setHours(0, 0, 0, 0);
    return d;
  }

  private toIso(d: Date | null | undefined): string | undefined {
    if (!d) return undefined;
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const j = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${j}`;
  }
}
