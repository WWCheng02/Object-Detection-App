package com.example.myapplication;

import android.graphics.Matrix;
import android.media.Image;

import java.nio.ByteBuffer;

//utility class to manipulate image
public class ImageUtilities {
    @SuppressWarnings("unused")
    private static Logger LOGGER = new Logger();

    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges are normalized to eight bits
    private static int kMaxChannelValue = 262143;

    public static int[] convertYUVToARGB(final Image image, final int previewWidth, final int previewHeight) {
        final Image.Plane[] planes = image.getPlanes();
        byte[][] yuvBytes = fillBytes(planes);
        return ImageUtilities.convertYUV420ToARGB8888(yuvBytes[0], yuvBytes[1], yuvBytes[2], previewWidth,
                previewHeight, planes[0].getRowStride(), planes[1].getRowStride(), planes[1].getPixelStride());
    }

    private static byte[][] fillBytes(final Image.Plane[] planes) {
        byte[][] yuvBytes = new byte[3][];
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }

        return yuvBytes;
    }

    private static int YUV2RGB(int y, int u, int v) {
        // Adjust and check YUV values
        if ((y-16)<0)
            y=0;
        else
            y = y-16;

        u -= 128;
        v -= 128;

        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
        g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
        b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

        return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    }


    public static int[] convertYUV420ToARGB8888(byte[] yData, byte[] uData, byte[] vData, int width, int height, int yRowStride, int uvRowStride, int uvPixelStride) {
        int [] out = new int[width*height];
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int pY = yRowStride * j;
            int pUV = uvRowStride * (j >> 1);

            for (int i = 0; i < width; i++) {
                int uv_offset = pUV + (i >> 1) * uvPixelStride;

                out[yp++] = YUV2RGB(0xff & yData[pY + i], 0xff & uData[uv_offset], 0xff & vData[uv_offset]);
            }
        }
        return out;
    }

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     * sourceWidth- Width of source frame.
     * sourceHeight - Height of source frame.
     * destinationWidth - Width of destination frame.
     *  destinationHeight - Height of destination frame.
     * applyRotation - Amount of rotation to apply from one frame to another.
     */
    public static int getYUVByteSize(final int width, final int height) {
        // The luminance plane requires 1 byte per pixel.
        final int ySize = width * height;

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        final int uvSize = ((width + 1) / 2) * ((height + 1) / 2) * 2;

        return ySize + uvSize;
    }

    public static Matrix getTransformationMatrix(int sourceWidth, int sourceHeight, int destinationWidth, int destinationHeight, int applyRotation, boolean maintainAspectRatio) {
        Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
            }

            // Translate so center of image is at origin
            matrix.postTranslate(-sourceWidth / 2.0f, -sourceHeight / 2.0f);

            // Rotate around origin
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how much scaling is needed for each axis.
        boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        int inWidth = transpose ? sourceHeight : sourceWidth;
        int inHeight = transpose ? sourceWidth : sourceHeight;

        // Apply scaling if necessary
        if (inWidth != destinationWidth || inHeight != destinationHeight) {
            float scaleFactorX = destinationWidth / (float) inWidth;
            float scaleFactorY = destinationHeight / (float) inHeight;

            //If true, will ensure that scaling in x and y remains constant, cropping the image if necessary
            if (maintainAspectRatio) {
                // Scale by minimum factor so that destination is filled completely while maintaining the aspect ratio. Some image may fall off the edge
                float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill destination from source
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame
            matrix.postTranslate(destinationWidth / 2.0f, destinationHeight / 2.0f);
        }

        return matrix; //return transformation fulfilling the desired requirements
    }
}
