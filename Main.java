import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.io.RandomAccessFile;
import java.io.IOException;  
import java.io.*;

/*
* JPEG Encoder for little-endian format bitmap (.bmp) image
* Bits per pixel must be divisble by 8 and between 8 and 32 bits
* 
* Encoding Standard -> JPEG
* File Format -> JFIF
*
* Input bitmap -> RGB -> YCC -> DCT -> Quantization -> Huffman Encoding -> Output JPEG
*
* Authors: 
* Maria Waseem
* Danial Waseem
*
* Start date: 08/12/2023
* First successful JPEG output: 
*/ 

public class Main{

    // Pixels of image
    static int[][] pixels;

    // YCC matricies
    static int[][] lum;
    static int[][] chromb;
    static int[][] chromr;

    // Returns the next 4 bytes in the file read
    public static int read4Bytes(RandomAccessFile raimage) throws IOException{
        return raimage.read() + (raimage.read() << 8) + (raimage.read() << 16) + (raimage.read() << 24);
    }

    // Returns the next N bytes in the file read
    public static int readNBytes(RandomAccessFile raimage, int n) throws IOException{
        int mult = 0;
        int result = 0;
        for(int i = 0; i < n; i++){
            result += raimage.read() << (mult);
            mult += 8;
        }
        return result;
    }

    // Converts RGB to YCbCr
    public static int RGBtoYCC(int rgb, int bpp){
        int R = rgb >> (bpp * 2);
        int G = (rgb & (0xFF00)) >> bpp;
        int B = rgb & (0xFF);
        
        double Y = (0.299 * R) + (0.587 * G) + (0.114 * B);
        double Cb = (-0.1687 * R) - (0.3313 * G) + (0.5 * B) + 128;
        double Cr = (0.5 * R) - (0.4187 * G) - (0.0813 * B) + 128;

        return 0;
    }

