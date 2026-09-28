package com.example.collab3d.client;

import com.example.collab3d.common.ParticipantInfo;
import com.example.collab3d.common.ParticipantResultState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Non-interactive 2D scoreboard layered over the 3D client viewport.
 *
 * <p>The scoreboard consumes the client replica of server-owned result state.
 * Accuracy is derived locally from authoritative hit and shot counters.</p>
 */
final class ClientScoreboardOverlay {

    private final ParticipantResultStore resultStore;
    private final Map<Long, ParticipantInfo> participants =
            new LinkedHashMap<>();
    private final VBox root = new VBox(5.0);
    private final GridPane grid = new GridPane();

    ClientScoreboardOverlay(ParticipantResultStore resultStore) {
        this.resultStore = resultStore;

        Label title = new Label("SCOREBOARD");
        title.setTextFill(Color.WHITE);

        grid.setHgap(10.0);
        grid.setVgap(2.0);

        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(8.0));
        root.setMouseTransparent(true);
        root.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        root.setBackground(new Background(new BackgroundFill(
                Color.rgb(8, 12, 20, 0.80),
                new CornerRadii(5.0),
                Insets.EMPTY)));
        root.getChildren().addAll(title, grid);

        refresh();
    }

    Node node() {
        return root;
    }

    void addOrUpdateParticipant(ParticipantInfo participant) {
        participants.put(participant.objectId(), participant);
        refresh();
    }

    void removeParticipant(long objectId) {
        participants.remove(objectId);
        refresh();
    }

    void resultStateUpdated(ParticipantResultState state) {
        refresh();
    }

    private void refresh() {
        grid.getChildren().clear();

        addHeader(0, "Player");
        addHeader(1, "Shots");
        addHeader(2, "Hits");
        addHeader(3, "Accuracy");
        addHeader(4, "Score");

        List<ParticipantInfo> ordered =
                new ArrayList<>(participants.values());

        ordered.sort(
                Comparator.<ParticipantInfo>comparingLong(
                        participant -> scoreFor(participant.objectId()))
                        .reversed()
                        .thenComparing(
                                Comparator.<ParticipantInfo>comparingLong(
                                        participant -> hitsFor(participant.objectId()))
                                        .reversed())
                        .thenComparing(ParticipantInfo::displayName)
                        .thenComparingLong(ParticipantInfo::objectId));

        int row = 1;
        for (ParticipantInfo participant : ordered) {
            ParticipantResultState state =
                    resultStore.get(participant.objectId());

            long shots = state == null ? 0L : state.shotsFired();
            long hits = state == null ? 0L : state.hitCount();
            long score = state == null ? 0L : state.score();
            double accuracy = state == null
                    ? 0.0
                    : state.accuracyPercent();

            addValue(0, row, participant.displayName(), Pos.CENTER_LEFT);
            addValue(1, row, Long.toString(shots), Pos.CENTER_RIGHT);
            addValue(2, row, Long.toString(hits), Pos.CENTER_RIGHT);
            addValue(
                    3,
                    row,
                    String.format(Locale.ROOT, "%.1f%%", accuracy),
                    Pos.CENTER_RIGHT);
            addValue(4, row, Long.toString(score), Pos.CENTER_RIGHT);
            row++;
        }
    }

    private long scoreFor(long objectId) {
        ParticipantResultState state = resultStore.get(objectId);
        return state == null ? 0L : state.score();
    }

    private long hitsFor(long objectId) {
        ParticipantResultState state = resultStore.get(objectId);
        return state == null ? 0L : state.hitCount();
    }

    private void addHeader(int column, String text) {
        Label label = new Label(text);
        label.setTextFill(Color.LIGHTGRAY);
        label.setMinWidth(Label.USE_PREF_SIZE);
        GridPane.setHgrow(label, Priority.NEVER);
        grid.add(label, column, 0);
    }

    private void addValue(
            int column,
            int row,
            String text,
            Pos alignment) {

        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setAlignment(alignment);
        label.setMaxWidth(Double.MAX_VALUE);
        grid.add(label, column, row);
    }
}
