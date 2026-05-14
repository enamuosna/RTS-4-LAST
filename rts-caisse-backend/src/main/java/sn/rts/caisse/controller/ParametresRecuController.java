package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
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
import sn.rts.caisse.service.RecuPdfService;

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
    private final RecuPdfService recuPdfService;

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
    //  Rendu PDF de prévisualisation
    //
    //  Endpoint volontairement placé sous /api/parametres/recu/pdf
    //  (et pas sous /api/recus/...) pour éviter les blocages des extensions
    //  anti-tracking : certaines listes de filtres (uBlock, Brave Shields)
    //  bloquent les URLs contenant "apercu", "preview", "recus", ce qui
    //  cassait l'iframe d'aperçu avec ERR_BLOCKED_BY_CLIENT.
    // ==================================================================

    @GetMapping("/pdf")
    @Operation(summary = "Rendu PDF de démonstration avec les paramètres courants",
               description = "Utilisé pour le bouton « Télécharger l'aperçu » côté admin.")
    public ResponseEntity<ByteArrayResource> rendrePdf() {
        byte[] pdf = recuPdfService.genererApercu();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("recu-demo.pdf").build());
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdf.length);
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        headers.setPragma("no-cache");

        return ResponseEntity.ok()
                .headers(headers)
                .body(new ByteArrayResource(pdf));
    }

    @GetMapping("/image")
    @Operation(summary = "Rendu PNG (rasterisation) du reçu pour l'aperçu admin",
               description = "Retourne la première page du reçu en PNG. Utilisé à la place "
                       + "du PDF pour éviter les blocages par Tracking Prevention / AdBlockers "
                       + "qui filtrent les XHR de PDF sur certains navigateurs.")
    public ResponseEntity<byte[]> rendreImage() {
        byte[] png = recuPdfService.genererApercuPng(150);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(png.length);
        headers.setCacheControl("no-store, no-cache, must-revalidate");

        return ResponseEntity.ok().headers(headers).body(png);
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
