package mb.sudoku.utils;

import mb.sudoku.helpers.HoughLine;
import mb.sudoku.helpers.HoughTransform;
import org.bytedeco.javacpp.opencv_core;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import static org.bytedeco.javacpp.opencv_core.cvGetImage;
import static org.bytedeco.javacpp.opencv_imgproc.cvWarpPerspective;
import static org.bytedeco.javacv.JavaCV.getPerspectiveTransform;

/**
 * <h1>ImageTools</h1>
 * The ImageTools class contains methods that
 * deal with image manipulation used in the program.
 *
 * @author Mihail
 * @version 1.0
 * @since 2015-02-01
 */
public class ImageTools {

    /**
     * Saves a BufferedImage to the logs directory. The name is
     * constructed by concatenation of the System.currentTimeMillis()
     * and the tag.
     * <p/>
     * This method will not throw an exception in case the save fails.
     *
     * @param tag           a tag to identify the image
     * @param bufferedImage the image to be saved to the logs directory
     * @return true if the operation was successful, false if the operation failed
     */
    public static boolean log(String tag, BufferedImage bufferedImage) {
        File directoryChecker = new File("logs");
        if (!directoryChecker.exists()) {
            directoryChecker.mkdir();
        }
        File outputFile = new File("logs/" + System.currentTimeMillis() + "_" + tag + ".jpg");
        try {
            ImageIO.write(bufferedImage, "jpg", outputFile);
            return true;
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    /**
     * This method creates the monochrome version of the {@link BufferedImage}
     * passed as parameter. It uses adaptive threshold to create the monochrome
     * version.
     * <p/>
     * The method uses an area 11x11 around the pixel to calculate the threshold.
     * Integral matrix is used to speed up the calculation.
     *
     * @param image the source image
     * @return the image in monochrome
     */
    public static BufferedImage monochrome(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[] integral = new int[width * height];
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        for (int pixel = 0; pixel < pixels.length; pixel += 3) {

            int i = (pixel / 3) / width;
            int j = (pixel / 3) % width;

            int r = pixels[pixel + 2];
            int g = pixels[pixel + 1];
            int b = pixels[pixel];
            if (r < 0) r = 256 + r;
            if (g < 0) g = 256 + g;
            if (b < 0) b = 256 + b;
            int argb = r + g + b;
            int argbByte = argb / 3;

            if (i == 0 && j == 0) {
                integral[i * width + j] = argbByte;
            } else if (i == 0) {
                integral[i * width + j] = integral[i * width + j - 1] + argbByte;
            } else if (j == 0) {
                integral[i * width + j] = integral[(i - 1) * width + j] + argbByte;
            } else {
                integral[i * width + j] = integral[(i - 1) * width + j] + integral[i * width + j - 1] - integral[(i - 1) * width + j - 1] + argbByte;
            }
        }

        BufferedImage imageBlackWhite = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = imageBlackWhite.getRaster();
        int[] pixelsBW = new int[width * height + 1];

        for (int pixel = 0; pixel < pixels.length; pixel += 3) {
            int r = pixels[pixel];
            int g = pixels[pixel + 1];
            int b = pixels[pixel + 2];
            if (r < 0) r = 256 + r;
            if (g < 0) g = 256 + g;
            if (b < 0) b = 256 + b;
            int argb = r + g + b;
            int argbByte = argb / 3;

            int threshold = 0;
            int i = (pixel / 3) / width;
            int j = (pixel / 3) % width;

            if (i > 5 && i < height - 5 && j > 5 && j < width - 5) {
                threshold = (integral[(i + 5) * width + (j + 5)] - integral[(i - 6) * width + (j + 5)]
                        - integral[(i + 5) * width + (j - 6)] + integral[(i - 6) * width + (j - 6)]) / 121;
            }

            if (argbByte > threshold * 0.9) {
                pixelsBW[pixel / 3] = 0;
            } else {
                pixelsBW[pixel / 3] = 255;
            }
        }

        raster.setPixels(0, 0, width, height, pixelsBW);
        return imageBlackWhite;
    }

    /**
     * Rotates a monochrome BufferedImage so the sudoku grid is aligned.
     * <p/>
     * {@link mb.sudoku.helpers.HoughTransform} is used to detect the horizontal
     * grid lines. The threshold for {@link mb.sudoku.helpers.HoughLine} used is
     * {@code 0.6 * HoughTransform.getHighestValue()} * votes of the most voted
     * {@link mb.sudoku.helpers.HoughLine}. The rotation needed is the mean of
     * the angles of all the horizontal lines that pass the threshold.
     *
     * @param bufferedImage the source monochrome image
     * @return the monochrome image with the sudoku grid aligned
     * @see mb.sudoku.helpers.HoughTransform
     * @see mb.sudoku.helpers.HoughLine
     * @see java.awt.geom.AffineTransform
     */
    public static BufferedImage getRotatedImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        /* 1. Initialize HoughTransform and vote horizontal lines */
        HoughTransform houghTransform = new HoughTransform(width, height);
        houghTransform.addHorizontalPoints(bufferedImage);

        /* 2. Calculate the mean of the angles off all lines that pass the threshold */
        double meanTheta = 0;
        Vector<HoughLine> lines = houghTransform.getLines((int) (0.6 * houghTransform.getHighestValue()));
        for (HoughLine line : lines) {
            meanTheta += line.getTheta();
        }
        meanTheta /= lines.size();

        /* 3. Rotate the image using the angle calculated */
        BufferedImage rotatedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        double normalRadian = Math.toRadians(90);
        AffineTransform tx = AffineTransform.getRotateInstance(normalRadian - meanTheta, width / 2, height / 2);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
        op.filter(bufferedImage, rotatedImage);

        return rotatedImage;
    }

    /**
     * This method detects the sudoku grid in the image and returns the image
     * cropped around the grid.
     * <p/>
     * {@link mb.sudoku.helpers.HoughTransform} is used to detect the grid lines.
     * The threshold for {@link mb.sudoku.helpers.HoughLine} used is
     * {@code 0.5 * HoughTransform.getHighestValue()} * votes of the most voted
     * {@link mb.sudoku.helpers.HoughLine}. {@link mb.sudoku.helpers.HoughLine}s
     * are sorted separately (vertical and horizontal). The middle square is found
     * and is used as the starting point to grow the grid horizontally and
     * vertically.
     * <p/>
     * The second part is performing a warpPerspective to eliminate the skewing of
     * the grid and to transform the grid into a square.
     *
     * @param bufferedImage the source monochrome rotated image
     * @return the monochrome image that contains only the sudoku grid in case of success, and null otherwise
     * @see mb.sudoku.helpers.HoughTransform
     * @see mb.sudoku.helpers.HoughLine
     * @see org.bytedeco.javacpp.opencv_core
     */
    public static BufferedImage detectGrid(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        /* 1. Get the vertical and horizontal lines and then sort them */
        ArrayList<HoughLine> horizontal = new ArrayList<HoughLine>();
        ArrayList<HoughLine> vertical = new ArrayList<HoughLine>();
        HoughTransform houghTransform = new HoughTransform(width, height);
        houghTransform.addPoints(bufferedImage);

        Vector<HoughLine> lines = houghTransform.getLines((int) (0.5 * houghTransform.getHighestValue()));
        for (HoughLine line : lines) {
            if ((line.getTheta() > 0.09 && line.getTheta() < 1.51) || (line.getTheta() > 1.60 && line.getTheta() < 3.05)) {
                continue;
            }
            if (line.getTheta() >= 1.51 && line.getTheta() <= 1.60) {
                horizontal.add(line);
            } else {
                vertical.add(line);
            }
        }
        Collections.sort(horizontal, new LineComparator(width, height));
        Collections.sort(vertical, new LineComparator(width, height));

        /* 2. Find the middle square */
        int horizontalMiddle = horizontal.size() / 2;
        int verticalMiddle = vertical.size() / 2;
        LineComparator lc = new LineComparator(width, height);

        int up = horizontalMiddle;
        int down = horizontalMiddle + 1;
        int dist = Math.abs(lc.compare(horizontal.get(up), horizontal.get(down)));
        while (dist < 30) {
            if (Math.abs(lc.compare(horizontal.get(up - 1), horizontal.get(down))) < Math.abs(lc.compare(horizontal.get(up), horizontal.get(down + 1)))) {
                up--;
            } else {
                down++;
            }
            dist = Math.abs(lc.compare(horizontal.get(up), horizontal.get(down)));
        }
        int lowerUp = up - 1;
        int newDist = Math.abs(lc.compare(horizontal.get(up), horizontal.get(lowerUp)));
        while (newDist < 30 && newDist < dist * 0.8) {
            if (newDist > 0.4 * dist && newDist < 0.6 * dist) {
                down = lowerUp;
                dist = newDist;
                break;
            }
            lowerUp--;
            newDist = Math.abs(lc.compare(horizontal.get(up), horizontal.get(lowerUp)));
        }
        int left = verticalMiddle;
        int right = verticalMiddle + 1;
        while (Math.abs(lc.compare(vertical.get(left), vertical.get(right))) < dist * 0.8) {
            if (Math.abs(lc.compare(vertical.get(left - 1), vertical.get(right))) < Math.abs(lc.compare(vertical.get(left), vertical.get(right + 1)))) {
                left--;
            } else {
                right++;
            }
        }

        /* 3. Start expanding vertically and horizontally till we have 10 lines of each type */
        int horizontalLines = 2;
        int verticalLines = 2;

        while (horizontalLines < 10) {
            int meanDistance = Math.abs(lc.compare(horizontal.get(up), horizontal.get(down))) / (horizontalLines - 1);
            for (int cup = up - 1; cup >= 0; cup--) {
                int distance = Math.abs(lc.compare(horizontal.get(cup), horizontal.get(up)));
                if (distance > meanDistance * 0.8 && distance < meanDistance * 1.2) {
                    up = cup;
                    horizontalLines++;
                    break;
                } else if (distance > meanDistance * 1.6 && distance < meanDistance * 2.4) {
                    up = cup;
                    horizontalLines++;
                    horizontalLines++;
                    break;
                }
            }
            for (int cup = down + 1; cup < horizontal.size(); cup++) {
                int distance = Math.abs(lc.compare(horizontal.get(cup), horizontal.get(down)));
                if (distance > meanDistance * 0.8 && distance < meanDistance * 1.2) {
                    down = cup;
                    horizontalLines++;
                    break;
                } else if (distance > meanDistance * 1.6 && distance < meanDistance * 2.4) {
                    down = cup;
                    horizontalLines++;
                    horizontalLines++;
                    break;
                }
            }
        }

        while (verticalLines < 10) {
            int meanDistance = Math.abs(lc.compare(vertical.get(left), vertical.get(right))) / (verticalLines - 1);
            for (int cup = left - 1; cup >= 0; cup--) {
                int distance = Math.abs(lc.compare(vertical.get(cup), vertical.get(left)));
                if (distance > meanDistance * 0.8 && distance < meanDistance * 1.2) {
                    left = cup;
                    verticalLines++;
                    break;
                } else if (distance > meanDistance * 1.6 && distance < meanDistance * 2.4) {
                    left = cup;
                    verticalLines++;
                    verticalLines++;
                    break;
                }
            }
            for (int cup = right + 1; cup < vertical.size(); cup++) {
                int distance = Math.abs(lc.compare(vertical.get(cup), vertical.get(right)));
                if (distance > meanDistance * 0.8 && distance < meanDistance * 1.2) {
                    right = cup;
                    verticalLines++;
                    break;
                } else if (distance > meanDistance * 1.6 && distance < meanDistance * 2.4) {
                    right = cup;
                    verticalLines++;
                    verticalLines++;
                    break;
                }
            }
        }

        Point pointTopLeft = parametricIntersect(horizontal.get(up).getR(), horizontal.get(up).getTheta(),
                vertical.get(left).getR(), vertical.get(left).getTheta(), width, height);
        Point pointTopRight = parametricIntersect(horizontal.get(up).getR(), horizontal.get(up).getTheta(),
                vertical.get(right).getR(), vertical.get(right).getTheta(), width, height);
        Point pointBottomLeft = parametricIntersect(horizontal.get(down).getR(), horizontal.get(down).getTheta(),
                vertical.get(left).getR(), vertical.get(left).getTheta(), width, height);
        Point pointBottomRight = parametricIntersect(horizontal.get(down).getR(), horizontal.get(down).getTheta(),
                vertical.get(right).getR(), vertical.get(right).getTheta(), width, height);

        /* 4. Crop and warp the image around the grid */
        ParameterBlock params = new ParameterBlock();
        params.addSource(bufferedImage);
        Point tl = new Point((int) pointTopLeft.getX(), (int) pointTopLeft.getY());
        Point tr = new Point((int) pointTopRight.getX(), (int) pointTopRight.getY());
        Point bl = new Point((int) pointBottomLeft.getX(), (int) pointBottomLeft.getY());
        Point br = new Point((int) pointBottomRight.getX(), (int) pointBottomRight.getY());
        try {
            opencv_core.CvMat mat = opencv_core.CvMat.create(3, 3);
            getPerspectiveTransform(new double[]{tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y},
                    new double[]{0, 0, 400, 0, 400, 400, 0, 400}, mat);
            org.bytedeco.javacpp.helper.opencv_core.CvArr input = opencv_core.IplImage.createFrom(bufferedImage).
                    asCvMat();
            org.bytedeco.javacpp.helper.opencv_core.CvArr output = opencv_core.IplImage.createFrom(
                    new BufferedImage(400, 400, BufferedImage.TYPE_BYTE_GRAY)
            ).asCvMat();
            cvWarpPerspective(input, output, mat);
            opencv_core.IplImage outputImage = opencv_core.IplImage.create(400, 400, 8, 1);
            cvGetImage(output, outputImage);
            BufferedImage cutImage = outputImage.getBufferedImage();
            return cutImage;
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    /**
     * Prepares the digit for learning or recognition.
     * <p/>
     * Crops the image around the digit, eliminating redundant space and noise
     * around it.
     *
     * @param bufferedImage the source square monochrome image
     * @return a monochrome 24x24 image with the noise and space eliminated.
     */
    public static BufferedImage prepareDigit(BufferedImage bufferedImage) {
        int up = bufferedImage.getHeight() / 2 - 1;
        int down = bufferedImage.getHeight() / 2 + 1;
        int left = bufferedImage.getWidth() / 2 - 1;
        int right = bufferedImage.getWidth() / 2 + 1;
        int horizontal = (int) (bufferedImage.getWidth() * 0.15);
        int vertical = (int) (bufferedImage.getHeight() * 0.15);

        boolean current = true;

        // found boundaries
        while (up > 0) {
            up--;
            current = true;
            int[] data = new int[bufferedImage.getWidth()];
            bufferedImage.getRaster().getPixels(0, up, bufferedImage.getWidth(), 1, data);
            for (int i = horizontal; i < bufferedImage.getWidth() - horizontal; i++) {
                if (data[i] != 0) {
                    current = false;
                    break;
                }
            }
            if (current) {
                up += 1;
                break;
            }
        }

        while (down < bufferedImage.getHeight() - 1) {
            down++;
            current = true;
            int[] data = new int[bufferedImage.getWidth()];
            bufferedImage.getRaster().getPixels(0, down, bufferedImage.getWidth(), 1, data);
            for (int i = horizontal; i < bufferedImage.getWidth() - horizontal; i++) {
                if (data[i] != 0) {
                    current = false;
                    break;
                }
            }
            if (current) {
                down -= 1;
                break;
            }
        }

        while (left > 0) {
            left--;
            current = true;
            int[] data = new int[bufferedImage.getHeight()];
            bufferedImage.getRaster().getPixels(left, 0, 1, bufferedImage.getHeight(), data);
            for (int i = vertical; i < bufferedImage.getHeight() - vertical; i++) {
                if (data[i] != 0) {
                    current = false;
                    break;
                }
            }
            if (current) {
                left += 1;
                break;
            }
        }

        while (right < bufferedImage.getWidth() - 1) {
            right++;
            current = true;
            int[] data = new int[bufferedImage.getHeight()];
            bufferedImage.getRaster().getPixels(right, 0, 1, bufferedImage.getHeight(), data);
            for (int i = vertical; i < bufferedImage.getHeight() - vertical; i++) {
                if (data[i] != 0) {
                    current = false;
                    break;
                }
            }
            if (current) {
                right -= 1;
                break;
            }
        }

        BufferedImage crop = bufferedImage.getSubimage(left, up, right - left, down - up);
        crop = resize(crop, 24, 24);
        return crop;
    }

    /**
     * This method resizes an image.
     *
     * @param img  the source image
     * @param newW new width
     * @param newH new height
     * @return resized image
     */
    private static BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D graphics = resizedImage.createGraphics();
        graphics.drawImage(tmp, 0, 0, null);
        graphics.dispose();

        return resizedImage;
    }

    /**
     * This method finds an intersection between two lines defined
     * with [radius, angle] ({@link mb.sudoku.helpers.HoughLine})
     *
     * @param r1     radius of the first line
     * @param t1     angle of the first line
     * @param r2     radius of the second line
     * @param t2     angle of the second line
     * @param width  width of the space
     * @param height height of the space
     * @return if the lines intersect - the point of intersection, null - otherwise
     */
    private static Point parametricIntersect(double r1, double t1, double r2, double t2, int width, int height) {
        int houghHeight = (int) (Math.sqrt(2) * Math.max(height, width)) / 2;

        /* Find edge points and vote in array */
        float centerX = width / 2;
        float centerY = height / 2;

        /* Draw edges in output array */
        double tsin = Math.sin(t1);
        double tcos = Math.cos(t1);
        double dsin = Math.sin(t2);
        double dcos = Math.cos(t2);

        if (t1 < Math.PI * 0.25 || t1 > Math.PI * 0.75) {
            /* t1 is a vertical line */
            for (int y = 0; y < height; y++) {
                int x = (int) ((((r1 - houghHeight) - ((y - centerY) * tsin)) / tcos) + centerX);
                int fy = (int) ((((r2 - houghHeight) - ((x - centerX) * dcos)) / dsin) + centerY);
                if (y == fy) {
                    return new Point(x, y);
                }
            }
        } else {
            /* t1 is a horizontal line */
            for (int x = 0; x < width; x++) {
                int y = (int) ((((r1 - houghHeight) - ((x - centerX) * tcos)) / tsin) + centerY);
                int fx = (int) ((((r2 - houghHeight) - ((y - centerY) * dsin)) / dcos) + centerX);
                if (x == fx) {
                    return new Point(x, y);
                }
            }
        }

        return null;
    }

    public static class LineComparator implements Comparator<HoughLine> {

        private final int height;
        private final int width;

        public LineComparator(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public int compare(HoughLine o1, HoughLine o2) {
            int houghHeight = (int) (Math.sqrt(2) * Math.max(height, width)) / 2;

            /* Find edge points and vote in array */
            float centerX = width / 2;
            float centerY = height / 2;

            /* Draw edges in output array */
            double tsin = Math.sin(o1.getTheta());
            double tcos = Math.cos(o1.getTheta());

            /* Draw edges in output array */
            double dsin = Math.sin(o2.getTheta());
            double dcos = Math.cos(o2.getTheta());

            if (o1.getTheta() < Math.PI * 0.25 || o1.getTheta() > Math.PI * 0.75) {
                /* Draw vertical lines */
                int y = height / 2;
                int x1 = (int) ((((o1.getR() - houghHeight) - ((y - centerY) * tsin)) / tcos) + centerX);
                int x2 = (int) ((((o2.getR() - houghHeight) - ((y - centerY) * dsin)) / dcos) + centerX);
                return x1 - x2;
            } else {
                /* Draw horizontal lines */
                int x = width / 2;
                int y1 = (int) ((((o1.getR() - houghHeight) - ((x - centerX) * tcos)) / tsin) + centerY);
                int y2 = (int) ((((o2.getR() - houghHeight) - ((x - centerX) * dcos)) / dsin) + centerY);
                return y1 - y2;
            }
        }
    }
}
