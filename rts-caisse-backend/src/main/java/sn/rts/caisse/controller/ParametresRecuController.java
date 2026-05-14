package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sn.rts.caisse.dto.ParametresRecuDto;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.service.ParametresRecuService;

import java.util.Set;

/**
 * Endpoints de personnalisation du reçu PDF.
 *
 * <ul>
 *   <li><b>GET</b> /api/parametres/recu — paramètres scalaires (authentifié).</li>
 *   <li><b>PUT</b> /api/parametres/recu — mise à jour scalaire (ADMIN).</li>
 *   <li><b>GET</b> /api/parametres/recu/logo — récupération du logo image (authentifié).</li>
 *   <li><b>POST</b> /api/parametres/recu/logo — upload du logo image (ADMIN).</li>
 *   <li><b>DELETE</b> /api/parametres/recu/logo — suppression du logo image (ADMIN).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/parametres/recu")
@RequiredArgsConstructor
@Tag(name = "Paramètres du reçu",
     description = "Personnalisation du reçu PDF (couleurs, infos, logo, layout)")
public class ParametresRecuController {

    /** Types MIME acceptés pour l'upload du logo. */
    private static final Set<String> TYPES_AUTORISES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    );

    /** Taille maximale du logo (2 Mo). */
    private static final long TAILLE_MAX_OCTETS = 2L * 1024 * 1024;

    private final ParametresRecuService service;

    // ==================================================================
    //  Paramètres scalaires (couleurs, textes, layout)
    // ==================================================================

    @GetMapping
    @Operation(summary = "Lire les paramètres courants du reçu")
    public ResponseEntity<ParametresRecuDto> obtenir() {
        return ResponseEntity.ok(service.obtenir());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mettre à jour les paramètres scalaires (ADMIN)")
    public ResponseEntity<ParametresRecuDto> mettreAJour(
            @Valid @RequestBody ParametresRecuDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(service.mettreAJour(dto, authentication.getName()));
    }

    // ==================================================================
    //  Logo (binaire)
    // ==================================================================

    @GetMapping("/logo")
    @Operation(summary = "Renvoie l'image du logo (404 si aucun logo n'a été déposé)")
    public ResponseEntity<byte[]> obtenirLogo() {
        return service.obtenirLogo()
                .map(logo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(logo.contentType()))
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(logo.image()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload du logo (PNG, JPG, GIF, WEBP — 2 Mo max)")
    public ResponseEntity<Void> televerserLogo(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        valider(file);
        try {
            service.enregistrerLogo(file.getBytes(), file.getContentType(),
                    authentication.getName());
        } catch (java.io.IOException e) {
            throw new BusinessException("Lecture du fichier impossible : " + e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprime le logo (fallback sur le texte logo)")
    public ResponseEntity<Void> supprimerLogo(Authentication authentication) {
        service.supprimerLogo(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    //  Validation upload
    // ==================================================================

    private void valider(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Aucun fichier reçu.");
        }
        if (file.getSize() > TAILLE_MAX_OCTETS) {
            throw new BusinessException(
                    "Fichier trop volumineux (max 2 Mo, reçu "
                            + (file.getSize() / 1024) + " Ko).");
        }
        String ct = file.getContentType();
        if (ct == null || !TYPES_AUTORISES.contains(ct.toLowerCase())) {
            throw new BusinessException(
                    "Type d'image non supporté : " + ct
                            + " (PNG, JPG, GIF ou WEBP attendu).");
        }
    }
}
