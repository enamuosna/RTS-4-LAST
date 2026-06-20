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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.rts.caisse.dto.TimbreConfigDto;
import sn.rts.caisse.service.TimbreConfigService;

/**
 * Endpoints de configuration du timbre fiscal.
 *
 * <ul>
 *   <li><b>GET</b> /api/parametres/timbre — lecture (tout utilisateur
 *       authentifié, le guichet en a besoin pour le calcul live).</li>
 *   <li><b>PUT</b> /api/parametres/timbre — mise à jour (ADMIN uniquement).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/parametres/timbre")
@RequiredArgsConstructor
@Tag(name = "Configuration du timbre",
     description = "Personnalisation du timbre fiscal (seuil, taux, catégories, modes)")
public class TimbreConfigController {

    private final TimbreConfigService service;

    @GetMapping
    @Operation(summary = "Lire la config du timbre d'une caisse (ou les défauts si caisseId absent)")
    public ResponseEntity<TimbreConfigDto> obtenir(
            @RequestParam(required = false) Long caisseId) {
        return ResponseEntity.ok(
                caisseId != null ? service.obtenir(caisseId) : service.obtenir());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mettre à jour la config du timbre d'une caisse (ADMIN)")
    public ResponseEntity<TimbreConfigDto> mettreAJour(
            @RequestParam(required = false) Long caisseId,
            @Valid @RequestBody TimbreConfigDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(caisseId != null
                ? service.mettreAJour(caisseId, dto, authentication.getName())
                : service.mettreAJour(dto, authentication.getName()));
    }
}
