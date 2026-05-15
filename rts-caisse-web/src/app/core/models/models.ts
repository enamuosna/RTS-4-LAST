// ============================================================
//  Types miroirs des DTOs Spring Boot (sn.rts.caisse.dto)
// ============================================================

export type Role = 'ADMIN' | 'SUPERVISEUR' | 'CAISSIER';

export type TypeOperation = 'ENTREE' | 'SORTIE';

export type ModePaiement =
  | 'ESPECES'
  | 'CHEQUE'
  | 'VIREMENT'
  | 'CARTE_BANCAIRE'
  | 'WAVE'
  | 'ORANGE_MONEY'
  | 'FREE_MONEY';

export type StatutCaisse = 'FERMEE' | 'OUVERTE' | 'SUSPENDUE';

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
  caissierId?: number;
  caissierNomComplet?: string;
}

// ---------- Catégorie ----------
export interface CategorieOperation {
  id: number;
  code: string;
  libelle: string;
  typeOperation: TypeOperation;
  actif: boolean;
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
  modePaiement: ModePaiement;
  motif: string;
  reference?: string;
  banqueId?: number;
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
