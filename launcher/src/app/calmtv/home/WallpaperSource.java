package app.calmtv.home;

import android.content.Context;
import android.media.ExifInterface;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Woher die Hintergrundbilder kommen: die mitgelieferten Naturfotos (assets/wallpapers, Nachweise in credits.txt) und, nur bei
 * eingeschalteter Einstellung "Eigene Bilder", Bilder aus den Ordnern /sdcard/Android/data/app.calmtv.home/files/wallpapers und
 * /sdcard/Android/media/app.calmtv.home/wallpapers (der zweite für Fire OS 8, wo ADB nicht nach Android/data schreiben darf).
 * Stabile Kennungen: "asset:Dateiname" und "own:Dateiname".
 */
final class WallpaperSource {
    private static final String DIRECTORY = "wallpapers";
    private static final String BUNDLED = "asset:", OWN = "own:";

    private final Context context;
    private final Map<String, String> credits = new HashMap<>();

    WallpaperSource(Context context) {
        this.context = context;
        readCredits();
    }

    /** Alle vorhandenen Kennungen, sortiert; der Wert sagt, ob das Bild ein eigenes ist. Eigene nur bei eingeschalteter Einstellung. */
    Map<String, Boolean> ids() {
        Map<String, Boolean> ids = new TreeMap<>();
        try {
            String[] names = context.getAssets().list(DIRECTORY);
            if (names != null) for (String name : names) if (name.endsWith(".jpg") || name.endsWith(".webp")) ids.put(BUNDLED + name, false);
        } catch (IOException e) {
            Log.w(HomeKeyService.TAG, "no bundled wallpapers", e);
        }
        if (Config.val(Opts.BG_OWN) != 0) {
            for (File folder : ownFolders()) {
                File[] files = folder.listFiles();
                if (files == null) continue;
                for (File file : files) if (file.isFile() && isImage(file.getName())) ids.put(OWN + file.getName(), true);
            }
        }
        return ids;
    }

    private List<File> ownFolders() {
        List<File> folders = new ArrayList<>();
        File data = context.getExternalFilesDir(DIRECTORY);
        if (data != null) folders.add(data);
        File[] media = context.getExternalMediaDirs();   // Android legt ihn für uns an, so kann ADB ihn füllen
        if (media.length > 0 && media[0] != null) {
            File folder = new File(media[0], DIRECTORY);
            if (folder.isDirectory() || folder.mkdirs()) folders.add(folder);
        }
        return folders;
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.startsWith(".") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"));
    }

    /** Die Datei eines eigenen Bildes, sonst null. */
    private File ownFile(String id) {
        String name = id.substring(OWN.length());
        for (File folder : ownFolders()) {
            File file = new File(folder, name);
            if (file.isFile()) return file;
        }
        return null;
    }

    InputStream open(String id) throws IOException {
        if (id.startsWith(OWN)) {
            File file = ownFile(id);
            if (file == null) throw new IOException("Bild fehlt: " + id);
            return new FileInputStream(file);
        }
        return context.getAssets().open(DIRECTORY + "/" + id.substring(BUNDLED.length()));
    }

    /** Drehung in Grad laut EXIF (nur eigene Bilder; mitgelieferte stehen aufrecht). */
    int rotationOf(String id) {
        if (!id.startsWith(OWN)) return 0;
        File file = ownFile(id);
        if (file == null) return 0;
        try {
            int orientation = new ExifInterface(file.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            return orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (IOException unreadable) {
            return 0;
        }
    }

    // ---- Bildnachweise ---------------------------------------------------------------------------

    /** Die Nachweiszeile eines mitgelieferten Bildes; leer bei eigenen Bildern oder wenn keine bekannt ist. */
    String creditFor(String id) {
        if (!id.startsWith(BUNDLED)) return "";
        String credit = credits.get(id.substring(BUNDLED.length()));
        return credit == null ? "" : credit;
    }

    /** Alle Nachweise der mitgelieferten Fotos in Dateireihenfolge. */
    List<String> allCredits() {
        List<String> files = new ArrayList<>(credits.keySet());
        Collections.sort(files);
        List<String> lines = new ArrayList<>();
        for (String file : files) lines.add(credits.get(file));
        return lines;
    }

    /** credits.txt: je mitgeliefertes Foto eine Zeile "Datei<TAB>Nachweis". */
    private void readCredits() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(DIRECTORY + "/credits.txt"), "UTF-8"))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] parts = line.split("\t", 2);
                if (parts.length == 2 && !parts[0].isEmpty()) credits.put(parts[0], parts[1]);
            }
        } catch (IOException e) {
            Log.i(HomeKeyService.TAG, "wallpaper credits missing", e);
        }
    }
}
