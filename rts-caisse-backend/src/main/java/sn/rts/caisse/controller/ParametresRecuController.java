package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.rts.caisse.dto.ParametresRecuDto;
import sn.rts.caisse.service.ParametresRecuService;

/**
 * Endpoints de personnalisation du reçu PDF.
 *
 * <ul>
 *   <li><b>GET</b> /api/parametres/recu — lecture des paramètres courants
 *       (accessible à tout utilisateur authentifié, utile pour l'aperçu).</li>
 *   <li><b>PUT</b> /api/parametres/recu — mise à jour, ADMIN uniquement.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/parametres/recu")
@RequiredArgsConstructor
@Tag(name = "Paramètres du reçu",
     description = "Personnalisation du reçu PDF (couleurs, infos, layout)")
public class ParametresRecuController {

    private final ParametresRecuService service;

    @GetMapping
    @Operation(summary = "Lire les paramètres courants du reçu")
    public ResponseEntity<ParametresRecuDto> obtenir() {
        return ResponseEntity.ok(service.obtenir());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mettre à jour les paramètres (ADMIN uniquement)")
    public ResponseEntity<ParametresRecuDto> mettreAJour(
            @Valid @RequestBody ParametresRecuDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(service.mettreAJour(dto, authentication.getName()));
    }
}
