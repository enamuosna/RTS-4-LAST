import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import { Caisse, JournalCaisse } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CaisseService } from '../../core/services/admin.services';
import { JournalService } from '../../core/services/caisse.services';

@Component({
  selector: 'rts-journaux',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatSelectModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './journaux.component.html',
  styleUrls: ['./journaux.component.css']
})
export class JournauxComponent implements OnInit {
  private readonly service      = inject(JournalService);
  private readonly auth         = inject(AuthService);
  private readonly caisseApi    = inject(CaisseService);
  private readonly snackBar     = inject(MatSnackBar);

  readonly journaux = signal<JournalCaisse[]>([]);
  readonly exportEnCours = signal<number | null>(null);

  /** Liste des caisses pour le selecteur. Filtree pour AGENT_RECETTE. */
  readonly caisses = signal<Caisse[]>([]);
  /** Caisse selectionnee dans le filtre. null = toutes les caisses. */
  caisseSelectionneeId: number | null = null;
  /** L'utilisateur peut-il choisir une caisse ? (Non pour AGENT_RECETTE.) */
  readonly peutChoisirCaisse = signal<boolean>(true);

  /** Pour l'AGENT_RECETTE : ids des caisses qui lui sont affectees.
   *  Vide pour les autres roles = pas de filtre. */
  private mesCaisseIds: Set<number> = new Set<number>();

  /** Plage de dates (incluses) pour le filtrage. Par defaut : 30 derniers jours. */
  dateDebut: Date = (() => { const d = new Date(); d.setDate(d.getDate() - 30); return d; })();
  dateFin:   Date = new Date();

  readonly colonnes = [
    'date',
    'caisse',
    'caissier',
    'fond',
    'entrees',
    'sorties',
    'theorique',
    'reel',
    'ecart',
    'statut',
    'actions'
  ];

  ngOnInit(): void {
    // On charge les caisses pour alimenter le selecteur. Pour AGENT_RECETTE,
    // on lock sur sa caisse affectee.
    this.caisseApi.lister().subscribe({
      next: (toutes) => {
        const role = this.auth.currentRole();
        if (role === 'AGENT_RECETTE') {
          const userId = this.auth.currentUser()?.utilisateurId;
          const mesCaisses = toutes.filter(c => c.agentRecetteId === userId);
          this.mesCaisseIds = new Set(mesCaisses.map(c => c.id));
          this.caisses.set(mesCaisses);
          this.peutChoisirCaisse.set(false);
          if (mesCaisses.length === 1) {
            this.caisseSelectionneeId = mesCaisses[0].id;
          }
        } else {
          this.caisses.set(toutes);
        }
        this.charger();
      },
      error: () => this.charger()
    });
  }

  charger(): void {
    let debut = this.dateDebut, fin = this.dateFin;
    if (debut && fin && fin < debut) [debut, fin] = [fin, debut];
    this.service.lister(this.toIso(debut), this.toIso(fin),
                        this.caisseSelectionneeId ?? undefined)
      .subscribe((list) => {
        // Defense en profondeur : restreint aussi cote front pour AGENT_RECETTE.
        if (this.mesCaisseIds.size > 0) {
          list = list.filter(j => this.mesCaisseIds.has(j.caisseId));
        }
        this.journaux.set(list);
      });
  }

  appliquerFiltre(): void {
    this.charger();
  }

  private toIso(d: Date | null | undefined): string | undefined {
    if (!d) return undefined;
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const j = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${j}`;
  }

  peutValider(): boolean {
    return this.auth.hasRole('ADMIN', 'SUPERVISEUR');
  }

  valider(journal: JournalCaisse): void {
    if (!confirm(`Valider la clôture de la caisse "${journal.caisseLibelle}" ?`)) {
      return;
    }
    this.service.valider(journal.id).subscribe(() => {
      this.snackBar.open('Journal validé avec succès', 'OK', {
        duration: 3000,
        panelClass: ['snackbar-success']
      });
      this.charger();
    });
  }

  /**
   * Télécharge le journal au format Excel et déclenche la sauvegarde
   * côté navigateur via création dynamique d'un lien <a download>.
   *
   * Affiche le vrai message d'erreur retourné par le serveur en cas d'échec
   * (au lieu d'un générique "Erreur inattendue") afin de faciliter le diagnostic
   * (problème CORS, endpoint manquant, droits insuffisants, etc.).
   */
  exporter(journal: JournalCaisse): void {
    this.exportEnCours.set(journal.id);

    this.service.exporterExcel(journal.id).subscribe({
      next: (response: HttpResponse<Blob>) => {
        this.exportEnCours.set(null);

        const blob = response.body;
        if (!blob || blob.size === 0) {
          this.snackBar.open('Le serveur a renvoyé un fichier vide', 'Fermer', {
            duration: 4000,
            panelClass: ['snackbar-error']
          });
          return;
        }

        // Vérification supplémentaire : s'assurer qu'on a bien un xlsx
        // et pas une page HTML d'erreur (typiquement page 404 nginx).
        if (blob.type && blob.type.includes('text/html')) {
          this.snackBar.open(
            "Le serveur a renvoyé du HTML au lieu d'un fichier Excel. Vérifiez la config nginx.",
            'Fermer',
            { duration: 7000, panelClass: ['snackbar-error'] }
          );
          return;
        }

        // Nom de fichier suggéré par le header Content-Disposition,
        // ou fallback sur un nom construit côté client.
        const nomFichier =
          this.extraireNomFichier(response) ?? `journal-${journal.id}.xlsx`;

        this.sauvegarderBlob(blob, nomFichier);

        this.snackBar.open(`Fichier ${nomFichier} téléchargé`, 'OK', {
          duration: 3000,
          panelClass: ['snackbar-success']
        });
      },
      error: (err: HttpErrorResponse) => {
        this.exportEnCours.set(null);
        console.error('Échec export Excel:', err);

        let message = 'Échec du téléchargement Excel';
        if (err.status === 0) {
          message = 'Impossible de joindre le serveur (CORS ou réseau)';
        } else if (err.status === 404) {
          message = "L'export n'est pas disponible (endpoint introuvable)";
        } else if (err.status === 401 || err.status === 403) {
          message = "Vous n'avez pas le droit d'exporter ce journal";
        } else if (
          err.error &&
          typeof err.error === 'object' &&
          'message' in err.error
        ) {
          message = (err.error as { message?: string }).message ?? message;
        } else if (err.message) {
          message = `${err.status} - ${err.message}`;
        }

        this.snackBar.open(message, 'Fermer', {
          duration: 6000,
          panelClass: ['snackbar-error']
        });
      }
    });
  }

  private extraireNomFichier(response: HttpResponse<Blob>): string | null {
    const disposition = response.headers.get('Content-Disposition');
    if (!disposition) {
      return null;
    }

    // Format du header : attachment; filename="journal-CAI-01-2026-04-21.xlsx"
    // Supporte aussi le format RFC 5987 : filename*=UTF-8''...
    const match = disposition.match(
      /filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?/i
    );
    return match ? decodeURIComponent(match[1].trim()) : null;
  }

  private sauvegarderBlob(blob: Blob, nomFichier: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nomFichier;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    // Libère la mémoire du blob
    setTimeout(() => window.URL.revokeObjectURL(url), 100);
  }

  classeEcart(j: JournalCaisse): string {
    if (j.ecart === null || j.ecart === undefined) {
      return '';
    }
    if (j.ecart === 0) {
      return 'ecart-nul';
    }
    return j.ecart > 0 ? 'ecart-positif' : 'ecart-negatif';
  }
}