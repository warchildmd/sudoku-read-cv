package mb.sudoku.utils;

import mb.sudoku.helpers.HoughLine;
import mb.sudoku.helpers.HoughTransform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

/**
 * <h1>ImageTools</h1>
 * The SudokuTools class contains methods that
 * deal with puzzle solving used in the program.
 *
 * @author Mihail
 * @version 1.0
 * @since 2015-02-01
 */
public class SudokuTools {

    /* Learned data */
    private static int [][] data;

    /**
     * Performs learning from samples in the train directory.
     * Saves the trained data in the learned directory.
     *
     * This method will not throw an exception in case the save fails.
     */
    public static void learn() {
        data = new int[10][576];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 576; j++) {
                data[i][j] = 0;
            }
        }
        File trainDir =  new File("train");
        for (File numberDir: trainDir.listFiles()) {
            if (numberDir.isFile() || numberDir.getName().contains(".")) {
                continue;
            }
            int countTrained = 0;
            for (File image: numberDir.listFiles()) {
                if (!image.getName().contains(".jpg")) {
                    continue;
                }
                try {
                    BufferedImage bufferedImage = ImageIO.read(image);
                    bufferedImage = ImageTools.prepareDigit(bufferedImage);
                    int [] imageData = new int[24 * 24];
                    bufferedImage.getRaster().getPixels(0, 0, 24, 24, imageData);
                    for (int i = 0; i < 24 * 24; i++) {
                        data[Integer.parseInt(numberDir.getName())][i] += imageData[i];
                    }
                    countTrained++;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (countTrained == 0) {
                continue;
            }
            for (int i = 0; i < 24 * 24; i++) {
                data[Integer.parseInt(numberDir.getName())][i] /= countTrained;
                BufferedImage learnedNumber = new BufferedImage(24, 24, BufferedImage.TYPE_BYTE_GRAY);
                learnedNumber.getRaster().setPixels(0, 0, 24, 24, data[Integer.parseInt(numberDir.getName())]);
                try {
                    ImageIO.write(learnedNumber, "jpg", new File("learned/" + numberDir.getName() + ".jpg"));
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        }
    }

    /**
     * This method returns the sudoku grid as {@code int[][]}
     *
     * @param bufferedImage the monochrome image containing just the sudoku grid
     * @return the sudoku grid
     */
    public static int[][] getSudoku(BufferedImage bufferedImage) {
        Image tmp = bufferedImage.getScaledInstance(360, 360, Image.SCALE_SMOOTH);
        bufferedImage = new BufferedImage(360, 360, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        ArrayList<HoughLine> horizontal = new ArrayList<HoughLine>();
        ArrayList<HoughLine> vertical = new ArrayList<HoughLine>();

        HoughTransform ht = new HoughTransform(width, height);
        ht.addPoints(bufferedImage);

        Vector<HoughLine> lines = ht.getLines((int) (0.5 * ht.getHighestValue()));
        for (HoughLine line : lines) {
            if ((line.getTheta() > 0.05 && line.getTheta() < 1.54) || (line.getTheta() > 1.60 && line.getTheta() < 3.09)) {
                continue;
            }
            if (line.getTheta() > 1.41 && line.getTheta() < 1.59) {
                horizontal.add(line);
            } else {
                vertical.add(line);
            }
        }

        Collections.sort(horizontal, new ImageTools.LineComparator(width, height));
        Collections.sort(vertical, new ImageTools.LineComparator(width, height));

        int cellWidth = width / 9;
        int cellHeight = height / 9;

        int [][]table = new int[9][9];
        SudokuTools.learn();
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                BufferedImage number = bufferedImage.getSubimage(cellWidth * i + 4, cellHeight * j + 4, cellWidth - 4, cellHeight - 4);
                table[j][i] = SudokuTools.recognize(number);
            }
        }

        return table;
    }

    /**
     * This method returns the most likely number on the image
     *
     * @param bufferedImage the monochrome image containing a sudoku cell
     * @return the most likely number that is contained in the image
     */
    public static int recognize(BufferedImage bufferedImage) {
        bufferedImage = ImageTools.prepareDigit(bufferedImage);
        int [] imageData = new int[24 * 24];
        bufferedImage.getRaster().getPixels(0, 0, 24, 24, imageData);
        int minDistance = 1000000000;
        int minDistanceNumber = 0;
        for (int i = 0; i < 10; i++) {
            int distance = 0;
            for (int j = 0; j < 24 * 24; j++) {
                distance += ((data[i][j] - imageData[j]) * (data[i][j] - imageData[j]));
            }
            if (distance < minDistance) {
                minDistance = distance;
                minDistanceNumber = i;
            }
        }
        return minDistanceNumber;
    }

}
