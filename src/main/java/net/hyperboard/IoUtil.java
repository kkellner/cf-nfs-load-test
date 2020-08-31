package net.hyperboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class IoUtil {

    private static Logger LOGGER = LogManager.getLogger();

    public static long copyFileUsingStreams(File srcFile, File targetFile, int bufferSize) throws IOException {

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("copyFileUsingStreams " + srcFile + " -> " + targetFile + ", bufferSize=" + bufferSize);

        long bytesCopied = 0L;
        byte[] buffer = new byte[bufferSize];

        try (FileInputStream in = new FileInputStream(srcFile)) {
            try (FileOutputStream out = new FileOutputStream(targetFile)) {
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    bytesCopied += bytesRead;
                }
            }
        }

        return bytesCopied;
    }

    public static long copyFileUsingBufferedStreams(File srcFile, File targetFile, int bufferSize) throws IOException {

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("copyFileUsingBufferedStreams " + srcFile + " -> " + targetFile + ", bufferSize=" + bufferSize);

        long bytesCopied = 0L;
        byte[] buffer = new byte[bufferSize];

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(srcFile))) {
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    bytesCopied += bytesRead;
                }
            }
        }

        return bytesCopied;
    }

    public static long copyFileUsingChannelWithDirectByteBuffer(File srcFile, File targetFile, int bufferSize) throws IOException {

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("copyFileUsingChannelWithDirectByteBuffer " + srcFile + " -> " + targetFile + ", bufferSize=" + bufferSize);

        long bytesCopied = 0L;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize); // direct ByteBuffer for native I/O

        try (FileChannel inputChannel = new FileInputStream(srcFile).getChannel()) {
            try (FileChannel outputChannel = new FileOutputStream(targetFile).getChannel()) {
                int bytesRead;
                while ((bytesRead = inputChannel.read(byteBuffer)) > 0) {
                    // flip the buffer which set the limit to current position, and position to 0.
                    byteBuffer.flip();
                    outputChannel.write(byteBuffer);
                    // Clear for the next read
                    byteBuffer.clear();
                    bytesCopied += bytesRead;
                }
            }
        }

        return bytesCopied;
    }

    public static long copyFileUsingInChannelOutBufferedStream(File srcFile, File targetFile, int bufferSize) throws IOException {

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("copyFileUsingInChannelOutBufferedStream " + srcFile + " -> " + targetFile + ", bufferSize=" + bufferSize);

        long bytesCopied = 0L;
        byte[] buffer = new byte[bufferSize];

        try (FileChannel inputChannel = new FileInputStream(srcFile).getChannel()) {
            int length = (int) inputChannel.size();
            MappedByteBuffer mb = inputChannel.map(FileChannel.MapMode.READ_ONLY, 0, length);
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                int bytesRead;
                while (mb.hasRemaining()) {
                    bytesRead = Math.min(mb.remaining(), bufferSize);
                    mb.get(buffer, 0, bytesRead);
                    out.write(buffer, 0, bytesRead);
                    bytesCopied += bytesRead;
                }
            }
        }

        return bytesCopied;
    }

    public static long copyFileUsingChannelTransferFrom(File srcFile, File targetFile) throws IOException {

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("copyFileUsingChannelTransferFrom " + srcFile + " -> " + targetFile);

        long bytesCopied;
        try (FileChannel inputChannel = new FileInputStream(srcFile).getChannel()) {
            try (FileChannel outputChannel = new FileOutputStream(targetFile).getChannel()) {
                bytesCopied = inputChannel.size();
                outputChannel.transferFrom(inputChannel, 0, bytesCopied);
            }
        }

        return bytesCopied;
    }

    public static long copyFileUsingChannelTransferTo(File srcFile, File targetFile) throws IOException {

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("copyFileUsingChannelTransferTo " + srcFile + " -> " + targetFile);

        long bytesCopied;
        try (FileChannel inputChannel = new FileInputStream(srcFile).getChannel()) {
            try (FileChannel outputChannel = new FileOutputStream(targetFile).getChannel()) {
                bytesCopied = inputChannel.size();
                inputChannel.transferTo(0, bytesCopied, outputChannel);
            }
        }

        return bytesCopied;
    }
}