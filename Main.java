import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main {

    private static final double COLOR_SPACING_THRESHOLD = 32;
    private static final int COLOR_COUNT = 16;
    private static final String IMAGE_PATH = "src/lena.jpg";

    public static void main(String[] args) throws IOException {
        BufferedImage originalImage = ImageIO.read(new File(IMAGE_PATH));

        if (originalImage == null) {
            System.err.println("Failed to load the image.");
            return;
        }

        BufferedImage img12Bit = convertTo12BitColor(originalImage);
        List<int[]> palette = generatePalette(img12Bit, COLOR_COUNT);

        System.out.println("Palette Colors:");
        for (int[] color : palette) {
            System.out.printf("R: %d, G: %d, B: %d\n", color[0], color[1], color[2]);
        }

        BufferedImage convertedImageHAM = convertImageToPalette(img12Bit, palette, true);
        BufferedImage convertedImageNoHAM = convertImageToPalette(img12Bit, palette, false);

        ImageIO.write(convertedImageHAM, "png", new File("src/converted_HAM.png"));
        ImageIO.write(convertedImageNoHAM, "png", new File("src/converted_noHAM.png"));
    }

    public static BufferedImage convertTo12BitColor(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage img12bit = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelColor = img.getRGB(x, y);
                int r = (pixelColor >> 16) & 0xFF;
                int g = (pixelColor >> 8) & 0xFF;
                int b = pixelColor & 0xFF;

                int r4 = r / 16;
                int g4 = g / 16;
                int b4 = b / 16;

                int rScaled = r4 * 17;
                int gScaled = g4 * 17;
                int bScaled = b4 * 17;

                int newPixelColor = (rScaled << 16) | (gScaled << 8) | bScaled;
                img12bit.setRGB(x, y, newPixelColor);
            }
        }

        return img12bit;
    }

    public static List<int[]> generatePalette(BufferedImage img, int k) {
        int width = img.getWidth();
        int height = img.getHeight();
        Map<String, Integer> colorFrequency = new HashMap<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelColor = img.getRGB(x, y);
                int[] rgb = getRGB(pixelColor);
                String colorKey = rgb[0] + "," + rgb[1] + "," + rgb[2];
                colorFrequency.put(colorKey, colorFrequency.getOrDefault(colorKey, 0) + 1);
            }
        }

        List<Map.Entry<String, Integer>> sortedColors = new ArrayList<>(colorFrequency.entrySet());
        sortedColors.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<int[]> palette = new ArrayList<>();
        int count = 0;

        for (Map.Entry<String, Integer> entry : sortedColors) {
            String[] rgbString = entry.getKey().split(",");
            int r = Integer.parseInt(rgbString[0]);
            int g = Integer.parseInt(rgbString[1]);
            int b = Integer.parseInt(rgbString[2]);

            if (isSpacedEnough(palette, new int[]{r, g, b})) {
                int scaledR = r / 16;
                int scaledG = g / 16;
                int scaledB = b / 16;

                palette.add(new int[]{scaledR, scaledG, scaledB});
                count++;
            }

            if (count >= k) break;
        }

        return palette;
    }

    public static boolean isSpacedEnough(List<int[]> palette, int[] newColor) {
        for (int[] color : palette) {
            if (colorDistance(color, newColor) < COLOR_SPACING_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    public static double colorDistance(int[] c1, int[] c2) {
        return Math.sqrt(Math.pow(c1[0] - c2[0], 2) + Math.pow(c1[1] - c2[1], 2) + Math.pow(c1[2] - c2[2], 2));
    }

    public static int[] getRGB(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return new int[]{r, g, b};
    }

    public static BufferedImage convertImageToPalette(BufferedImage img, List<int[]> palette, boolean HAM) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage convertedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelColor = img.getRGB(x, y);
                int[] rgb = getRGB(pixelColor);
                int[] nearestColor = findNearestColor(rgb, palette, convertedImage, x, y, HAM);

                int newColor = (nearestColor[0] * 17 << 16) | (nearestColor[1] * 17 << 8) | (nearestColor[2] * 17);
                convertedImage.setRGB(x, y, newColor);
            }
        }

        return convertedImage;
    }

    public static int[] findNearestColor(int[] color, List<int[]> palette, BufferedImage convert, int x, int y, boolean HAM) {
        double minDistance = Double.MAX_VALUE;
        int[] nearestColor = null;

        for (int[] paletteColor : palette) {
            double distance = colorDistance(color, new int[]{paletteColor[0] * 17, paletteColor[1] * 17, paletteColor[2] * 17});
            if (distance < minDistance) {
                minDistance = distance;
                nearestColor = paletteColor;
            }
        }

        if (HAM) {
            if (x == 0) {
                return nearestColor;
            }
            int[] leftPixelColor = getLeftPixelColor(convert, x, y);

            for (int[] alternateColor : generateAlternateColors(rgbToInt12Bit(leftPixelColor[0] / 16, leftPixelColor[1] / 16, leftPixelColor[2] / 16))) {
                double distance = colorDistance(color, new int[]{alternateColor[0] * 17, alternateColor[1] * 17, alternateColor[2] * 17});
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestColor = alternateColor;
                }
            }
        }
        return nearestColor;
    }

    public static int rgbToInt12Bit(int r, int g, int b) {
        if (r < 0 || r > 15 || g < 0 || g > 15 || b < 0 || b > 15) {
            throw new IllegalArgumentException("RGB values must be between 0 and 15 for 12-bit color");
        }
        return (r << 8) | (g << 4) | b;
    }

    public static List<int[]> generateAlternateColors(int color) {
        List<int[]> alternateColors = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            int flippedColor = color ^ (1 << i);
            int[] rgb = getRGBFrom12Bit(flippedColor);
            alternateColors.add(rgb);
        }

        return alternateColors;
    }

    public static int[] getRGBFrom12Bit(int color) {
        int r = (color >> 8) & 0x0F;
        int g = (color >> 4) & 0x0F;
        int b = color & 0x0F;
        return new int[]{r, g, b};
    }

    public static int[] getLeftPixelColor(BufferedImage img, int x, int y) {
        if (x <= 0 || y < 0 || y >= img.getHeight()) {
            System.err.println("Pixel is on the left edge or out of bounds.");
            return null;
        }

        int leftPixelColor = img.getRGB(x - 1, y);
        return getRGB(leftPixelColor);
    }
}