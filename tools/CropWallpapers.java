import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Rectangle;

/**
 * Schneidet alle JPGs eines Ordners mittig auf 16:9 zu und skaliert sie auf 1920x1080 (läuft mit dem JDK, ohne weitere Werkzeuge).
 *
 * Aufruf: java tools/CropWallpapers.java <Ordner mit Rohbildern> <Ausgabeordner> [--png]
 * Mit --png entstehen verlustfreie PNGs (aus denen setup.sh WebP macht, wenn cwebp vorhanden ist), sonst JPEGs mit Qualität 0,82.
 * Große Bilder werden schon beim Lesen verkleinert (Teilbereich, Unterabtastung), damit auch 14000 Pixel breite Panoramen wenig Speicher brauchen.
 */
public final class CropWallpapers {
    private static final int OUT_WIDTH = 1920, OUT_HEIGHT = 1080;
    private static final float JPEG_QUALITY = 0.82f;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Aufruf: java tools/CropWallpapers.java <Rohbilder> <Ausgabe> [--png]");
            System.exit(2);
        }
        File in = new File(args[0]), out = new File(args[1]);
        boolean asPng = Arrays.asList(args).contains("--png");
        if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Ausgabeordner nicht anlegbar: " + out);
        File[] files = in.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) throw new IOException("Ordner nicht lesbar: " + in);
        Arrays.sort(files);
        int count = 0;
        for (File file : files) {
            BufferedImage picture = cropAndScale(file);
            String name = asPng ? file.getName().replaceAll("\\.jpg$", ".png") : file.getName();
            if (picture == null) {
                System.out.println("übersprungen: " + file.getName());
                continue;
            }
            write(picture, new File(out, name), asPng);
            count++;
        }
        System.out.println("zugeschnitten: " + count);
    }

    /** Mittiger Zuschnitt auf 16:9, schon beim Lesen verkleinert, dann auf genau 1920x1080 gebracht. */
    private static BufferedImage cropAndScale(File file) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                Rectangle crop = width * 9L > height * 16L
                        ? new Rectangle((width - (int) Math.round(height * 16.0 / 9.0)) / 2, 0, (int) Math.round(height * 16.0 / 9.0), height)
                        : new Rectangle(0, (height - (int) Math.round(width * 9.0 / 16.0)) / 2, width, (int) Math.round(width * 9.0 / 16.0));
                // Unterabtastung beim Lesen überspringt Pixel (Treppen), darum nur grob und mit mindestens doppelter Zielgröße; das Feine macht das Mitteln unten.
                int step = Math.max(1, (int) Math.floor(Math.min(crop.width / (2.0 * OUT_WIDTH), crop.height / (2.0 * OUT_HEIGHT))));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceRegion(crop);
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage current = reader.read(0, param);
                // Schrittweise halbieren (bilinear bei genau 2:1 ist ein Mittelwert über 2x2 Punkte), zuletzt bikubisch auf die Zielgröße.
                while (current.getWidth() >= 2 * OUT_WIDTH && current.getHeight() >= 2 * OUT_HEIGHT) {
                    current = resize(current, current.getWidth() / 2, current.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                }
                BufferedImage scaled = resize(current, OUT_WIDTH, OUT_HEIGHT, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                return scaled;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage resize(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return target;
    }

    private static void write(BufferedImage picture, File target, boolean asPng) throws IOException {
        if (asPng) {
            ImageIO.write(picture, "png", target);
            return;
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(picture, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