    // DCT Calculation
    public static double[][] DCT(int[][] block) {
        double[][] DCTblock = new double[8][8];

        for (int u = 0; u < 8; u++) {
            for (int v = 0; v < 8; v++) {
                double Cu = (u == 0) ? Math.sqrt(2) / 2 : 1;
                double Cv = (v == 0) ? Math.sqrt(2) / 2 : 1;
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 8; y++) {
                        DCTblock[u][v] += block[x][y] * Math.cos( ((2 * x + 1) * u * Math.PI) / 16) * Math.cos( ((2 * y + 1) * v * Math.PI) / 16);
                    }
                }
                DCTblock[u][v] *= 0.25 * Cu * Cv;
            }
        }
        return DCTblock;
    }

    /* Quantization
    *
    * Quantization Matrix:
    * 16 11 10 16 24 40 51 61
    * 12 12 14 19 26 58 60 55
    * 14 13 16 24 40 57 69 56
    * 14 17 22 29 51 87 80 62
    * 18 22 37 56 68 109 103 77
    * 24 35 55 64 81 104 113 92
    * 49 64 78 87 103 121 120 101
    * 72 92 95 98 112 100 103 99
    */
    public static int[][] quantization(double[][] DCTblock) {
        int[][] quantizationmatrix = { { 16,  11,  10,  16,  24,  40,  51,  61}, 
                                       { 12,  12,  14,  19,  26,  58,  60,  55},
                                       { 14,  13,  16,  24,  40,  57,  69,  56},
                                       { 14,  17,  22,  29,  51,  87,  80,  62},
                                       { 18,  22,  37,  56,  68, 109, 103,  77}, 
                                       { 24,  35,  55,  64,  81, 104, 113,  92},
                                       { 49,  64,  78,  87, 103, 121, 120, 101}, 
                                       { 72,  92,  95,  98, 112, 100, 103,  99} };
        int[][] quantized = new int[8][8];

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                quantized[i][j] = (int) Math.round(DCTblock[i][j] / quantizationmatrix[i][j]);
            }
        }
        return quantized;
    }

    /*
    * Zig-Zag traversal of quantized matrix for Huffman encoding
    */
    public static int[] zigzagTraveral(int[][] quantizedBlock) {
        int row;
        int col;
        int[] zigzagarr = new int[64];
        int index = 0;
        int[] order = {1, 8, 16, 9, 2, 3, 10, 17,
                       24, 32, 25, 18, 11, 4, 5, 12,
                       19, 26, 33, 40, 48, 41, 34, 27,
                       20, 13, 6, 7, 14, 21, 28, 35,
                       42, 49, 56, 57, 50, 43, 36, 29,
                       22, 15, 23, 30, 37, 44, 51, 58,
                       59, 52, 45, 38, 31, 39, 46, 53,
                       60, 61, 54, 47, 55, 62, 63};

        for (int i = 0; i < order.length; i++) {
            row = i / 8;
            col = i % 8;
            zigzagarr[index] += quantizedBlock[row][col];
            index++;
        }
        return zigzagarr;
    }

    /*
    * DC Encoding for DC coefficicents of quantized matrices
    */
    public static String DCEncoding(int DCcoefficient) {
        int category = 0;
        int vals = 0;
        int index = 0;
        int firstneg = 0;
        int firstpos = 0;
        int absDC = Math.abs(DCcoefficient);
        String encoded = "";

        // 0
        if (DCcoefficient == 0) {
            return "00";
        }

        // -1, 1
        else if (DCcoefficient == -1 || DCcoefficient == 1) {
            category = 1;
            firstneg = -1;
            firstpos = 1;
        }

        // -3, -2, 2, 3
        else if (absDC >= 2 && absDC <= 3) {
            category = 2;
            firstneg = -3;
            firstpos = 2;
        }

        // -7, -6, -5, -4, 4, 5, 6, 7
        else if (absDC >= 4 && absDC <= 7) {
            category = 3;
            firstneg = -7;
            firstpos = 4;
        }

        // -15, ..., -8, 8, ..., 15
        else if (absDC >= 8 && absDC <= 15) {
            category = 4;
            firstneg = -15;
            firstpos = 8;
        }

        // -31, ..., -16, 16, ..., 31
        else if (absDC >= 16 && absDC <= 31) {
            category = 5;
            firstneg = -31;
            firstpos = 16;
        }

        // -63, ..., -32, 32, ..., 63
        else if (absDC >= 32 && absDC <= 63) {
            category = 6;
            firstneg = -63;
            firstpos = 32;
        }

        // -127, ..., -64, 64, ..., 127
        else if (absDC >= 64 && absDC <= 127) {
            category = 7;
            firstneg = -127;
            firstpos = 64;
        }

        // -255, ..., -128, 128, ..., 255
        else if (absDC >= 128 && absDC <= 255) {
            category = 8;
            firstneg = -255;
            firstpos = 128;
        }

        // -511, ..., -256, 256, ..., 511
        else if (absDC >= 256 && absDC <= 511) {
            category = 9;
            firstneg = -511;
            firstpos = 256;
        }

        // -1023, ..., -512, 512, ..., 1023
        else if (absDC >= 512 && absDC <= 1023) {
            category = 10;
            firstneg = -1023;
            firstpos = 512;
        }

        // -2047, ..., -1024, 1024, ..., 2047
        else if (absDC >= 1024 && absDC <= 2047) {
            category = 11;
            firstneg = -2047;
            firstpos = 1024;
        }

        // -4095, ..., -2048, 2048, ..., 4095
        else if (absDC >= 2048 && absDC <= 4095) {
            category = 12;
            firstneg = -4095;
            firstpos = 2048;
        }

        // -8191, ..., -4096, 4096, ..., 8191
        else if (absDC >= 4096 && absDC <= 8191) {
            category = 13;
            firstneg = -8191;
            firstpos = 4096;
        }

        // -16383, ..., -8192, 8192, ..., 16383
        else if (absDC >= 8192 && absDC <= 16383) {
            category = 14;
            firstneg = -16283;
            firstpos = 8192;
        }

        // -32767, ..., -16384, 16384, ..., 32767
        else if (absDC >= 16384 && absDC <= 32767) {
            category = 15;
            firstneg = 16384;
            firstpos = 32767;
        }

        vals = (int) Math.pow(2, category);

        String[] bitreps = new String[vals];
        for (int i = 0; i < bitreps.length; i++) {
            bitreps[i] = Integer.toBinaryString(i);
        }

        if (DCcoefficient > 0) {
            index = (bitreps.length / 2) + (DCcoefficient - firstpos);
        }

        else if (DCcoefficient < 0) {
            index = Math.abs(DCcoefficient - firstneg);
        }

        encoded = bitreps[index];
        while (encoded.length() < category) {
            encoded = "0" + encoded;
        }
        
        return DCEncodingHelper(category) + encoded;
    }

    /*
    * Helper Method for DCEncoding method to return encoding of the category
    */
    public static String DCEncodingHelper(int categoryhelper) {
        String[] codewords = {"00", "010", "011", "100", "101", "110", "1110", "11110",
                              "111110", "1111110", "11111110", "111111110", "1111111110", 
                              "11111111110", "111111111110", "1111111111110"};
        return codewords[categoryhelper];
    }

    /*
    * Create and write output file
    */
    public static void outputFile() throws IOException {
        File output = new File("output.jfif");
        FileOutputStream jfifWriter = new FileOutputStream(output);
        FileWriter stringWriter = new FileWriter(output);

        // Start of image & marker/length
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, (byte)0x00, (byte)0x10});

        // Identifier

        // Version, units (dpi), density, & thumbnail
        jfifWriter.write(new byte[]{(byte)0x00, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x00, (byte)0x48,
                         (byte)0x00, (byte)0x48, (byte)0x00, (byte)0x00});

        // Define quanization table (luminance)
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xD8, (byte)0x00, (byte)0x43, (byte)0x00});

        // Define quantization table (chrominance)
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xD8, (byte)0x00, (byte)0x43, (byte)0x01});

        // Start of frame
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xC0, (byte)0x00, (byte)0x11, (byte)0x08, (byte)0x00,
                         (byte)0x02, (byte)0x00, (byte)0x06, (byte)0x03, (byte)0x01, (byte)0x22, (byte)0x00,
                         (byte)0x02, (byte)0x11, (byte)0x01, (byte)0x03, (byte)0x03, (byte)0x11, (byte)0x01});

        // Define Huffman table R
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xC4, (byte)0x00, (byte)0x15, (byte)0x00, (byte)0x01,
                         (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                         (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                         (byte)0x00, (byte)0x00, (byte)0x09});

        // Define Huffman table G
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xC4, (byte)0x00, (byte)0x19, (byte)0x10, (byte)0x01,
                         (byte)0x00, (byte)0x02, (byte)0x03, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                         (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00
                         (byte)0x00, (byte)0x00, (byte)0x06, (byte)0x08, (byte)0x38, (byte)0x88, (byte)0xB6});
        
        // Define Huffman table B
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xC4, (byte)0x00, (byte)0x15, (byte)0x01, (byte)0x01,
                         (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                         (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00
                         (byte)0x00, (byte)0x07, (byte)0x0A});

        // Define Huffman table luminance
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xC4, (byte)0x00, (byte)0x1C, (byte)0x11, (byte)0x00,
                         (byte)0x01, (byte)0x03, (byte)0x05, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                         (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                         (byte)0x00, (byte)0x08, (byte)0x00}, (byte)0x07, (byte)0xB8, (byte)0x09, (byte)0x38,
                         (byte)0x39, (byte)0x76, byte)0x78);

        // Start of scan
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xDA, (byte)0x00, (byte)0x0C, (byte)0x03, (byte)0x01,
                         (byte)0x00, (byte)0x02, (byte)0x11, (byte)0x03, (byte)0x11, (byte)0x00, (byte)0x3F,
                         (byte)0x00});

        // Image Data (Entropy encoded segment)

        // End of image
        jfifWriter.write(new byte[]{(byte)0xFF, (byte)0xD9});

        jfifWriter.close();

    }

    public static void main(String[] args) throws IOException {

        // Testing stuff

        // int[][] test = { {144, 139, 149, 155, 153, 155, 155, 155}, {151, 151, 151, 159, 156, 156, 156, 158}, {151, 156, 160, 162, 159, 151, 151, 151},
        //                  {158, 163, 161, 160, 160, 160, 160, 161}, {158, 160, 161, 162, 160, 155, 155, 156}, {161, 161, 161, 161, 160, 157, 157, 157},
        //                  {162, 162, 161, 160, 161, 157, 157, 157}, {162, 162, 161, 160, 163, 157, 158, 154} };

        // double[][] testoutput = DCT(test);

        // System.out.println("Input:\n");
        // for (int i = 0; i < 8; i++) {
        //     for (int j = 0; j < 8; j++) {
        //         System.out.println(test[i][j]);
        //     }
        // }
        // System.out.println("Output:\n");
        // for (int i = 0; i < 8; i++) {
        //     for (int j = 0; j < 8; j++) {
        //         System.out.println(testoutput[i][j]);
        //     }
        // }

        // double[][] test2 = { {-415, -33, -58, 35, 58, -51, -15, -12}, 
        //                      {5, -34, 49, 18, 27, 1, -5, 3},
        //                      {-46, 14, 80, -35, -50, 19, 7, -18},
        //                      {-53, 21, 34, -20, 2, 34, 36, 12},
        //                      {9, -2, 9, -5, -32, -15, 45, 37}, 
        //                      {-8, 15, -16, 7, -8, 11, 4, 7},
        //                      {19, -28, -2, -26, -2, 7, -44, -21}, 
        //                      {18, 25, -12, -44, 35, 48, -37, -3} };

        // int[][] test2out = quantization(test2);
        // System.out.println("Output:\n");
        // for (int i = 0; i < 8; i++) {
        //     for (int j = 0; j < 8; j++) {
        //         System.out.print(test2out[i][j] + " ");
        //     }
        //     System.out.println("");
        // }

        String testencoding = DCEncoding(-30);
        System.out.println(testencoding);

        // int[] zigzag = zigzagTraveral(test2out);
        // for (int i = 0; i < 64; i++) {
        //     System.out.print(zigzag[i] + " ");
        // }
        


        // end

        // Access image metadata (width, height, imagestart)
        RandomAccessFile raimage = new RandomAccessFile("MARBLES.bmp", "r");
        raimage.seek(10);
        int imagestart = read4Bytes(raimage);
        
        raimage.seek(18);
        int width  = read4Bytes(raimage);
        int height = read4Bytes(raimage);

        pixels = new int[height][width];

        raimage.seek(28);
        int bpp = raimage.read() + (raimage.read() << 8);

        // Certain formats not accepted
        // !! Should probably throw exception here !!
        if(bpp < 8 || bpp > 32 || bpp % 8 != 0) {
            System.out.println("This Bitmap is not supported");
            return;
        }

        // Read image pixels into 2d array
        raimage.seek(imagestart);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                pixels[row][col] = readNBytes(raimage, bpp / 8);
            }
        }

        outputFile();

    }
}
