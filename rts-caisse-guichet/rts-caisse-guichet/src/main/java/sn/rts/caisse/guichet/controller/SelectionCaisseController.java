package sn.rts.caisse.guichet.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import sn.rts.caisse.guichet.api.CaisseApi;
import sn.rts.caisse.guichet.app.GuichetApplication;
import sn.rts.caisse.guichet.model.Dto.AuthResponse;
import sn.rts.caisse.guichet.model.Dto.CaisseDTO;
import sn.rts.caisse.guichet.model.StatutCaisse;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Session;
import sn.rts.caisse.guichet.util.Ui;

import java.util.Comparator;
import java.util.List;

/**
 * Écran de sélection de la caisse active.
 * Les caisses affectées au caissier connecté remontent en haut de liste.
 */
public class SelectionCaisseController {

    @FXML private Label agentNameLabel;
    @FXML private Label agentRoleLabel;
    @FXML private ListView<CaisseDTO> caissesList;
    @FXML private Button selectionnerButton;

    private final CaisseApi api = CaisseApi.getInstance();

    @FXML
    public void initialize() {
        AuthResponse auth = Session.getInstance().getAuth();
        if (auth != null) {
            agentNameLabel.setText(auth.nomComplet);
            agentRoleLabel.setText(auth.matricule + " · " + auth.role.name());
        }

        caissesList.setCellFactory(list -> new CaisseCell());
        caissesList.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> selectionnerButton.setDisable(n == null
                        || n.statut == StatutCaisse.SUSPENDUE));

        charger();
    }

    @FXML
    public void onRefresh() {
        charger();
    }

    private void charger() {
        Long monId = Session.getInstance().getAuth() != null
                ? Session.getInstance().getAuth().utilisateurId : null;

        AsyncRunner.run(
                api::listerCaisses,
                caisses -> {
                    List<CaisseDTO> triees = caisses.stream()
                            .sorted(Comparator
                                    .<CaisseDTO, Integer>comparing(c ->
                                            (monId != null && monId.equals(c.caissierId)) ? 0 : 1)
                                    .thenComparing(c -> c.code))
                            .toList();
                    caissesList.setItems(FXCollections.observableArrayList(triees));
                },
                e -> Ui.erreur("Erreur", "Impossible de charger les caisses : " + e.getMessage()));
    }

    @FXML
    public void onSelectionner() {
        CaisseDTO selection = caissesList.getSelectionModel().getSelectedItem();
        if (selection == null) return;

        if (selection.statut == StatutCaisse.SUSPENDUE) {
            Ui.erreur("Caisse suspendue",
                    "Cette caisse est suspendue. Contactez votre administrateur.");
            return;
        }

        Session.getInstance().setCaisseActive(selection);
        GuichetApplication.goTo("caissier.fxml");
    }

    @FXML
    public void onLogout() {
        Session.getInstance().clear();
        GuichetApplication.goTo("login.fxml");
    }

    // ==============================================================
    //  Cellule personnalisée
    // ==============================================================

    private class CaisseCell extends ListCell<CaisseDTO> {
        @Override
        protected void updateItem(CaisseDTO caisse, boolean empty) {
            super.updateItem(caisse, empty);
            if (empty || caisse == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            Long monId = Session.getInstance().getAuth() != null
                    ? Session.getInstance().getAuth().utilisateurId : null;
            boolean estMaCaisse = monId != null && monId.equals(caisse.caissierId);

            // Code + affectation
            Label code = new Label(caisse.code);
            code.setStyle("-fx-font-weight: bold; -fx-text-fill: #E30613; -fx-font-size: 13px;");

            Label libelle = new Label(caisse.libelle);
            libelle.setStyle("-fx-font-size: 14px;");

            Label emplacement = new Label(caisse.emplacement == null ? "" : "📍 " + caisse.emplacement);
            emplacement.setStyle("-fx-text-fill: #9e9e9e; -fx-font-size: 11px;");

            VBox infos = new VBox(2, code, libelle, emplacement);

            // Badge statut
            Label badge = new Label(caisse.statut.name());
            badge.getStyleClass().add("badge");
            switch (caisse.statut) {
                case OUVERTE -> badge.getStyleClass().add("badge-success");
                case SUSPENDUE -> badge.getStyleClass().add("badge-warning");
                case FERMEE -> badge.getStyleClass().add("badge-neutral");
            }

            VBox droite = new VBox(4, badge);
            droite.setStyle("-fx-alignment: center-right;");

            if (estMaCaisse) {
                Label mien = new Label("★ affectée à vous");
                mien.setStyle("-fx-text-fill: #e8a317; -fx-font-size: 11px; -fx-font-weight: bold;");
                droite.getChildren().add(mien);
            }

            HBox box = new HBox(16, infos, new Region(), droite);
            HBox.setHgrow(box.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
            box.setStyle("-fx-padding: 8 6;");
            setGraphic(box);
            setText(null);
        }
    }
}
