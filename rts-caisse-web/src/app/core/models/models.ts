// ============================================================
//  Types miroirs des DTOs Spring Boot (sn.rts.caisse.dto)
// ============================================================

export type Role =
  | 'ADMIN'
  | 'SUPERVISEUR'
  | 'CAISSIER'
  | 'AGENT_RECETTE'
  | 'CONTROLEUR'
  | 'CHEF_UNITE_FINANCES'
  | 'CHEF_DEPARTEMENT';

export type TypeOperation = 'ENTREE' | 'SORTIE';

/** Type(s) d'opération qu'une caisse est autorisée à effectuer. */
export type TypeOperationAutorise = 'ENTREE' | 'SORTIE' | 'TOUS';

export type ModePaiement =
  | 'ESPECES'
  | 'CHEQUE'
  | 'VIREMENT'
  | 'CARTE_BANCAIRE'
  | 'WAVE'
  | 'ORANGE_MONEY'
  | 'FREE_MONEY';

export type StatutCaisse = 'FERMEE' | 'OUVERTE' | 'SUSPENDUE';

/**
 * Configuration personnalisable du timbre fiscal (singleton, ADMIN).
 * Le timbre s'applique si actif, montant >= seuil, mode concerné
 * (modesPaiement vide = tous) et catégorie concernée (categorieIds vide
 * = toutes). Montant = montant * pourcentage / 100.
 */
export type ModeTimbre = 'AUTO' | 'MANUEL';

export interface TimbreConfig {
  actif: boolean;
  seuil: number;
  pourcentage: number;
  categorieIds: number[];
  modesPaiement: ModePaiement[];
  /** AUTO = calcul automatique ; MANUEL = saisie par le caissier (par caisse). */
  mode?: ModeTimbre;
}

// ---------- Auth ----------
export interface LoginRequest {
  login: string;
  motDePasse: string;
}

export interface AuthResponse {
  token: string;
  type: string;
  expiresInMs: number;
  utilisateurId: number;
  matricule: string;
  login: string;
  nomComplet: string;
  role: Role;
}

export interface RegisterRequest {
  matricule: string;
  login: string;
  motDePasse: string;
  prenom: string;
  nom: string;
  email?: string;
  telephone?: string;
  role: Role;
}

// ---------- Utilisateur ----------
export interface Utilisateur {
  id: number;
  matricule: string;
  login: string;
  prenom: string;
  nom: string;
  email?: string;
  telephone?: string;
  role: Role;
  actif: boolean;
}

// ---------- Caisse ----------
export interface Caisse {
  id: number;
  code: string;
  libelle: string;
  emplacement?: string;
  statut: StatutCaisse;
  soldeCourant: number;
  /** Type(s) d'opération autorisé(s) sur la caisse (défini par l'ADMIN). */
  typeOperationAutorise?: TypeOperationAutorise;
  caissierId?: number;
  caissierNomComplet?: string;
  /** Agent de recette rattaché : peut modifier/réactiver les opérations. */
  agentRecetteId?: number;
  agentRecetteNomComplet?: string;
}

// ---------- Catégorie ----------
export interface CategorieOperation {
  id: number;
  code: string;
  libelle: string;
  typeOperation: TypeOperation;
  actif: boolean;
  /** True si les opérations de cette catégorie peuvent porter un
   *  justificatif (PDF/JPG/PNG) joint par le caissier. */
  accepteJustificatif: boolean;
  /** True si le produit propose le choix d'une langue de diffusion
   *  (ex. Avis & Communiqués). Sélection optionnelle au guichet. */
  proposeLangue: boolean;
}

// ---------- Langue de diffusion ----------
export interface Langue {
  id?: number;
  code: string;
  libelle: string;
  actif: boolean;
}

// ---------- Créneau de diffusion (date/heure + langue) ----------
export interface DiffusionSlot {
  /** ISO 8601 datetime. */
  dateHeure: string;
  langueId?: number | null;
  langueLibelle?: string | null;
}

// ---------- Client ----------
export interface Client {
  id: number;
  raisonSociale: string;
  identifiantFiscal?: string;
  telephone?: string;
  email?: string;
  adresse?: string;
  actif: boolean;
}

