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
    @Operation(summary = "Lire la configuration courante du timbre")
    public ResponseEntity<TimbreConfigDto> obtenir() {
        return ResponseEntity.ok(service.obtenir());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mettre à jour la configuration du timbre (ADMIN)")
    public ResponseEntity<TimbreConfigDto> mettreAJour(
            @Valid @RequestBody TimbreConfigDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(service.mettreAJour(dto, authentication.getName()));
    }
}
