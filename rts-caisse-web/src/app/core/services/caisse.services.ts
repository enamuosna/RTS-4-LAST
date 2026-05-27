import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  ClotureCaisseRequest,
  DashboardResponse,
  JournalCaisse,
  OperationCaisse,
  OperationCaisseRequest,
  OuvertureCaisseRequest,
  Page,
  ParametresRecu,
  SupervisionSnapshot,
  Versement
} from '../models/models';

// ======================================================
//  OPERATIONS DE CAISSE
// ======================================================
@Injectable({ providedIn: 'root' })
export class OperationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/operations`;

  enregistrer(request: OperationCaisseRequest): Observable<OperationCaisse> {
    return this.http.post<OperationCaisse>(this.base, request);
  }

  /** Modifie une opération existante. Recalcule le solde côté serveur. */
  modifier(id: number, request: OperationCaisseRequest): Observable<OperationCaisse> {
    return this.http.put<OperationCaisse>(`${this.base}/${id}`, request);
  }

  annuler(id: number, motif: string): Observable<OperationCaisse> {
    return this.http.patch<OperationCaisse>(`${this.base}/${id}/annuler`, null, {
      params: new HttpParams().set('motif', motif)
    });
  }

  /** Réactive une opération annulée par erreur (annule la contre-passation). */
  reactiver(id: number): Observable<OperationCaisse> {
    return this.http.patch<OperationCaisse>(`${this.base}/${id}/reactiver`, null);
  }

  obtenir(id: number): Observable<OperationCaisse> {
    return this.http.get<OperationCaisse>(`${this.base}/${id}`);
  }

  historiqueParCaisse(
    caisseId: number,
    page = 0,
    size = 20,
    dateDebut?: string,
    dateFin?: string
  ): Observable<Page<OperationCaisse>> {
    let params = new HttpParams()
        .set('page', page)
        .set('size', size)
        .set('sort', 'dateOperation,desc');
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin)   params = params.set('dateFin', dateFin);
    return this.http.get<Page<OperationCaisse>>(`${this.base}/caisse/${caisseId}`, { params });
  }

  historiqueDuJour(caisseId: number): Observable<OperationCaisse[]> {
    return this.http.get<OperationCaisse[]>(`${this.base}/caisse/${caisseId}/jour`);
  }

  // ---------- Justificatif (PDF/JPG/PNG) ----------

  /** Attache (ou remplace) le justificatif d'une opération. */
  uploaderJustificatif(operationId: number, fichier: File): Observable<OperationCaisse> {
    const formData = new FormData();
    formData.append('fichier', fichier);
    return this.http.post<OperationCaisse>(
      `${this.base}/${operationId}/justificatif`, formData);
  }

  /** Telecharge le justificatif via JSON+base64 (bypass tracking blockers). */
  telechargerJustificatif(operationId: number): Observable<{
    nomFichier: string; typeMime: string; blob: Blob;
  }> {
    return this.http.get<{
      nomFichier: string;
      typeMime: string;
      tailleFichier: number;
      contenuBase64: string;
    }>(`${this.base}/${operationId}/justificatif-donnees`).pipe(
      map(payload => {
        const bin = atob(payload.contenuBase64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        return {
          nomFichier: payload.nomFichier,
          typeMime:   payload.typeMime,
          blob:       new Blob([bytes], { type: payload.typeMime })
        };
      })
    );
  }

  supprimerJustificatif(operationId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${operationId}/justificatif`);
  }

  // ---------- Purge (suppression définitive — ADMIN uniquement) ----------

  /** Supprime DÉFINITIVEMENT une opération (contre-pass auto si nécessaire). */
  supprimerDefinitivement(id: number): Observable<PurgeResult> {
    return this.http.delete<PurgeResult>(`${this.base}/${id}/definitif`);
  }

  /** Prévisualise l'impact d'une purge (compteur, sommes, dates). */
  previewPurge(filter: PurgeFilter): Observable<PurgePreviewResponse> {
    return this.http.post<PurgePreviewResponse>(`${this.base}/purger/preview`, filter);
  }

  /** Télécharge le CSV des opérations qui seraient supprimées. */
  exporterPurgeCsv(filter: PurgeFilter): Observable<Blob> {
    return this.http.post(`${this.base}/purger/csv`, filter, {
      responseType: 'blob'
    });
  }

  /** Exécute la purge en masse selon le filtre. */
  purgerEnMasse(filter: PurgeFilter): Observable<PurgeResult> {
    return this.http.post<PurgeResult>(`${this.base}/purger`, filter);
  }
}

