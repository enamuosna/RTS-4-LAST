import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { SupervisionSnapshot } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { CaisseService } from '../../core/services/admin.services';
import { SupervisionService } from '../../core/services/caisse.services';

/**
 * Vue temps réel pour le Responsable des caisses RTS.
 *
 * <p>Polling toutes les 10 secondes du backend. Affiche :
 * <ul>
 *   <li>4 KPI globaux : total caisses ouvertes, entrées/sorties/net du jour</li>
 *   <li>Une carte par caisse (toutes), avec statut, caissier, solde, dernière op</li>
 *   <li>Le flux des 20 dernières opérations toutes caisses confondues</li>
 * </ul>
 */
@Component({
  selector: 'rts-supervision',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    RouterLink
  ],
  templateUrl: './supervision.component.html',
  styleUrls: ['./supervision.component.css']
})
export class SupervisionComponent implements OnInit, OnDestroy {
  private readonly api       = inject(SupervisionService);
  private readonly auth      = inject(AuthService);
  private readonly caisseApi = inject(CaisseService);

  /** Intervalle de rafraîchissement automatique (ms). */
  private static readonly POLLING_INTERVAL_MS = 10_000;

  readonly snapshot = signal<SupervisionSnapshot | null>(null);
  readonly loading = signal(true);
  readonly errorMsg = signal<string | null>(null);

  /** Compte à rebours visuel jusqu'au prochain refresh (en secondes). */
  readonly secondsUntilRefresh = signal(SupervisionComponent.POLLING_INTERVAL_MS / 1000);

  /** IDs et codes des caisses affectees a l'AGENT_RECETTE connecte.
   *  Vide pour ADMIN / SUPERVISEUR (= pas de filtre, on voit tout). */
  private mesCaisseIds:   Set<number> = new Set<number>();
  private mesCaisseCodes: Set<string> = new Set<string>();

  // Filtre periode. Par defaut today/today => mode live (avec polling).
  // Si l'utilisateur change les dates, on bascule en mode historique
  // (polling stoppe pour ne pas spammer le backend).
  dateDebut: Date = new Date();
  dateFin:   Date = new Date();
  readonly modeHistorique = signal<boolean>(false);

  private pollingSub?: Subscription;
  private countdownSub?: Subscription;

  ngOnInit(): void {
    // Pour l'AGENT_RECETTE on doit limiter la vue a sa propre caisse :
    // on charge d'abord la liste des caisses pour identifier "mes caisses"
    // (celles ou agentRecetteId = userId), puis on demarre le polling.
    const role = this.auth.currentRole();
    if (role === 'AGENT_RECETTE') {
      const userId = this.auth.currentUser()?.utilisateurId;
      this.caisseApi.lister().subscribe({
        next: (toutes) => {
          const mesCaisses = toutes.filter(c => c.agentRecetteId === userId);
          this.mesCaisseIds   = new Set(mesCaisses.map(c => c.id));
          this.mesCaisseCodes = new Set(mesCaisses.map(c => c.code));
          this.demarrerPolling();
        },
        error: () => this.demarrerPolling()  // fallback : on tente quand meme
      });
    } else {
      this.demarrerPolling();
    }
  }

  private demarrerPolling(): void {
    // Polling principal toutes les 10s, démarré immédiatement (mode live).
    this.pollingSub = interval(SupervisionComponent.POLLING_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.api.snapshot())
      )
      .subscribe({
        next: (s) => {
          this.snapshot.set(this.filtrerPourAgent(s));
          this.loading.set(false);
          this.errorMsg.set(null);
          this.secondsUntilRefresh.set(SupervisionComponent.POLLING_INTERVAL_MS / 1000);
        },
        error: (err) => {
          this.loading.set(false);
          this.errorMsg.set(err?.error?.message ?? 'Snapshot indisponible.');
        }
      });

