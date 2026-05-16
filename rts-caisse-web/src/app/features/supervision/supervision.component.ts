import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';
import { SupervisionSnapshot } from '../../core/models/models';
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
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    RouterLink
  ],
  templateUrl: './supervision.component.html',
  styleUrls: ['./supervision.component.css']
})
export class SupervisionComponent implements OnInit, OnDestroy {
  private readonly api = inject(SupervisionService);

  /** Intervalle de rafraîchissement automatique (ms). */
  private static readonly POLLING_INTERVAL_MS = 10_000;

  readonly snapshot = signal<SupervisionSnapshot | null>(null);
  readonly loading = signal(true);
  readonly errorMsg = signal<string | null>(null);

  /** Compte à rebours visuel jusqu'au prochain refresh (en secondes). */
  readonly secondsUntilRefresh = signal(SupervisionComponent.POLLING_INTERVAL_MS / 1000);

  private pollingSub?: Subscription;
  private countdownSub?: Subscription;

  ngOnInit(): void {
    // Polling principal toutes les 10s, démarré immédiatement
    this.pollingSub = interval(SupervisionComponent.POLLING_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.api.snapshot())
      )
      .subscribe({
        next: (s) => {
          this.snapshot.set(s);
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

  ngOnDestroy(): void {
    this.pollingSub?.unsubscribe();
    this.countdownSub?.unsubscribe();
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
