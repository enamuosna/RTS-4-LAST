package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.ClientDTO;
import sn.rts.caisse.service.ClientService;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Annonceurs, partenaires, tiers")
public class ClientController {

    private final ClientService service;

    @GetMapping
    @Operation(summary = "Lister / rechercher les clients (paramètre q optionnel)",
            description = "Endpoint non pagine, retourne la liste complete des clients. "
                    + "Utilise par le guichet desktop pour alimenter le combo de "
                    + "recherche client. Pour la page admin web, utiliser /page.")
    public ResponseEntity<List<ClientDTO>> lister(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.lister(q));
    }

    @GetMapping("/page")
    @Operation(summary = "Liste paginee des clients avec recherche optionnelle",
            description = "Variante paginee pour les pages admin web. Filtre 'q' "
                    + "s'applique sur raison sociale, NINEA, telephone et email.")
    public ResponseEntity<Page<ClientDTO>> listerPaginee(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(service.listerPaginee(q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping
    public ResponseEntity<ClientDTO> creer(@Valid @RequestBody ClientDTO dto) {
        return ResponseEntity.ok(service.creer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientDTO> modifier(@PathVariable Long id,
                                              @Valid @RequestBody ClientDTO dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }
}
