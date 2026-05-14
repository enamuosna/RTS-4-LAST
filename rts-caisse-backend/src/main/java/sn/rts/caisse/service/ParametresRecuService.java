package sn.rts.caisse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.ParametresRecuDto;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.model.ParametresRecu;
import sn.rts.caisse.repository.ParametresRecuRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service singleton qui gère la lecture et la mise à jour des paramètres
 * de personnalisation du reçu PDF. La table {@code parametres_recu} ne
 * contient qu'une seule ligne (id=1) initialisée par la migration Flyway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParametresRecuService {

    private static final Long SINGLETON_ID = 1L;

    private final ParametresRecuRepository repository;
    private final ObjectMapper objectMapper;

    // ==================================================================
    //  LECTURE
    // ==================================================================

    @Transactional(readOnly = true)
    public ParametresRecuDto obtenir() {
        return toDto(loadEntity());
    }

    /**
     * Renvoie directement l'entité (utilisé par {@link RecuPdfService} pour
     * éviter une conversion DTO inutile lors de la génération du PDF).
     */
    @Transactional(readOnly = true)
    public ParametresRecu obtenirEntite() {
        return loadEntity();
    }

    // ==================================================================
    //  MISE À JOUR
    // ==================================================================

    @Transactional
    public ParametresRecuDto mettreAJour(ParametresRecuDto dto, String loginAdmin) {
        ParametresRecu entity = loadEntity();
        applyDto(entity, dto);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(loginAdmin);
        repository.save(entity);
        log.info("Paramètres du reçu mis à jour par {}", loginAdmin);
        return toDto(entity);
    }

    // ==================================================================
    //  LOGO (binaire)
    // ==================================================================

    /** Logo image + type MIME — vide si aucun logo n'a été uploadé. */
    public record LogoBinaire(byte[] image, String contentType) {}

    @Transactional(readOnly = true)
    public Optional<LogoBinaire> obtenirLogo() {
        ParametresRecu e = loadEntity();
        if (e.getLogoImage() == null || e.getLogoImage().length == 0) {
            return Optional.empty();
        }
        String ct = e.getLogoContentType() != null
                ? e.getLogoContentType() : "image/png";
        return Optional.of(new LogoBinaire(e.getLogoImage(), ct));
    }

    @Transactional
    public void enregistrerLogo(byte[] image, String contentType, String loginAdmin) {
        ParametresRecu e = loadEntity();
        e.setLogoImage(image);
        e.setLogoContentType(contentType);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(loginAdmin);
        repository.save(e);
        log.info("Logo téléversé par {} ({} octets, {})",
                loginAdmin, image.length, contentType);
    }

    @Transactional
    public void supprimerLogo(String loginAdmin) {
        ParametresRecu e = loadEntity();
        e.setLogoImage(null);
        e.setLogoContentType(null);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(loginAdmin);
        repository.save(e);
        log.info("Logo supprimé par {}", loginAdmin);
    }

    // ==================================================================
    //  Mapping
    // ==================================================================

    private ParametresRecu loadEntity() {
        return repository.findById(SINGLETON_ID)
                .orElseThrow(() -> new BusinessException(
                        "Paramètres du reçu introuvables : la migration Flyway V4 "
                        + "doit avoir été exécutée."));
    }

    private ParametresRecuDto toDto(ParametresRecu e) {
        return new ParametresRecuDto(
                e.getLogoTexte(),
                e.getRaisonSociale(),
                e.getSousTitreEntete(),
                e.getLigneLegale(),
                e.getCapital(),
                e.getAdresse(),
                e.getTelephone(),
                e.getBoitePostale(),
                e.getNinea(),
                e.getFooterLigne1(),
                e.getFooterLigne2(),
                e.getVilleSignature(),
                e.getCouleurPrimaire(),
                e.getCouleurAccent(),
                e.getCouleurTexte(),
                e.getCouleurTexteSecondaire(),
                e.getCouleurSuccess(),
                e.getCouleurDanger(),
                e.getCouleurFondMontant(),
                e.getTailleTitre(),
                e.getTailleEntete(),
                e.getTailleCorps(),
                e.getTailleMontant(),
                e.getTailleFooter(),
                deserializeSections(e.getLayoutJson()),
                e.getLogoImage() != null && e.getLogoImage().length > 0
        );
    }

    private void applyDto(ParametresRecu e, ParametresRecuDto dto) {
        e.setLogoTexte(dto.logoTexte());
        e.setRaisonSociale(dto.raisonSociale());
        e.setSousTitreEntete(dto.sousTitreEntete());
        e.setLigneLegale(dto.ligneLegale());
        e.setCapital(dto.capital());
        e.setAdresse(dto.adresse());
        e.setTelephone(dto.telephone());
        e.setBoitePostale(dto.boitePostale());
        e.setNinea(dto.ninea());
        e.setFooterLigne1(dto.footerLigne1());
        e.setFooterLigne2(dto.footerLigne2());
        e.setVilleSignature(dto.villeSignature());
        e.setCouleurPrimaire(dto.couleurPrimaire());
        e.setCouleurAccent(dto.couleurAccent());
        e.setCouleurTexte(dto.couleurTexte());
        e.setCouleurTexteSecondaire(dto.couleurTexteSecondaire());
        e.setCouleurSuccess(dto.couleurSuccess());
        e.setCouleurDanger(dto.couleurDanger());
        e.setCouleurFondMontant(dto.couleurFondMontant());
        e.setTailleTitre(dto.tailleTitre());
        e.setTailleEntete(dto.tailleEntete());
        e.setTailleCorps(dto.tailleCorps());
        e.setTailleMontant(dto.tailleMontant());
        e.setTailleFooter(dto.tailleFooter());
        e.setLayoutJson(serializeSections(dto.sections()));
    }

    private List<ParametresRecuDto.Section> deserializeSections(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<ParametresRecuDto.Section>>() {});
        } catch (JsonProcessingException e) {
            log.warn("layout_json corrompu : {}", e.getMessage());
            return List.of();
        }
    }

    private String serializeSections(List<ParametresRecuDto.Section> sections) {
        if (sections == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(sections);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    "Impossible de sérialiser le layout : " + e.getMessage());
        }
    }
}
