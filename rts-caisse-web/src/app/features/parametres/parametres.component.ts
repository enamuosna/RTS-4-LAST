import { CommonModule } from '@angular/common';
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
  private readonly sanitizer = inject(DomSanitizer);
  private readonly snack = inject(MatSnackBar);

  readonly loading      = signal(true);
  readonly saving       = signal(false);
  readonly uploadingLogo = signal(false);
  readonly previewUrl   = signal<SafeResourceUrl | null>(null);
  readonly logoUrl      = signal<SafeResourceUrl | null>(null);
  private currentLogoObjectUrl: string | null = null;

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
        this.rafraichirLogo();
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Impossible de charger les paramètres.', 'OK',
          { duration: 4000, panelClass: 'snackbar-error' });
      }
    });
  }

  /** Recharge l'image du logo depuis le backend, l'affiche en object URL.
   *  N'appelle l'API que si le backend a indiqué qu'un logo existe
   *  (champ logoPresent du DTO) — évite un 404 inutile dans la console. */
  rafraichirLogo(): void {
    this.revoquerLogoUrl();
    if (!this.params?.logoPresent) {
      this.logoUrl.set(null);
      return;
    }
    this.api.obtenirLogo().subscribe((blob) => {
      if (blob) {
        const objectUrl = URL.createObjectURL(blob);
        this.currentLogoObjectUrl = objectUrl;
        this.logoUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
      } else {
        this.logoUrl.set(null);
      }
    });
  }

  /** Sélection d'un fichier dans l'<input type="file"> : upload immédiat. */
  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      this.snack.open('Image trop volumineuse (2 Mo max).', 'OK',
        { duration: 4000, panelClass: 'snackbar-error' });
      input.value = '';
      return;
    }
    this.uploadingLogo.set(true);
    this.api.uploadLogo(file).subscribe({
      next: () => {
        this.uploadingLogo.set(false);
        if (this.params) this.params.logoPresent = true;
        this.snack.open('Logo mis à jour.', 'OK',
          { duration: 2500, panelClass: 'snackbar-success' });
        this.rafraichirLogo();
        this.rafraichirApercu();
        input.value = '';
      },
      error: (err) => {
        this.uploadingLogo.set(false);
        const message = err?.error?.message ?? "Échec du téléversement du logo.";
        this.snack.open(message, 'OK',
          { duration: 4000, panelClass: 'snackbar-error' });
        input.value = '';
      }
    });
  }

  supprimerLogo(): void {
    this.api.supprimerLogo().subscribe({
      next: () => {
        if (this.params) this.params.logoPresent = false;
        this.snack.open('Logo supprimé.', 'OK',
          { duration: 2500, panelClass: 'snackbar-success' });
        this.rafraichirLogo();
        this.rafraichirApercu();
      },
      error: () => {
        this.snack.open("Échec de la suppression du logo.", 'OK',
          { duration: 4000, panelClass: 'snackbar-error' });
      }
    });
  }

  private revoquerLogoUrl(): void {
    if (this.currentLogoObjectUrl) {
      URL.revokeObjectURL(this.currentLogoObjectUrl);
      this.currentLogoObjectUrl = null;
    }
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
   * Recharge l'aperçu sous forme d'image PNG (rasterisation côté backend
   * via PDFBox). On utilise une image plutôt qu'une iframe PDF parce que
   * certains navigateurs (Edge Tracking Prevention en mode strict,
   * uBlock, Brave Shields) bloquent les XHR retournant {@code application/pdf}
   * avec ERR_BLOCKED_BY_CLIENT — surtout sur les domaines tiers/dynamiques
   * type duckdns. Les images PNG passent partout.
   */
  rafraichirApercu(): void {
    this.api.obtenirApercuImage().subscribe({
      next: (blob) => {
        const objectUrl = URL.createObjectURL(blob);
        this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
      },
      error: () => {
        this.snack.open('Impossible de générer l\'aperçu.', 'OK',
          { duration: 3000, panelClass: 'snackbar-error' });
      }
    });
  }

  /**
   * Télécharge l'aperçu en PDF (pour ceux qui veulent voir le rendu exact
   * avant impression). Passe par un objectURL puis simule un click sur un
   * lien {@code <a download>} — évite l'iframe.
   */
  telechargerApercuPdf(): void {
    this.api.obtenirApercu().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'recu-demo.pdf';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1500);
      },
      error: () => {
        this.snack.open('Impossible de télécharger l\'aperçu PDF.', 'OK',
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
