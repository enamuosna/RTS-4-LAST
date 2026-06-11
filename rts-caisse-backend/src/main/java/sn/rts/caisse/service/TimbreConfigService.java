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
import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.TimbreConfig;
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
            Set<ModePaiement> modesPaiement) {
    }

    @Transactional(readOnly = true)
    public Reglement obtenirReglement() {
        TimbreConfig e = loadEntity();
        return new Reglement(
                e.isActif(),
                e.getSeuil(),
                e.getPourcentage(),
                Set.copyOf(parseLongs(e.getCategoriesJson())),
                parseModes(e.getModesPaiementJson()));
    }

    // ==================================================================
    //  LECTURE / MISE À JOUR (IHM admin)
    // ==================================================================

    @Transactional(readOnly = true)
    public TimbreConfigDto obtenir() {
        return toDto(loadEntity());
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
                new ArrayList<>(parseModes(e.getModesPaiementJson())));
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