// ----- Types liés à la purge (mirror des DTOs backend) -----

export type PurgeStatut = 'TOUS' | 'SEULEMENT_ANNULEES' | 'SEULEMENT_ACTIVES';

export interface PurgeFilter {
  caisseId?: number | null;
  avantDate: string;   // ISO LocalDate (yyyy-MM-dd)
  statut: PurgeStatut;
}

export interface PurgePreviewResponse {
  total: number;
  nbAnnulees: number;
  nbActives: number;
  nbDansCloture: number;
  sommeEntrees: number;
  sommeSorties: number;
  plusAncienne: string | null;
  plusRecente: string | null;
}

export interface PurgeResult {
  nbSupprimees: number;
  nbContrepassees: number;
  nbEchecs: number;
  erreurs: string[];
}

// ======================================================
//  JOURNAL DE CAISSE
// ======================================================
@Injectable({ providedIn: 'root' })
export class JournalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/journaux`;

  ouvrir(caisseId: number, request: OuvertureCaisseRequest): Observable<JournalCaisse> {
    return this.http.post<JournalCaisse>(`${this.base}/caisse/${caisseId}/ouvrir`, request);
  }

  cloturer(caisseId: number, request: ClotureCaisseRequest): Observable<JournalCaisse> {
    return this.http.post<JournalCaisse>(`${this.base}/caisse/${caisseId}/cloturer`, request);
  }

  valider(journalId: number): Observable<JournalCaisse> {
    return this.http.post<JournalCaisse>(`${this.base}/${journalId}/valider`, null);
  }

  obtenir(id: number): Observable<JournalCaisse> {
    return this.http.get<JournalCaisse>(`${this.base}/${id}`);
  }

  parCaisse(caisseId: number, dateDebut?: string, dateFin?: string): Observable<JournalCaisse[]> {
    let params = new HttpParams();
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin)   params = params.set('dateFin',   dateFin);
    return this.http.get<JournalCaisse[]>(`${this.base}/caisse/${caisseId}`, { params });
  }

  duJour(date?: string): Observable<JournalCaisse[]> {
    let params = new HttpParams();
    if (date) params = params.set('date', date);
    return this.http.get<JournalCaisse[]>(`${this.base}/jour`, { params });
  }

  /**
   * Recherche unifiee : plage de dates + caisse optionnelle.
   * Sans caisseId -> toutes les caisses. Sans dates -> aujourd'hui.
   */
  lister(dateDebut?: string, dateFin?: string, caisseId?: number): Observable<JournalCaisse[]> {
    let params = new HttpParams();
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin)   params = params.set('dateFin',   dateFin);
    if (caisseId)  params = params.set('caisseId',  caisseId);
    return this.http.get<JournalCaisse[]>(this.base, { params });
  }

  /**
   * Variante paginee : meme criteres mais avec pagination admin web.
   * Si dateDebut/Fin sont absents, le backend prend une fenetre d'1 an.
   */
  listerPaginee(opts?: {
    dateDebut?: string; dateFin?: string; caisseId?: number;
    page?: number; size?: number;
  }): Observable<Page<JournalCaisse>> {
    let params = new HttpParams()
      .set('page', opts?.page ?? 0)
      .set('size', opts?.size ?? 20)
      .set('sort', 'dateJournal,desc');
    if (opts?.dateDebut) params = params.set('dateDebut', opts.dateDebut);
    if (opts?.dateFin)   params = params.set('dateFin',   opts.dateFin);
    if (opts?.caisseId)  params = params.set('caisseId',  opts.caisseId);
    return this.http.get<Page<JournalCaisse>>(`${this.base}/page`, { params });
  }

  /**
   * Exporte un journal au format Excel.
   *
   * Problème Angular : quand `responseType: 'blob'` est actif, les réponses
   * d'ERREUR arrivent elles aussi sous forme de Blob au lieu de JSON.
   * L'intercepteur global ne sait pas les lire et affiche "Erreur inattendue".
   *
   * Solution : on intercepte l'erreur ici, on lit le Blob comme du texte,
   * on reparse le JSON du serveur, puis on relance un HttpErrorResponse
   * correctement formé que l'intercepteur global sait traiter.
   */
  exporterExcel(journalId: number): Observable<HttpResponse<Blob>> {
    return this.http
      .get(`${this.base}/${journalId}/export/excel`, {
        responseType: 'blob',
        observe: 'response'
      })
      .pipe(catchError((error: HttpErrorResponse) => this.parseBlobError(error)));
  }

  /**
   * Convertit une erreur Blob en HttpErrorResponse lisible par l'intercepteur.
   * Si le corps n'est pas du JSON valide, on retransmet l'erreur d'origine.
   */
  private parseBlobError(error: HttpErrorResponse): Observable<never> {
    if (!(error.error instanceof Blob)) {
      return throwError(() => error);
    }

    return new Observable<never>((observer) => {
      const reader = new FileReader();

      reader.onload = () => {
        let parsedError: unknown;
        try {
          parsedError = JSON.parse(reader.result as string);
        } catch {
          parsedError = { message: reader.result };
        }

        observer.error(
          new HttpErrorResponse({
            error: parsedError,
            headers: error.headers,
            status: error.status,
            statusText: error.statusText,
            url: error.url ?? undefined
          })
        );
      };

      reader.onerror = () => observer.error(error);
      reader.readAsText(error.error);
    });
  }
}

// ======================================================
//  REPORTING
// ======================================================
@Injectable({ providedIn: 'root' })
export class ReportingService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/reporting`;

  /**
   * @param dateDebut format ISO yyyy-MM-dd, facultatif (par défaut aujourd'hui)
   * @param dateFin   format ISO yyyy-MM-dd, facultatif (par défaut = dateDebut)
   */
  dashboard(dateDebut?: string, dateFin?: string,
            caisseId?: number): Observable<DashboardResponse> {
    let params = new HttpParams();
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin)   params = params.set('dateFin', dateFin);
    if (caisseId)  params = params.set('caisseId', caisseId);
    return this.http.get<DashboardResponse>(`${this.base}/dashboard`, { params });
  }
}

