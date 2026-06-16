package sn.rts.caisse.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sn.rts.caisse.model.*;
import sn.rts.caisse.repository.CaisseRepository;
import sn.rts.caisse.repository.CategorieOperationRepository;
import sn.rts.caisse.repository.ParametresRecuRepository;
import sn.rts.caisse.repository.TimbreConfigRepository;
import sn.rts.caisse.repository.UtilisateurRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Initialise la base au premier démarrage :
 *   - un administrateur par défaut (à changer immédiatement en production)
 *   - les catégories comptables RTS couramment utilisées
 *   - une caisse d'exemple
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UtilisateurRepository utilisateurRepository;
    private final CategorieOperationRepository categorieRepository;
    private final CaisseRepository caisseRepository;
    private final TimbreConfigRepository timbreConfigRepository;
    private final ParametresRecuRepository parametresRecuRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initAdmin();
        initChefsControle();
        initCategories();
        initCaisse();
        initTimbreConfig();
        initParametresRecu();
    }

    // ------------------------------------------------------------------

    private void initAdmin() {
        if (utilisateurRepository.existsByLogin("admin")) {
            return;
        }
        Utilisateur admin = Utilisateur.builder()
                .matricule("RTS-ADMIN-001")
                .login("admin")
                .motDePasse(passwordEncoder.encode("Admin@2026"))
                .prenom("Administrateur")
                .nom("RTS")
                .email("admin@rts.sn")
                .telephone("+221338000000")
                .role(Role.ADMIN)
                .actif(true)
                .build();
        utilisateurRepository.save(admin);
        log.warn("┌──────────────────────────────────────────────────────────────┐");
        log.warn("│  Administrateur par défaut créé                              │");
        log.warn("│  Login    : admin                                            │");
        log.warn("│  Password : Admin@2026                                       │");
        log.warn("│  ** CHANGEZ CE MOT DE PASSE IMMÉDIATEMENT EN PRODUCTION **   │");
        log.warn("└──────────────────────────────────────────────────────────────┘");
    }

    /**
     * Crée deux comptes de démonstration pour les signataires de la ventilation
     * hebdomadaire des recettes : Chef Unité Finances (Contrôle 1) et Chef de
     * Département (Contrôle 2). Idempotent.
     */
    private void initChefsControle() {
        if (!utilisateurRepository.existsByLogin("chef.finances")) {
            utilisateurRepository.save(Utilisateur.builder()
                    .matricule("RTS-CUF-001")
                    .login("chef.finances")
                    .motDePasse(passwordEncoder.encode("Chef@2026"))
                    .prenom("Chef")
                    .nom("Unité Finances")
                    .email("finances@rts.sn")
                    .role(Role.CHEF_UNITE_FINANCES)
                    .actif(true)
                    .build());
            log.warn("Compte de démo créé : chef.finances / Chef@2026 (CHEF_UNITE_FINANCES) — à sécuriser.");
        }
        if (!utilisateurRepository.existsByLogin("chef.departement")) {
            utilisateurRepository.save(Utilisateur.builder()
                    .matricule("RTS-CDEP-001")
                    .login("chef.departement")
                    .motDePasse(passwordEncoder.encode("Chef@2026"))
                    .prenom("Chef")
                    .nom("Département")
                    .email("departement@rts.sn")
                    .role(Role.CHEF_DEPARTEMENT)
                    .actif(true)
                    .build());
            log.warn("Compte de démo créé : chef.departement / Chef@2026 (CHEF_DEPARTEMENT) — à sécuriser.");
        }
    }

    private void initCategories() {
        if (categorieRepository.count() > 0) {
            return;
        }
        // Entrées (encaissements typiques RTS)
        List<CategorieOperation> entrees = List.of(
                cat("REC-PUB", "Recette publicitaire", TypeOperation.ENTREE),
                cat("CESS-DRT", "Cession de droits audiovisuels", TypeOperation.ENTREE),
                cat("VTE-ARCH", "Vente d'archives", TypeOperation.ENTREE),
                cat("LOC-STU", "Location de studio / régie", TypeOperation.ENTREE),
                cat("PRESTA", "Prestation technique externe", TypeOperation.ENTREE),
                cat("PART", "Partenariat / parrainage", TypeOperation.ENTREE)
        );

        // Sorties (décaissements typiques)
        List<CategorieOperation> sorties = List.of(
                cat("MISS", "Frais de mission", TypeOperation.SORTIE),
                cat("CONSO", "Achat de consommables", TypeOperation.SORTIE),
                cat("CARB", "Carburant véhicules", TypeOperation.SORTIE),
                cat("REMB", "Remboursement client", TypeOperation.SORTIE),
                cat("DIV-SORT", "Dépense diverse", TypeOperation.SORTIE),
                cat("PIGE", "Paiement pige / correspondant", TypeOperation.SORTIE)
        );

        categorieRepository.saveAll(entrees);
        categorieRepository.saveAll(sorties);
        log.info("Catégories comptables RTS initialisées ({} entrées + {} sorties).",
                entrees.size(), sorties.size());
    }

    private CategorieOperation cat(String code, String libelle, TypeOperation type) {
        return CategorieOperation.builder()
                .code(code)
                .libelle(libelle)
                .typeOperation(type)
                .actif(true)
                .build();
    }

    private void initCaisse() {
        if (caisseRepository.existsByCode("CAI-01")) {
            return;
        }
        Caisse caisse = Caisse.builder()
                .code("CAI-01")
                .libelle("Caisse principale - Siège RTS Triangle Sud")
                .emplacement("Dakar, Triangle Sud - Bâtiment administratif")
                .statut(StatutCaisse.FERMEE)
                .soldeCourant(BigDecimal.ZERO)
                .build();
        caisseRepository.save(caisse);
        log.info("Caisse d'exemple créée : {} - {}", caisse.getCode(), caisse.getLibelle());
    }

    /**
     * Seed du singleton de configuration du timbre. Indispensable en profil
     * docker (Flyway désactivé, ddl-auto=update) où la table est créée par
     * Hibernate mais aucune ligne n'est insérée. Idempotent : si la migration
     * Flyway (dev) a déjà inséré la ligne id=1, on ne fait rien.
     */
    private void initTimbreConfig() {
        if (timbreConfigRepository.existsById(1L)) {
            return;
        }
        TimbreConfig config = TimbreConfig.builder()
                .id(1L)
                .actif(true)
                .seuil(new BigDecimal("20000"))
                .pourcentage(new BigDecimal("1.00"))
                .categoriesJson("[]")
                .modesPaiementJson("[\"ESPECES\"]")
                .updatedAt(LocalDateTime.now())
                .updatedBy("system")
                .build();
        timbreConfigRepository.save(config);
        log.info("Configuration du timbre initialisée (seuil=20000, taux=1%, ESPECES).");
    }

    /**
     * Seed du singleton de paramètres du reçu (id=1). Indispensable en profil
     * docker (Flyway désactivé, ddl-auto=update) où la table est créée par
     * Hibernate mais aucune ligne n'est insérée — sans cette ligne, l'aperçu
     * du reçu et l'upload du logo échouent ("Paramètres du reçu introuvables").
     * Reprend les valeurs par défaut RTS de la migration V4. Idempotent.
     */
    private void initParametresRecu() {
        if (parametresRecuRepository.existsById(1L)) {
            return;
        }
        ParametresRecu params = ParametresRecu.builder()
                .id(1L)
                .logoTexte("RTS")
                .raisonSociale("SOCIÉTÉ NATIONALE DE RADIODIFFUSION TÉLÉVISION DU SÉNÉGAL")
                .sousTitreEntete("Radiodiffusion Télévision Sénégalaise")
                .ligneLegale("Créée par la loi n° 92-02 du 06 janvier 1992")
                .capital("Capital : 7 milliards FCFA")
                .adresse("Triangle Sud")
                .telephone("Tél. (221) 33 849 12 12")
                .boitePostale("B.P. 1765 — DAKAR")
                .ninea("NINEA : 2059782 2G3")
                .footerLigne1("Merci de votre passage.")
                .footerLigne2("RTS — Conservez ce reçu comme preuve.")
                .villeSignature("Dakar")
                .couleurPrimaire("#E30613")
                .couleurAccent("#1A1A1A")
                .couleurTexte("#212121")
                .couleurTexteSecondaire("#9E9E9E")
                .couleurSuccess("#2E7D32")
                .couleurDanger("#C62828")
                .couleurFondMontant("#FBE5E7")
                .tailleTitre(14)
                .tailleEntete(16)
                .tailleCorps(9)
                .tailleMontant(20)
                .tailleFooter(7)
                // layout null -> RecuPdfService retombe sur sa config granulaire par défaut
                .layoutJson(null)
                .updatedAt(LocalDateTime.now())
                .updatedBy("system")
                .build();
        parametresRecuRepository.save(params);
        log.info("Paramètres du reçu initialisés (singleton id=1, valeurs RTS par défaut).");
    }
}
