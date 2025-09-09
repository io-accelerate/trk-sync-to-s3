package io.accelerate.tracking.sync.testframework.rules;

import ch.qos.logback.core.encoder.ByteArrayUtil;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;

public class TemporarySyncFolder {
    public static final int PART_SIZE_IN_BYTES = 5 * 1024 * 1024;
    public static final int ONE_MEGABYTE = 1024 * 1024;

    private final Path tempDir;

    public TemporarySyncFolder(Path tempDir) {
        this.tempDir = tempDir;
    }

    //~~~ Modifiers

    public void addFileFromResources(String fileName) throws IOException {
        addFile("src/test/resources/" + fileName);
    }

    @SuppressWarnings("WeakerAccess")
    public void addFile(String sourceFile) throws IOException {
        Path source = Paths.get(sourceFile);
        Files.copy(source, getFolderPath().resolve(source.getFileName()));
    }

    public Path getFilePath(String fileName) {
        return getFolderPath().resolve(fileName);
    }

    public void lock(String fileName) throws IOException {
        Files.write(getLockPathFor(fileName), new byte[]{0});
    }

    public void unlock(String fileName) throws IOException {
        Files.deleteIfExists(getLockPathFor(fileName));
    }

    @SuppressWarnings("WeakerAccess")
    public Path getLockPathFor(String fileName) {
        return getFolderPath().resolve(fileName + ".lock");
    }


    public void writeBytesToFile(String fileName, int sizeInBytes) {
        File targetFile = getFolderPath().resolve(fileName).toFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(targetFile, true)) {
            byte[] part = new byte[sizeInBytes];
            new Random().nextBytes(part);
            fileOutputStream.write(part);
        } catch (IOException e) {
            throw new RuntimeException("Test interrupted.", e);
        }
    }

    //~~~ Accessors

    public Path getFolderPath() {
        return tempDir;
    }

    public Map<Integer, String> getPartsHashes(String fileName) throws IOException, NoSuchAlgorithmException {
        Path targetFile = getFolderPath().resolve(fileName);
        long fileSize = Files.size(targetFile);
        MessageDigest digest = MessageDigest.getInstance("MD5");
        Map<Integer, String> result = new HashMap<>(3, 2);
        try (FileInputStream fileInputStream = new FileInputStream(targetFile.toFile())) {
            long read = 0;
            for (int i = 1; read < fileSize; i++) {
                int chunkSize = fileSize - read > PART_SIZE_IN_BYTES ? PART_SIZE_IN_BYTES : (int) (fileSize - read);
                byte[] chunk = IOUtils.readFully(fileInputStream, chunkSize);
                String hash = HexFormat.of().formatHex(digest.digest(chunk));
                result.put(i, hash);
                read += chunk.length;
            }
        }
        return result;
    }

    public String getCompleteFileMD5(String fileName) throws IOException, NoSuchAlgorithmException {
        return concatenateHashesAndHashAgain(getPartsHashes(fileName));
    }

    //~~~~ Utils for working with multipart uploads

    private String concatenateHashesAndHashAgain(Map<Integer, String> partHashes) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hashesData = partHashes.keySet().stream()
                .sorted()
                .map(partHashes::get)
                .map(ByteArrayUtil::hexStringToByteArray)
                .reduce(new byte[0], this::merge);
        return ByteArrayUtil.toHexString(digest.digest(hashesData));
    }

    private byte[] merge(byte[] acc, byte[] hash) {
        int size = acc.length + hash.length;
        byte[] result = new byte[size];
        System.arraycopy(acc, 0, result, 0, acc.length);
        System.arraycopy(hash, 0, result, acc.length, hash.length);
        return result;
    }
}