// ======================================================
//  PARAMÈTRES DU REÇU PDF
// ======================================================
@Injectable({ providedIn: 'root' })
export class ParametresRecuService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/parametres/recu`;

  obtenir(): Observable<ParametresRecu> {
    return this.http.get<ParametresRecu>(this.base);
  }

  mettreAJour(dto: ParametresRecu): Observable<ParametresRecu> {
    return this.http.put<ParametresRecu>(this.base, dto);
  }

  /**
   * Renvoie l'aperçu rasterisé en PNG. Privilégié à l'aperçu PDF dans
   * l'UI parce que certains navigateurs (Edge Tracking Prevention strict,
   * uBlock, Brave Shields) bloquent les XHR retournant {@code application/pdf}
   * avec ERR_BLOCKED_BY_CLIENT. Une image PNG passe partout.
   */
  obtenirApercuImage(): Observable<Blob> {
    return this.http.get(`${this.base}/image`, { responseType: 'blob' });
  }

  /** Renvoie le rendu PDF — utilisé par le bouton « Télécharger l'aperçu ». */
  obtenirApercu(): Observable<Blob> {
    return this.http.get(`${this.base}/pdf`, { responseType: 'blob' });
  }

  /**
   * Charge le logo binaire. Renvoie un Blob ou null s'il n'y a pas de logo
   * (réponse 404). Le composant transforme ensuite le blob en object URL
   * pour l'afficher dans <img>.
   */
  obtenirLogo(): Observable<Blob | null> {
    return this.http
      .get(`${this.base}/logo`, { responseType: 'blob' })
      .pipe(catchError(() => of<Blob | null>(null)));
  }

  /** Upload du logo (PNG, JPG, GIF, WEBP — 2 Mo max). */
  uploadLogo(file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<void>(`${this.base}/logo`, formData);
  }

  /** Supprime le logo image. Le PDF retombera sur le texte du logo. */
  supprimerLogo(): Observable<void> {
    return this.http.delete<void>(`${this.base}/logo`);
  }
}

// ======================================================
//  SUPERVISION (vue temps réel pour le responsable des caisses)
// ======================================================
@Injectable({ providedIn: 'root' })
export class SupervisionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/supervision`;

  /**
   * Snapshot complet : caisses + agregats + flux d'activite.
   * @param dateDebut/dateFin optionnels au format ISO yyyy-MM-dd ;
   * sans dates -> vue temps reel du jour (polling), avec dates -> vue historique.
   */
  snapshot(dateDebut?: string, dateFin?: string): Observable<SupervisionSnapshot> {
    let params = new HttpParams();
    if (dateDebut) params = params.set('dateDebut', dateDebut);
    if (dateFin)   params = params.set('dateFin',   dateFin);
    return this.http.get<SupervisionSnapshot>(`${this.base}/snapshot`, { params });
  }
}

