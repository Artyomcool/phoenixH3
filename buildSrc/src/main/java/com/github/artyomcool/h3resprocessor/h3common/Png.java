package com.github.artyomcool.h3resprocessor.h3common;

import ar.com.hjg.pngj.*;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import ar.com.hjg.pngj.chunks.PngChunkTRNS;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class Png {

    public static DefInfo.Frame load(ByteBuffer buffer) {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        PngReader pngReader = new PngReader(new ByteArrayInputStream(data));
        PngChunkPLTE plte = pngReader.getMetadata().getPLTE();
        PngChunkTRNS trns = pngReader.getMetadata().getTRNS();

        int[] palette = null;
        if (plte != null) {
            palette = new int[256];
            int[] paletteAlpha = trns.getPalletteAlpha();
            for (int i = 0; i < plte.getNentries(); i++) {
                int alpha = i < paletteAlpha.length ? paletteAlpha[i] : 0xff;
                palette[i] = (alpha << 24) | plte.getEntry(i);
            }
        }

        int fullWidth = pngReader.imgInfo.cols;
        int fullHeight = pngReader.imgInfo.rows;
        IntBuffer pixels = IntBuffer.allocate(fullWidth * fullHeight);

        IImageLineSet<? extends IImageLine> rows = pngReader.readRows();
        for (int row = 0; row < rows.size(); row++) {
            IImageLine line = rows.getImageLine(row);
            ImageLineByte lineByte = line instanceof ImageLineByte ? (ImageLineByte) line : null;
            ImageLineInt lineInt = line instanceof ImageLineInt ? (ImageLineInt) line : null;

            for (int column = 0; column < pngReader.imgInfo.cols; column++) {
                if (plte == null) {
                    int pixelARGB8 = ImageLineHelper.getPixelARGB8(line, column);
                    pixels.put(pixelARGB8);
                } else {
                    int color = lineByte != null ? lineByte.getElem(column) : lineInt.getElem(column) & 0xff;
                    pixels.put(palette[color]);
                }
            }
        }

        return new DefInfo.Frame(fullWidth, fullHeight, pixels.flip());
    }

}
