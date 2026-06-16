import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ConsolidationRecette, VentilationRecette } from '../models/models';

/**
 * Rubrique « Recettes » : ventilation hebdomadaire des recettes par caisse
 * et double validation (Contrôle 1 / Contrôle 2). Miroir de RecetteController.
 */
@Injectable({ providedIn: 'root' })
export class RecetteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/recettes`;

  /** Génère/charge la ventilation d'une caisse sur une période (DU/AU, ISO yyyy-MM-dd). */
  ventilation(caisseId: number, dateDebut?: string, dateFin?: string): Observable<VentilationRecette> {
    let params = new HttpParams().set('caisseId', caisseId);
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin) params = params.set('dateFin', dateFin);
    return this.http.get<VentilationRecette>(`${this.base}/ventilation`, { params });
  }

  /** Contrôle 1 — Chef Unité Finances. */
  controle1(id: number): Observable<VentilationRecette> {
    return this.http.post<VentilationRecette>(`${this.base}/${id}/controle1`, null);
  }

  /** Contrôle 2 — Chef de Département (validation finale). */
  controle2(id: number): Observable<VentilationRecette> {
    return this.http.post<VentilationRecette>(`${this.base}/${id}/controle2`, null);
  }

  /** PDF de la fiche officielle. */
  pdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' });
  }

  /** Consolidation toutes caisses sur une période (par produit + par caisse). */
  consolidation(dateDebut?: string, dateFin?: string): Observable<ConsolidationRecette> {
    let params = new HttpParams();
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin) params = params.set('dateFin', dateFin);
    return this.http.get<ConsolidationRecette>(`${this.base}/consolidation`, { params });
  }
}
