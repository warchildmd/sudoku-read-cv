package mb.sudoku;

import mb.sudoku.utils.ImageTools;
import mb.sudoku.utils.SudokuTools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Created by Mihail on 2/13/2015.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(new File("sudoku.jpg"));
        BufferedImage bufferedImageBW = ImageTools.monochrome(bufferedImage);
        BufferedImage rotatedImage = ImageTools.getRotatedImage(bufferedImageBW);
        BufferedImage detectedGrid = ImageTools.detectGrid(rotatedImage);
        int [][] table = SudokuTools.getSudoku(detectedGrid);
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (table[i][j] == 0) {
                    System.out.print("_");
                } else {
                    System.out.print(table[i][j]);
                }
                System.out.print("|");
            }
            System.out.println();
        }
    }
}