// ---------- Opération ----------
export interface OperationCaisseRequest {
  caisseId: number;
  categorieId: number;
  clientId?: number;
  typeOperation: TypeOperation;
  montant: number;
  /** Timbre fiscal optionnel. Si null/0, montantTtc = montant. */
  timbre?: number;
  /** true = timbre saisi manuellement (valeur `timbre` utilisée telle quelle) ;
   *  false/absent = timbre calculé automatiquement par le backend. */
  timbreManuel?: boolean;
  modePaiement: ModePaiement;
  motif: string;
  reference?: string;
  banqueId?: number;
  /**
   * Date+heure prévue de diffusion du produit à l'antenne (spot, sponsoring,
   * message). Optionnel : laissé vide quand l'opération n'est pas liée à
   * une diffusion. Format ISO 8601 attendu par le backend.
   */
  dateDiffusion?: string | null;
  /** Créneaux de diffusion multiples (date/heure + langue optionnelle). */
  diffusions?: DiffusionSlot[];
}

// ──────────────────────────────
export interface Banque {
  id?: number;
  code: string;
  libelle: string;
  pays: string;
  codeEtablissement?: string | null;
  siteInternet?: string | null;
  actif: boolean;
}

export interface OperationCaisse {
  id: number;
  numeroRecu: string;
  typeOperation: TypeOperation;
  montant: number;
  /** Timbre fiscal (0 si non applicable). */
  timbre: number;
  /** Montant TTC = montant + timbre. Calculé serveur. */
  montantTtc: number;
  motif: string;
  modePaiement: ModePaiement;
  reference?: string;
  dateOperation: string;
  /** Date+heure de diffusion du produit a l'antenne (optionnel, 1er créneau). */
  dateDiffusion?: string | null;
  /** Créneaux de diffusion (date/heure + langue). */
  diffusions?: DiffusionSlot[];
  /** Nombre de créneaux de diffusion. */
  nombreDiffusions?: number;
  caisseId: number;
  caisseLibelle: string;
  caissierId: number;
  caissierNomComplet: string;
  categorieId: number;
  categorieLibelle: string;
  clientId?: number;
  clientRaisonSociale?: string;
  banqueId?: number;
  banqueCode?: string;
  banqueLibelle?: string;
  annulee: boolean;
  motifAnnulation?: string;
  /** Indique qu'un justificatif (PDF/image) est attache a l'operation. */
  justificatifPresent?: boolean;
  justificatifNomFichier?: string;
  justificatifTypeMime?: string;
  justificatifTailleFichier?: number;
}

// ---------- Versements bancaires ----------
export interface Versement {
  id: number;
  caisseId: number;
  caisseLibelle: string;
  journalId?: number;
  banqueId: number;
  banqueCode: string;
  banqueLibelle: string;
  montant: number;
  numeroBordereau: string;
  /** ISO 8601 datetime. */
  dateVersement: string;
  nomFichier: string;
  typeMime: string;
  tailleFichier: number;
  createdById: number;
  createdByNom: string;
  /** ISO 8601 datetime. */
  createdAt: string;
  notes?: string;
}

// ---------- Journal ----------
export interface OuvertureCaisseRequest {
  fondOuverture: number;
}

export interface ClotureCaisseRequest {
  soldeReel: number;
  commentaire?: string;
}

export interface JournalCaisse {
  id: number;
  dateJournal: string;
  caisseId: number;
  caisseLibelle: string;
  caissierId: number;
  caissierNomComplet: string;
  fondOuverture: number;
  totalEntrees: number;
  totalSorties: number;
  soldeTheorique: number;
  soldeReel?: number;
  ecart?: number;
  commentaire?: string;
  ouvertLe: string;
  clotureLe?: string;
  cloture: boolean;
  valideeParId?: number;
  valideeParNom?: string;
}

// ---------- Dashboard ----------
export interface LigneCaisse {
  caisseId: number;
  codeCaisse: string;
  libelleCaisse: string;
  entrees: number;
  sorties: number;
  solde: number;
}

export interface LigneCategorie {
  categorieId: number;
  codeCategorie: string;
  libelleCategorie: string;
  typeOperation: string;
  montantTotal: number;
  nombre: number;
}

