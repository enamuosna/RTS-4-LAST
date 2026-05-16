import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Caisse,
  DashboardResponse,
  JournalCaisse,
  OperationCaisse
} from '../../../core/models/models';
import { CaisseService } from '../../../core/services/admin.services';
import {
  JournalService,
  OperationService,
  ReportingService
} from '../../../core/services/caisse.services';

/**
 * Page « Détail d'une caisse » pour le responsable des caisses RTS.
 * Affiche en une seule vue, pour la caisse sélectionnée :
 * <ul>
 *   <li>Bandeau d'identité (code, libellé, statut, caissier, agent recette, solde)</li>
 *   <li>Sélecteur de plage de dates → KPI filtrés</li>
 *   <li>Tableau paginé de toutes les opérations sur la période, avec un
 *       bouton "imprimer reçu" qui ouvre le PDF dans un nouvel onglet</li>
 *   <li>Liste des journaux de caisse de la même période</li>
 *   <li>Refresh auto 30s du solde + bandeau identité</li>
 * </ul>
 */
@Component({
  selector: 'rts-caisse-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    RouterLink
  ],
  templateUrl: './caisse-detail.component.html',
  styleUrls: ['./caisse-detail.component.css']
})
export class CaisseDetailComponent implements OnInit, OnDestroy {
  private readonly route        = inject(ActivatedRoute);
  private readonly caisseApi    = inject(CaisseService);
  private readonly reportingApi = inject(ReportingService);
  private readonly opApi        = inject(OperationService);
  private readonly journalApi   = inject(JournalService);

  readonly caisseId = signal<number>(0);
  readonly caisse = signal<Caisse | null>(null);
  readonly stats = signal<DashboardResponse | null>(null);
  readonly operations = signal<OperationCaisse[]>([]);
  readonly totalOps = signal(0);
  readonly journaux = signal<JournalCaisse[]>([]);

  readonly loadingCaisse = signal(true);
  readonly loadingStats = signal(false);
  readonly loadingOps = signal(false);
  readonly loadingJournaux = signal(false);

  // Plage de dates (par défaut : 30 derniers jours)
  dateDebut: Date = (() => { const d = new Date(); d.setDate(d.getDate() - 30); return d; })();
  dateFin: Date   = new Date();

  // Pagination opérations
  pageIndex = 0;
  pageSize = 20;

  readonly colonnesOp = [
    'date', 'numero', 'type', 'categorie', 'client',
    'mode', 'montant', 'timbre', 'montantTtc', 'caissier', 'actions'
  ];

  readonly colonnesJournal = [
    'date', 'caissier', 'fond', 'entrees', 'sorties',
    'soldeTheorique', 'soldeReel', 'ecart', 'statut'
  ];

  private refreshSub?: Subscription;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id') ?? 0);
    this.caisseId.set(id);

    // Refresh auto du bandeau identité (solde + statut) toutes les 30s
    this.refreshSub = interval(30_000).pipe(startWith(0),
        switchMap(() => this.caisseApi.lister()))
      .subscribe((list) => {
        this.caisse.set(list.find(c => c.id === id) ?? null);
        this.loadingCaisse.set(false);
      });

    this.chargerStats();
    this.chargerOperations();
    this.chargerJournaux();
  }

  ngOnDestroy(): void {
    this.refreshSub?.unsubscribe();
  }

  // ==================================================================
  //  Chargements
  // ==================================================================

  chargerStats(): void {
    this.loadingStats.set(true);
    let debut = this.dateDebut, fin = this.dateFin;
    if (debut && fin && fin < debut) [debut, fin] = [fin, debut];
    this.reportingApi.dashboard(this.toIso(debut), this.toIso(fin), this.caisseId())
      .subscribe({
        next: (s) => { this.stats.set(s); this.loadingStats.set(false); },
        error: () => this.loadingStats.set(false)
      });
  }

  chargerOperations(): void {
    this.loadingOps.set(true);
    this.opApi.historiqueParCaisse(this.caisseId(), this.pageIndex, this.pageSize)
      .subscribe({
        next: (page) => {
          this.operations.set(page.content);
          this.totalOps.set(page.totalElements);
          this.loadingOps.set(false);
        },
        error: () => this.loadingOps.set(false)
      });
  }

  chargerJournaux(): void {
    this.loadingJournaux.set(true);
    this.journalApi.parCaisse(this.caisseId()).subscribe({
      next: (list) => {
        this.journaux.set(list);
        this.loadingJournaux.set(false);
      },
      error: () => this.loadingJournaux.set(false)
    });
  }

  // ==================================================================
  //  Handlers UI
  // ==================================================================

  appliquerFiltre(): void {
    this.pageIndex = 0;
    this.chargerStats();
    this.chargerOperations();
  }

  changerPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.chargerOperations();
  }

  /** Ouvre le PDF du reçu dans un nouvel onglet (download navigateur). */
  imprimerRecu(op: OperationCaisse): void {
    // On passe par GET /api/operations/{id}/pdf : URL neutre (sans le mot
    // "recus") pour échapper aux filtres uBlock / Brave Shields / Edge
    // Tracking Prevention qui bloquent toute URL contenant "recus" en
    // renvoyant ERR_BLOCKED_BY_CLIENT.
    // Token JWT : on ne peut pas l'attacher à un window.open. On passe
    // donc par un fetch authentifié + Blob → URL.createObjectURL.
    const url = `${environment.apiUrl}/operations/${op.id}/pdf`;
    const token = localStorage.getItem(environment.tokenKey) ?? '';
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then((r) => r.ok ? r.blob() : Promise.reject(r.statusText))
      .then((blob) => {
        const objectUrl = URL.createObjectURL(blob);
        window.open(objectUrl, '_blank');
        setTimeout(() => URL.revokeObjectURL(objectUrl), 30_000);
      })
      .catch(() => alert("Impossible de récupérer le reçu PDF."));
  }

  private toIso(d: Date): string {
    if (!d) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const j = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${j}`;
  }

  classForStatut(statut: string | undefined): string {
    switch (statut) {
      case 'OUVERTE':   return 'statut-ouverte';
      case 'SUSPENDUE': return 'statut-suspendue';
      default:          return 'statut-fermee';
    }
  }
}
