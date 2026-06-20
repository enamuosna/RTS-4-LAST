package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.LangueDto;
import sn.rts.caisse.service.LangueService;

import java.util.List;

/**
 * Référentiel des langues de diffusion. Lecture : tout utilisateur authentifié
 * (le guichet en a besoin) ; écriture : ADMIN.
 */
@RestController
@RequestMapping("/api/langues")
@RequiredArgsConstructor
@Tag(name = "Langues", description = "Référentiel des langues de diffusion à l'antenne")
public class LangueController {

    private final LangueService service;

    @GetMapping
    @Operation(summary = "Lister les langues (actives=true pour filtrer)")
    public ResponseEntity<List<LangueDto>> lister(
            @RequestParam(name = "actives", defaultValue = "false") boolean actives) {
        return ResponseEntity.ok(service.lister(actives));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une langue (ADMIN)")
    public ResponseEntity<LangueDto> creer(@Valid @RequestBody LangueDto dto) {
        return ResponseEntity.ok(service.creer(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier une langue (ADMIN)")
    public ResponseEntity<LangueDto> modifier(@PathVariable Long id,
                                              @Valid @RequestBody LangueDto dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer une langue (ADMIN)")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