export interface DashboardResponse {
  dateDebut: string;
  dateFin: string;
  totalEntreesJour: number;
  totalSortiesJour: number;
  soldeNetJour: number;
  nombreOperations: number;
  nombreCaissesOuvertes: number;
  repartitionParCaisse: LigneCaisse[];
  repartitionParCategorie: LigneCategorie[];
}

// ---------- Paramètres du reçu ----------
export interface SectionRecu {
  id: string;
  visible: boolean;
}

export interface ParametresRecu {
  // En-tête
  logoTexte: string;
  raisonSociale: string;
  sousTitreEntete: string;
  ligneLegale: string;
  capital: string;
  adresse: string;
  telephone: string;
  boitePostale: string;
  ninea: string;

  // Footer
  footerLigne1: string;
  footerLigne2: string;
  villeSignature: string;

  // Couleurs (hex #RRGGBB)
  couleurPrimaire: string;
  couleurAccent: string;
  couleurTexte: string;
  couleurTexteSecondaire: string;
  couleurSuccess: string;
  couleurDanger: string;
  couleurFondMontant: string;

  // Tailles (pt)
  tailleTitre: number;
  tailleEntete: number;
  tailleCorps: number;
  tailleMontant: number;
  tailleFooter: number;

  // Layout
  sections: SectionRecu[];

  /** True si un logo image a été uploadé côté backend. */
  logoPresent: boolean;
}

// ---------- Supervision (vue temps réel responsable des caisses) ----------
export interface EtatCaisseSupervision {
  id: number;
  code: string;
  libelle: string;
  statut: StatutCaisse;
  caissierNomComplet: string | null;
  soldeCourant: number;
  nombreOperationsJour: number;
  totalEntreesJour: number;
  totalSortiesJour: number;
  derniereOperationLe: string | null;
}

export interface ActiviteRecente {
  operationId: number;
  numeroRecu: string;
  dateOperation: string;
  caisseCode: string;
  caissierNom: string;
  typeOperation: TypeOperation;
  montantTtc: number;
  categorieLibelle: string;
  clientRaisonSociale: string | null;
  annulee: boolean;
}

export interface SupervisionSnapshot {
  horodatage: string;
  totalCaisses: number;
  caissesOuvertes: number;
  totalEntreesJour: number;
  totalSortiesJour: number;
  soldeNetJour: number;
  caisses: EtatCaisseSupervision[];
  activiteRecente: ActiviteRecente[];
}

// ---------- Recettes (ventilation hebdomadaire + double validation) ----------
export type StatutRecette = 'BROUILLON' | 'CONTROLE_1' | 'VALIDEE';

export interface LigneVentilation {
  produitCode: string;
  produitLibelle: string;
  montantHt: number;
  timbre: number;
  montantTtc: number;
}

export interface FicheReference {
  numeroRecu: string;
  /** ISO 8601 datetime. */
  date: string;
  montant: number;
}

export interface ReversementLigne {
  /** ISO 8601 datetime. */
  date: string;
  refBordereau: string;
  montant: number;
}

export interface VentilationRecette {
  recetteId: number;
  caisseId: number;
  caisseCode: string;
  caisseLibelle: string;
  /** ISO LocalDate (yyyy-MM-dd). */
  dateDebut: string;
  dateFin: string;
  statut: StatutRecette;
  controle1ParId?: number;
  controle1ParNom?: string;
  controle1Le?: string;
  controle2ParId?: number;
  controle2ParNom?: string;
  controle2Le?: string;
  lignes: LigneVentilation[];
  totalHt: number;
  totalTimbre: number;
  totalTtc: number;
  fiches: FicheReference[];
  reversements: ReversementLigne[];
  totalReversements: number;
}

export interface LigneCaisseRecette {
  caisseId: number;
  caisseCode: string;
  caisseLibelle: string;
  totalHt: number;
  totalTimbre: number;
  totalTtc: number;
  nbOperations: number;
}

export interface ConsolidationRecette {
  dateDebut: string;
  dateFin: string;
  parProduit: LigneVentilation[];
  totalHt: number;
  totalTimbre: number;
  totalTtc: number;
  parCaisse: LigneCaisseRecette[];
}

// ---------- Pagination Spring ----------
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
