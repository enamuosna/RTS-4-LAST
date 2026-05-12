package sn.rts.caisse.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sn.rts.caisse.model.Banque;
import sn.rts.caisse.repository.BanqueRepository;

import java.util.List;

@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class BanqueSeeder implements CommandLineRunner {

    private final BanqueRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("Banques déjà présentes ({}), seeder ignoré.", repository.count());
            return;
        }

        List<Banque> banques = List.of(
                build("B01", "BICIS", "SN010"),
                build("B02", "SGBS", "SN011"),
                build("B04", "CITIBANK", "SN115"),
                build("B05", "ECOBANK", "SN094"),
                build("B06", "CNCA", "SN116"),
                build("B07", "TRESOR", "SN017"),
                build("B08", "BHS", "SN039"),
                build("B10", "CBAO", "SN012"),
                build("B23", "CORIS BANK", "SN213"),
                build("B26", "BNDE", "SN169"),
                build("B27", "UBA", "SN153"),
                build("B28", "BDK", "SN191"),
                build("B29", "CORIS BANK", "SN213"),
                build("B30", "CORIS BANK EVENEMENTIEL", "SN213"),
                build("BHS", "BANQUE DE L'HABITAT DU SENEGAL", "SN039")
        );

        repository.saveAll(banques);
        log.info("✅ {} banques sénégalaises initialisées.", banques.size());
    }

    private Banque build(String code, String libelle, String codeEtab) {
        return Banque.builder()
                .code(code)
                .libelle(libelle)
                .pays("SÉNÉGAL")
                .codeEtablissement(codeEtab)
                .actif(true)
                .build();
    }
}