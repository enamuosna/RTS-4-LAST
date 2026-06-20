package sn.rts.caisse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.TimbreConfigDto;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.model.CaisseTimbreConfig;
import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.ModeTimbre;
import sn.rts.caisse.model.TimbreConfig;
import sn.rts.caisse.repository.CaisseTimbreConfigRepository;
import sn.rts.caisse.repository.TimbreConfigRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Service singleton gérant la configuration personnalisable du timbre
 * fiscal. La table {@code timbre_config} ne contient qu'une seule ligne
 * (id=1), seedée par la migration Flyway (profil dev) et/ou par
 * {@code DataInitializer} (profil docker où Flyway est désactivé).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimbreConfigService {

    private static final Long SINGLETON_ID = 1L;

    private final TimbreConfigRepository repository;
    private final CaisseTimbreConfigRepository caisseRepository;
    private final ObjectMapper objectMapper;

    // ==================================================================
    //  Règlement résolu (utilisé par le calculateur)
    // ==================================================================

    /**
     * Vue "prête à l'emploi" de la configuration : les listes JSON sont
     * désérialisées en ensembles typés.
     *
     * @param categorieIds  catégories concernées ; vide = toutes
     * @param modesPaiement modes de paiement concernés ; vide = tous
     */
    public record Reglement(
            boolean actif,
            BigDecimal seuil,
            BigDecimal pourcentage,
            Set<Long> categorieIds,
            Set<ModePaiement> modesPaiement,
            /** true = mode MANUEL (saisie caissier, calcul auto désactivé). */
            boolean manuel) {
    }

    /** Règlement par défaut (modèle global), mode AUTO. */
    @Transactional(readOnly = true)
    public Reglement obtenirReglement() {
        TimbreConfig e = loadEntity();
        return new Reglement(
                e.isActif(),
                e.getSeuil(),
                e.getPourcentage(),
                Set.copyOf(parseLongs(e.getCategoriesJson())),
                parseModes(e.getModesPaiementJson()),
                false);
    }

    /**
     * Règlement effectif pour une caisse : sa config propre si elle existe,
     * sinon les valeurs par défaut (modèle global, mode AUTO).
     */
    @Transactional(readOnly = true)
    public Reglement obtenirReglement(Long caisseId) {
        CaisseTimbreConfig c = caisseId == null ? null
                : caisseRepository.findByCaisseId(caisseId).orElse(null);
        if (c == null) {
            return obtenirReglement();
        }
        return new Reglement(
                c.isActif(),
                c.getSeuil(),
                c.getPourcentage(),
                Set.copyOf(parseLongs(c.getCategoriesJson())),
                parseModes(c.getModesPaiementJson()),
                c.getMode() == ModeTimbre.MANUEL);
    }

    // ==================================================================
    //  LECTURE / MISE À JOUR (IHM admin)
    // ==================================================================

    @Transactional(readOnly = true)
    public TimbreConfigDto obtenir() {
        return toDto(loadEntity());
    }

    /** Config d'une caisse : la sienne si elle existe, sinon les défauts (mode AUTO). */
    @Transactional(readOnly = true)
    public TimbreConfigDto obtenir(Long caisseId) {
        CaisseTimbreConfig c = caisseId == null ? null
                : caisseRepository.findByCaisseId(caisseId).orElse(null);
        if (c == null) {
            // Pas encore configurée : on renvoie les valeurs par défaut (modèle global)
            // avec mode AUTO, pour pré-remplir le formulaire admin.
            TimbreConfigDto base = toDto(loadEntity());
            return new TimbreConfigDto(base.actif(), base.seuil(), base.pourcentage(),
                    base.categorieIds(), base.modesPaiement(), ModeTimbre.AUTO.name());
        }
        return toDto(c);
    }

    /** Crée ou met à jour la config timbre d'une caisse. */
    @Transactional
    public TimbreConfigDto mettreAJour(Long caisseId, TimbreConfigDto dto, String loginAdmin) {
        if (caisseId == null) {
            throw new BusinessException("caisseId requis pour configurer le timbre d'une caisse.");
        }
        CaisseTimbreConfig c = caisseRepository.findByCaisseId(caisseId)
                .orElseGet(() -> CaisseTimbreConfig.builder().caisseId(caisseId).build());
        c.setActif(dto.actif());
        c.setSeuil(dto.seuil());
        c.setPourcentage(dto.pourcentage());
        c.setCategoriesJson(serialize(dto.categorieIds() == null ? List.of() : dto.categorieIds()));
        c.setModesPaiementJson(serialize(dto.modesPaiement() == null ? List.of() : dto.modesPaiement()));
        c.setMode(parseMode(dto.mode()));
        c.setUpdatedAt(LocalDateTime.now());
        c.setUpdatedBy(loginAdmin);
        caisseRepository.save(c);
        log.info("Config timbre caisse {} mise à jour par {} : mode={} actif={} seuil={} taux={}%",
                caisseId, loginAdmin, c.getMode(), c.isActif(), c.getSeuil(), c.getPourcentage());
        return toDto(c);
    }

    private ModeTimbre parseMode(String mode) {
        if (mode == null || mode.isBlank()) return ModeTimbre.AUTO;
        try {
            return ModeTimbre.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ModeTimbre.AUTO;
        }
    }

    private TimbreConfigDto toDto(CaisseTimbreConfig c) {
        return new TimbreConfigDto(
                c.isActif(),
                c.getSeuil(),
                c.getPourcentage(),
                new ArrayList<>(parseLongs(c.getCategoriesJson())),
                new ArrayList<>(parseModes(c.getModesPaiementJson())),
                c.getMode().name());
    }

    @Transactional
    public TimbreConfigDto mettreAJour(TimbreConfigDto dto, String loginAdmin) {
        TimbreConfig e = loadEntity();
        e.setActif(dto.actif());
        e.setSeuil(dto.seuil());
        e.setPourcentage(dto.pourcentage());
        e.setCategoriesJson(serialize(dto.categorieIds() == null
                ? List.of() : dto.categorieIds()));
        e.setModesPaiementJson(serialize(dto.modesPaiement() == null
                ? List.of() : dto.modesPaiement()));
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(loginAdmin);
        repository.save(e);
        log.info("Configuration du timbre mise à jour par {} : actif={} seuil={} taux={}%",
                loginAdmin, e.isActif(), e.getSeuil(), e.getPourcentage());
        return toDto(e);
    }

    // ==================================================================
    //  Mapping
    // ==================================================================

    private TimbreConfig loadEntity() {
        return repository.findById(SINGLETON_ID)
                .orElseThrow(() -> new BusinessException(
                        "Configuration du timbre introuvable : la migration V12 "
                                + "ou le DataInitializer doit l'avoir initialisée."));
    }

    private TimbreConfigDto toDto(TimbreConfig e) {
        return new TimbreConfigDto(
                e.isActif(),
                e.getSeuil(),
                e.getPourcentage(),
                new ArrayList<>(parseLongs(e.getCategoriesJson())),
                new ArrayList<>(parseModes(e.getModesPaiementJson())),
                ModeTimbre.AUTO.name());
    }

    // ------------------------------------------------------------------
    //  (Dé)sérialisation JSON
    // ------------------------------------------------------------------

    private List<Long> parseLongs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException ex) {
            log.warn("categories_json corrompu, ignoré : {}", ex.getMessage());
            return List.of();
        }
    }

    private Set<ModePaiement> parseModes(String json) {
        if (json == null || json.isBlank()) {
            return EnumSet.noneOf(ModePaiement.class);
        }
        try {
            List<String> noms = objectMapper.readValue(json,
                    new TypeReference<List<String>>() {});
            Set<ModePaiement> modes = EnumSet.noneOf(ModePaiement.class);
            for (String nom : noms) {
                try {
                    modes.add(ModePaiement.valueOf(nom));
                } catch (IllegalArgumentException ignored) {
                    log.warn("Mode de paiement inconnu dans la config timbre : {}", nom);
                }
            }
            return modes;
        } catch (JsonProcessingException ex) {
            log.warn("modes_paiement_json corrompu, ignoré : {}", ex.getMessage());
            return EnumSet.noneOf(ModePaiement.class);
        }
    }

    private String serialize(List<?> valeurs) {
        try {
            return objectMapper.writeValueAsString(valeurs);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                    "Impossible de sérialiser la configuration du timbre : "
                            + ex.getMessage());
        }
    }
}
