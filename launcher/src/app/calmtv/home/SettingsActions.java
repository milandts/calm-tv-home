package app.calmtv.home;

/**
 * Rückmeldungen der Zeilen, der Vorschau-Leiste und der Seitenleiste an {@link SettingsScreen}. Benannte statische
 * Klassen statt innerer Klassen: D8 aus den Build-Tools 33 stürzt bei inneren Klassen dieser Art ab.
 */
final class SettingsActions {
    private static final long TOGGLE_REBUILD_DELAY_MS = 170, RANGE_REBUILD_DELAY_MS = 500;

    private SettingsActions() {}

    /** Die Zeilen der normalen Seiten melden Fokus, Link, Schalter, Auswahl, Einstellung und Button hierher. */
    static final class Rows implements SettingsRowView.Actions {
        private final SettingsScreen screen;

        Rows(SettingsScreen screen) {
            this.screen = screen;
        }

        @Override public void rowFocused(int rowIndex) {
            screen.lastRowIndex = rowIndex;
        }

        @Override public void openLink(int rowIndex, SettingsModel.Link link) {
            if (link.target != null) screen.pushPage(link.target, rowIndex);
        }

        @Override public void toggled(SettingsModel.Toggle toggle, boolean on) {
            toggle.set.set(on);
            screen.scheduleRebuild(TOGGLE_REBUILD_DELAY_MS);   // der Schalter soll erst zu Ende laufen
        }

        @Override public void stepChoice(SettingsModel.Choice choice, int direction, boolean cycle) {
            int count = choice.options.size();
            if (count == 0) return;
            int current = SettingsRowView.selectedIndex(choice);
            int next = cycle ? (current + 1) % count : current + direction;
            if (next < 0 || next >= count || next == current) return;
            choice.select.set(next);
            screen.rebuildAfterChange();
        }

        @Override public void runButton(SettingsModel.Button button) {
            screen.requestButton(button);
        }

        @Override public int optValue(SettingsModel.OptRow row) {
            return screen.options.value(row);
        }

        @Override public boolean optChanged(SettingsModel.OptRow row, int value) {
            if (!screen.options.apply(row, value)) return false;
            if (row.opt.kind == Opt.RANGE) screen.scheduleRebuild(RANGE_REBUILD_DELAY_MS);
            else if (row.opt.kind == Opt.BOOL) screen.scheduleRebuild(TOGGLE_REBUILD_DELAY_MS);
            else screen.rebuildAfterChange();
            return true;
        }
    }

    /** Die Vorschau-Leiste meldet Buttons und Einstellungen hierher. */
    static final class Preview implements SettingsPreview.Actions {
        private final SettingsScreen screen;

        Preview(SettingsScreen screen) {
            this.screen = screen;
        }

        @Override public void runButton(SettingsModel.Button button) {
            screen.requestButton(button);
        }

        @Override public int optValue(SettingsModel.OptRow row) {
            return screen.options.value(row);
        }

        @Override public boolean optChanged(SettingsModel.OptRow row, int value) {
            return screen.options.apply(row, value);
        }

        @Override public void optReset(SettingsModel.OptRow row) {
            screen.options.reset(row);
        }
    }

    /** Fokus und Auswahl in der Seitenleiste. */
    static final class Sidebar implements SettingsSidebar.Listener {
        private final SettingsScreen screen;

        Sidebar(SettingsScreen screen) {
            this.screen = screen;
        }

        @Override public void onFocused(int index) {
            screen.selectCategory(index);
        }

        @Override public void onClicked(int index) {
            screen.focusContentFirst();
        }
    }
}
