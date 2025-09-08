package io.accelerate.tracking.sync.upload;

import io.accelerate.tracking.sync.helpers.ByteHelper;
import io.accelerate.tracking.sync.helpers.ChecksumHelper;
import io.accelerate.tracking.sync.helpers.FileHelper;
import io.accelerate.tracking.sync.sync.destination.Destination;
import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import io.accelerate.tracking.sync.sync.progress.ProgressListener;
import org.slf4j.Logger;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

public class MultipartUploadFile {
    private static final Logger log = getLogger(MultipartUploadFile.class);

    // Minimum part size is 5 MB
    private static final int MINIMUM_PART_SIZE = 5 * 1024 * 1024;

    private final File file;

    private final String remotePath;

    private final Destination destination;

    private String uploadId;

    private long uploadedSize = 0;

    private List<Part> alreadyUploadedParts;

    private Set<Integer> failedMiddlePartNumbers;

    private int nextPartToUploadIndex = 1;

    private List<CompletedPart> completedParts;

    private boolean isWritingFinished;

    public MultipartUploadFile(File file, String remotePath, Destination destination) throws DestinationOperationException {
        this.file = file;
        this.remotePath = remotePath;
        this.destination = destination;
        init();
    }

    public File getFile() {
        return file;
    }

    public String getUploadId() {
        return uploadId;
    }

    public List<CompletedPart> getCompletedParts() {
        return completedParts;
    }

    public Set<Integer> getFailedMiddlePartNumbers() {
        return failedMiddlePartNumbers;
    }

    private void init() throws DestinationOperationException {
        Optional<String> maybeExistingUploadId = destination.getExistingUploadId(remotePath);
        isWritingFinished = !FileHelper.lockFileExists(file);

        if (maybeExistingUploadId.isEmpty()) {
            uploadId = destination.initUploading(remotePath);
            alreadyUploadedParts = Collections.emptyList();
            failedMiddlePartNumbers = Collections.emptySet();
        } else {
            uploadId = maybeExistingUploadId.get();
            alreadyUploadedParts = destination.getAlreadyUploadedParts(remotePath);
            failedMiddlePartNumbers = MultipartUploadHelper.getFailedMiddlePartNumbers(alreadyUploadedParts);
            uploadedSize = MultipartUploadHelper.getUploadedSize(alreadyUploadedParts);
            nextPartToUploadIndex = MultipartUploadHelper.getLastPartIndex(alreadyUploadedParts) + 1;
        }

        completedParts = MultipartUploadHelper.convertPartsToCompletedParts(alreadyUploadedParts);
    }

    public void validateUploadedFileSize() {
        if (file.length() < uploadedSize) {
            throw new IllegalStateException(
                    "Already uploaded size of file " + file.getName()
                    + " is greater than the actual file size. "
                    + "The file might have been modified and cannot be uploaded now."
            );
        }
    }

    public BufferedInputStream createBufferedInputStreamFromFile() throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(file));
    }

    public UploadPartRequestAndBody getUploadPartRequestForData(byte[] nextPart, int partNumber) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .uploadId(uploadId)
                .bucket(destination.getBucketName()) // Assuming destination has bucket name
                .key(remotePath)
                .partNumber(partNumber)
                .contentMD5(ChecksumHelper.digest(nextPart, "MD5"))
                .contentLength((long) nextPart.length)
                .build();
        RequestBody requestBody = RequestBody.fromInputStream(ByteHelper.createInputStream(nextPart), nextPart.length);
        return new UploadPartRequestAndBody(uploadPartRequest, requestBody);
    }

    public void commitIfFinishedWriting() throws DestinationOperationException {
        if (isWritingFinished) {
            destination.commitMultipartUpload(remotePath, completedParts, uploadId);
        }
    }

    public Stream<UploadPartRequestAndBody> streamUploadPartRequestForFailedParts() {
        return getFailedMiddlePartNumbers()
                .stream()
                .map(partNumber -> {
                    try {
                        byte[] partData = readPart(partNumber);
                        UploadPartRequestAndBody request = getUploadPartRequestForData(partData, partNumber);
                        uploadedSize += partData.length;
                        return request;
                    } catch (IOException ex) {
                        log.error("Cannot upload part " + partNumber, ex);
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }

    public byte[] readPart(int partNumber) throws IOException {
        return ByteHelper.readPart(partNumber, file);
    }

    public void notifyStart(ProgressListener listener) {
        listener.uploadFileStarted(file, uploadId, uploadedSize);
    }

    public void notifyFinish(ProgressListener listener) {
        listener.uploadFileFinished(file);
    }

    public Stream<UploadPartRequestAndBody> streamUploadPartRequestForIncompleteParts() throws IOException, DestinationOperationException {
        try (InputStream inputStream = createBufferedInputStreamFromFile()) {
            byte[] nextPart = ByteHelper.getNextPartFromInputStream(inputStream, uploadedSize, isWritingFinished);
            int partSize = nextPart.length;
            List<UploadPartRequestAndBody> requests = new ArrayList<>();
            while (partSize > 0) {
                boolean isLastPart = isWritingFinished && partSize < MINIMUM_PART_SIZE;
                UploadPartRequestAndBody request = getUploadPartRequestForData(nextPart, nextPartToUploadIndex);
                nextPartToUploadIndex++;
                requests.add(request);
                nextPart = ByteHelper.getNextPartFromInputStream(inputStream, 0, isWritingFinished);
                partSize = nextPart.length;
            }
            return requests.stream();
        }
    }
}