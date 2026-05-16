import { CurrencyPipe, DatePipe, DecimalPipe, NgClass } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { Caisse, DashboardResponse } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CaisseService } from '../../core/services/admin.services';
import { ReportingService } from '../../core/services/caisse.services';

@Component({
  selector: 'rts-dashboard',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    NgClass,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    BaseChartDirective
  ],
  providers: [provideCharts(withDefaultRegisterables())],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  private readonly reporting = inject(ReportingService);
  private readonly auth      = inject(AuthService);
  private readonly caisseApi = inject(CaisseService);

  /** Pour l'AGENT_RECETTE : id de sa caisse affectee, lockee = on ne propose
   *  pas le selecteur de caisse. Null pour les autres roles = "Toutes les caisses". */
  private maCaisseId: number | null = null;

  readonly loading = signal(false);
  readonly data = signal<DashboardResponse | null>(null);

  /** Liste des caisses pour le selecteur (ADMIN / SUPERVISEUR / CAISSIER). */
  readonly caisses = signal<Caisse[]>([]);
  /** Caisse selectionnee dans le filtre ; null = "Toutes les caisses". */
  caisseSelectionneeId: number | null = null;
  /** L'utilisateur peut-il choisir une caisse ? (Non pour AGENT_RECETTE.) */
  readonly peutChoisirCaisse = computed(() =>
      this.auth.currentRole() !== 'AGENT_RECETTE');

  /** Bornes de la plage de dates (incluses). Par défaut : aujourd'hui → aujourd'hui. */
  dateDebut: Date = new Date();
  dateFin: Date   = new Date();

  readonly colonnesCaisse = ['code', 'libelle', 'entrees', 'sorties', 'solde'];
  readonly colonnesCategorie = ['code', 'libelle', 'type', 'nombre', 'montant'];

  // Options des graphiques
  readonly doughnutOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { padding: 16, font: { size: 12 } } }
    }
  };

  readonly barOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { padding: 16, font: { size: 12 } } }
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          callback: (value) => new Intl.NumberFormat('fr-FR').format(value as number)
        }
      }
    }
  };

  // Données réactives des graphiques (computed)
  readonly doughnutData = computed<ChartData<'doughnut'>>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };
    return {
      labels: d.repartitionParCaisse.map((c) => c.libelleCaisse),
      datasets: [
        {
          data: d.repartitionParCaisse.map((c) => c.entrees),
          backgroundColor: ['#E30613', '#1a1a1a', '#B30510', '#7A030B', '#F47A82', '#4a4a4a'],
          borderWidth: 2,
          borderColor: '#fff'
        }
      ]
    };
  });

  readonly barData = computed<ChartData<'bar'>>(() => {
    const d = this.data();
    if (!d) return { labels: [], datasets: [] };

    const labels = d.repartitionParCategorie.map((c) => c.codeCategorie);
    const entrees = d.repartitionParCategorie.map((c) =>
      c.typeOperation === 'ENTREE' ? c.montantTotal : 0
    );
    const sorties = d.repartitionParCategorie.map((c) =>
      c.typeOperation === 'SORTIE' ? c.montantTotal : 0
    );

    return {
      labels,
      datasets: [
        { label: 'Entrées', data: entrees, backgroundColor: '#2e7d32' },
        { label: 'Sorties', data: sorties, backgroundColor: '#c62828' }
      ]
    };
  });

  ngOnInit(): void {
    // On charge la liste des caisses pour alimenter le selecteur.
    // AGENT_RECETTE : on lock sur sa caisse (caisseSelectionneeId = maCaisseId).
    // Autres roles : selecteur libre (par defaut null = "Toutes les caisses").
    this.caisseApi.lister().subscribe({
      next: (toutes) => {
        if (this.auth.currentRole() === 'AGENT_RECETTE') {
          const userId = this.auth.currentUser()?.utilisateurId;
          const ma = toutes.find(c => c.agentRecetteId === userId);
          this.maCaisseId = ma ? ma.id : null;
          this.caisseSelectionneeId = this.maCaisseId;
          this.caisses.set(ma ? [ma] : []);
        } else {
          this.caisses.set(toutes);
        }
        this.charger();
      },
      error: () => this.charger()
    });
  }

  charger(): void {
    this.loading.set(true);
    // Si l'utilisateur a inversé les bornes, on les remet dans l'ordre avant l'appel
    let debut = this.dateDebut;
    let fin   = this.dateFin;
    if (debut && fin && fin < debut) {
      [debut, fin] = [fin, debut];
    }
    const isoDebut = debut ? this.toIso(debut) : undefined;
    const isoFin   = fin   ? this.toIso(fin)   : undefined;
    // Priorite : AGENT_RECETTE lock sur sa caisse, sinon valeur du selecteur,
    // sinon null = toutes les caisses (pas de filtre).
    const caisseId = this.maCaisseId
        ?? this.caisseSelectionneeId
        ?? undefined;
    this.reporting.dashboard(isoDebut, isoFin, caisseId).subscribe({
      next: (resp) => {
        this.data.set(resp);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  /** Convertit en yyyy-MM-dd dans le fuseau local (évite le décalage UTC). */
  private toIso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const j = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${j}`;
  }

  rafraichir(): void {
    this.charger();
  }
}