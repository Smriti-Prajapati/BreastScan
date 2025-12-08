package com.example.breastscan;

import android.content.Context;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FileUtil {

    public static MappedByteBuffer loadMappedFile(Context context, String modelName) throws IOException {
        FileInputStream fis = new FileInputStream(context.getAssets().openFd(modelName).getFileDescriptor());
        FileChannel fileChannel = fis.getChannel();
        long startOffset = context.getAssets().openFd(modelName).getStartOffset();
        long declaredLength = context.getAssets().openFd(modelName).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