    // Compte à rebours visuel chaque seconde
    this.countdownSub = interval(1000).subscribe(() => {
      const v = this.secondsUntilRefresh();
      this.secondsUntilRefresh.set(v <= 1
        ? SupervisionComponent.POLLING_INTERVAL_MS / 1000
        : v - 1);
    });
  }

  /**
   * Bascule en mode historique : stoppe le polling et fait un seul appel
   * snapshot avec les dates choisies. La page n'est plus rafraichie auto
   * tant que l'utilisateur n'a pas relance le mode live.
   */
  appliquerFiltre(): void {
    let debut = this.dateDebut, fin = this.dateFin;
    if (debut && fin && fin < debut) [debut, fin] = [fin, debut];
    this.modeHistorique.set(true);
    this.pollingSub?.unsubscribe();
    this.countdownSub?.unsubscribe();
    this.loading.set(true);
    this.api.snapshot(this.toIso(debut), this.toIso(fin)).subscribe({
      next: (s) => {
        this.snapshot.set(this.filtrerPourAgent(s));
        this.loading.set(false);
        this.errorMsg.set(null);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMsg.set(err?.error?.message ?? 'Snapshot indisponible.');
      }
    });
  }

  /** Repasse en mode live : aujourd'hui + relance du polling. */
  revenirEnDirect(): void {
    this.dateDebut = new Date();
    this.dateFin   = new Date();
    this.modeHistorique.set(false);
    this.pollingSub?.unsubscribe();
    this.countdownSub?.unsubscribe();
    this.demarrerPolling();
  }

  private toIso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const j = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${j}`;
  }

  ngOnDestroy(): void {
    this.pollingSub?.unsubscribe();
    this.countdownSub?.unsubscribe();
  }

  /**
   * Pour l'AGENT_RECETTE : restreint le snapshot a sa caisse affectee.
   * On filtre les caisses par id et l'activite recente par code caisse.
   * On recalcule aussi les agregats du jour pour qu'ils refletent uniquement
   * sa caisse — sinon il verrait "0 F CFA" alors que sa caisse a des operations.
   * Pour les autres roles, on renvoie le snapshot tel quel.
   */
  private filtrerPourAgent(s: SupervisionSnapshot): SupervisionSnapshot {
    if (this.mesCaisseIds.size === 0) return s;
    const caissesFiltrees = s.caisses.filter(c => this.mesCaisseIds.has(c.id));
    const activiteFiltree = s.activiteRecente.filter(a => this.mesCaisseCodes.has(a.caisseCode));
    const totalEntrees = caissesFiltrees.reduce((sum, c) => sum + (Number(c.totalEntreesJour) || 0), 0);
    const totalSorties = caissesFiltrees.reduce((sum, c) => sum + (Number(c.totalSortiesJour) || 0), 0);
    return {
      ...s,
      totalCaisses:     caissesFiltrees.length,
      caissesOuvertes:  caissesFiltrees.filter(c => c.statut === 'OUVERTE').length,
      totalEntreesJour: totalEntrees,
      totalSortiesJour: totalSorties,
      soldeNetJour:     totalEntrees - totalSorties,
      caisses:          caissesFiltrees,
      activiteRecente:  activiteFiltree
    };
  }

  // ==================================================================
  //  Helpers d'affichage
  // ==================================================================

  /** Couleur de la pastille statut. */
  classForStatut(statut: string): string {
    switch (statut) {
      case 'OUVERTE':   return 'statut-ouverte';
      case 'SUSPENDUE': return 'statut-suspendue';
      default:          return 'statut-fermee';
    }
  }

  /** Format relatif "il y a 3 min" pour la dernière opération. */
  formatRelatif(iso: string | null): string {
    if (!iso) return 'Aucune opération';
    const now = Date.now();
    const ts  = new Date(iso).getTime();
    const sec = Math.max(0, Math.round((now - ts) / 1000));
    if (sec < 60) return `il y a ${sec}s`;
    const min = Math.round(sec / 60);
    if (min < 60) return `il y a ${min} min`;
    const h = Math.round(min / 60);
    return `il y a ${h} h`;
  }
}
