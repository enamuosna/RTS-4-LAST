import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { environment } from '../../../environments/environment';
import { ParametresRecu, SectionRecu } from '../../core/models/models';
import { ParametresRecuService } from '../../core/services/caisse.services';

interface SectionMeta {
  id: string;
  libelle: string;
  description: string;
  conditionnelle: boolean;
}

/**
 * Page « Paramètres du reçu ». Réservée aux ADMIN.
 *
 * <p>Permet de modifier les informations statiques (raison sociale, NINEA,
 * adresse...), les couleurs, les tailles de police et l'ordre/visibilité des
 * sections du reçu PDF, avec un aperçu rendu côté backend (la même chaîne
 * de génération que les vrais reçus, donc 100% fidèle).</p>
 */
@Component({
  selector: 'rts-parametres',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    DragDropModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule
  ],
  templateUrl: './parametres.component.html',
  styleUrls: ['./parametres.component.css']
})
export class ParametresComponent implements OnInit {
  private readonly api = inject(ParametresRecuService);
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly snack = inject(MatSnackBar);

  readonly loading = signal(true);
  readonly saving  = signal(false);
  readonly previewUrl = signal<SafeResourceUrl | null>(null);

  /** Modèle bindé en deux sens à toute la page. */
  params: ParametresRecu | null = null;

  /** Métadonnées d'affichage pour chaque section connue. */
  readonly sectionsMeta: Record<string, SectionMeta> = {
    header:     { id: 'header',     libelle: 'En-tête (logo + société)',  description: 'Bandeau avec le logo et les infos statiques de la société', conditionnelle: false },
    titre:      { id: 'titre',      libelle: 'Titre "REÇU"',              description: 'Grand titre central', conditionnelle: false },
    numero:     { id: 'numero',     libelle: 'Numéro de reçu',             description: 'N° d\'opération en gros caractères', conditionnelle: false },
    details:    { id: 'details',    libelle: 'Détails (caisse, agent…)',   description: 'Date, caisse, agent, catégorie, mode de règlement', conditionnelle: false },
    client:     { id: 'client',     libelle: 'Bloc client',                description: 'Affiché uniquement si un client est associé', conditionnelle: true },
    montant:    { id: 'montant',    libelle: 'Bloc montant',               description: 'Le montant en grand format', conditionnelle: false },
    motif:      { id: 'motif',      libelle: 'Motif',                      description: 'Affiché uniquement si un motif a été saisi', conditionnelle: true },
    annulation: { id: 'annulation', libelle: 'Bandeau annulation',         description: 'Affiché uniquement pour les opérations annulées', conditionnelle: true },
    signature:  { id: 'signature',  libelle: 'Signature + date',           description: 'Ligne signature et date du jour', conditionnelle: false },
    footer:     { id: 'footer',     libelle: 'Pied de page',               description: 'Messages de remerciement / mentions légales', conditionnelle: false }
  };

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.loading.set(true);
    this.api.obtenir().subscribe({
      next: (p) => {
        this.params = p;
        this.loading.set(false);
        this.rafraichirApercu();
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Impossible de charger les paramètres.', 'OK',
          { duration: 4000, panelClass: 'snackbar-error' });
      }
    });
  }

  metaPourSection(id: string): SectionMeta {
    return this.sectionsMeta[id]
      ?? { id, libelle: id, description: '', conditionnelle: false };
  }

  onDrop(event: CdkDragDrop<SectionRecu[]>): void {
    if (!this.params) return;
    moveItemInArray(this.params.sections, event.previousIndex, event.currentIndex);
  }

  enregistrer(): void {
    if (!this.params) return;
    this.saving.set(true);
    this.api.mettreAJour(this.params).subscribe({
      next: (p) => {
        this.params = p;
        this.saving.set(false);
        this.snack.open('Paramètres enregistrés.', 'OK',
          { duration: 2500, panelClass: 'snackbar-success' });
        this.rafraichirApercu();
      },
      error: () => {
        this.saving.set(false);
        this.snack.open('Échec de l\'enregistrement.', 'OK',
          { duration: 4000, panelClass: 'snackbar-error' });
      }
    });
  }

  /**
   * Recharge l'aperçu PDF en faisant un GET authentifié (le navigateur ne peut
   * pas attacher le JWT à un <iframe src="...">), puis on transforme le blob
   * en URL locale qui s'affiche dans l'iframe.
   */
  rafraichirApercu(): void {
    const url = `${environment.apiUrl}/recus/apercu?_t=${Date.now()}`;
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const objectUrl = URL.createObjectURL(blob);
        this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
      },
      error: () => {
        this.snack.open('Impossible de générer l\'aperçu PDF.', 'OK',
          { duration: 3000, panelClass: 'snackbar-error' });
      }
    });
  }

  reinitialiserOrdre(): void {
    if (!this.params) return;
    const ordreParDefaut = ['header','titre','numero','details','client','montant','motif','annulation','signature','footer'];
    this.params.sections = ordreParDefaut.map(id => {
      const existing = this.params!.sections.find(s => s.id === id);
      return existing ?? { id, visible: true };
    });
  }
}