// ======================================================
//  SAUVEGARDE (export / import BDD)
// ======================================================

export interface RapportImportBackup {
  succes: boolean;
  tailleOctets: number;
  message: string;
  dernieresLignesPsql: string[];
}

@Injectable({ providedIn: 'root' })
export class BackupService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/backup`;

  /** Télécharge un dump SQL complet de la BDD. */
  exporter(): Observable<Blob> {
    return this.http.get(`${this.base}/export`, { responseType: 'blob' });
  }

  /** Restaure la BDD depuis un dump SQL. ÉCRASE les données existantes. */
  importer(file: File): Observable<RapportImportBackup> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<RapportImportBackup>(`${this.base}/import`, formData);
  }
}

// ======================================================
//  VERSEMENTS BANCAIRES
// ======================================================
@Injectable({ providedIn: 'root' })
export class VersementService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/versements`;

  /** Tous les versements (admin/superviseur, pagine). */
  listerTous(page = 0, size = 20): Observable<Page<Versement>> {
    const params = new HttpParams().set('page', page).set('size', size)
      .set('sort', 'dateVersement,desc');
    return this.http.get<Page<Versement>>(this.base, { params });
  }

  /** Versements d'une caisse, filtrable par dates (YYYY-MM-DD). */
  listerParCaisse(caisseId: number, opts?: {
    dateDebut?: string; dateFin?: string; page?: number; size?: number;
  }): Observable<Page<Versement>> {
    let params = new HttpParams()
      .set('page', opts?.page ?? 0)
      .set('size', opts?.size ?? 20)
      .set('sort', 'dateVersement,desc');
    if (opts?.dateDebut) params = params.set('dateDebut', opts.dateDebut);
    if (opts?.dateFin)   params = params.set('dateFin',   opts.dateFin);
    return this.http.get<Page<Versement>>(`${this.base}/caisse/${caisseId}`, { params });
  }

  /** Détail d'un versement (sans le contenu binaire). */
  obtenir(id: number): Observable<Versement> {
    return this.http.get<Versement>(`${this.base}/${id}`);
  }

  /**
   * Téléchargement du bordereau bancaire (PDF ou image).
   *
   * Edge Tracking Prevention bloque les telechargements binaires directs
   * sur DuckDNS (ERR_BLOCKED_BY_CLIENT), meme avec un Bearer token.
   * Solution : passer par un endpoint JSON qui renvoie le fichier en
   * base64. JSON n'est jamais bloque. On reconstruit le Blob cote client.
   */
  telechargerFichier(id: number): Observable<{ nomFichier: string; typeMime: string; blob: Blob }> {
    return this.http.get<{
      nomFichier: string;
      typeMime: string;
      tailleFichier: number;
      contenuBase64: string;
    }>(`${this.base}/${id}/donnees`).pipe(
      map(payload => {
        const bin = atob(payload.contenuBase64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        return {
          nomFichier: payload.nomFichier,
          typeMime:   payload.typeMime,
          blob:       new Blob([bytes], { type: payload.typeMime })
        };
      })
    );
  }

  /**
   * Crée un versement avec upload du bordereau bancaire (multipart).
   * Le fichier doit être PDF, JPG ou PNG, max 5 Mo.
   */
  creer(req: {
    caisseId: number;
    banqueId: number;
    journalId?: number;
    montant: number;
    numeroBordereau: string;
    /** ISO 8601 datetime (optionnel ; backend met now() par défaut). */
    dateVersement?: string;
    notes?: string;
    fichier: File;
  }): Observable<Versement> {
    const params = new HttpParams()
      .set('caisseId',        req.caisseId)
      .set('banqueId',        req.banqueId)
      .set('montant',         req.montant)
      .set('numeroBordereau', req.numeroBordereau)
      .set('journalId',       req.journalId ?? '')
      .set('dateVersement',   req.dateVersement ?? '')
      .set('notes',           req.notes ?? '');
    const formData = new FormData();
    formData.append('fichier', req.fichier);
    return this.http.post<Versement>(this.base, formData, { params });
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
