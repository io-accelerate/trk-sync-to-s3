package io.accelerate.tracking.sync.upload;

import io.accelerate.tracking.sync.helpers.FileHelper;
import io.accelerate.tracking.sync.helpers.FormattingHelper;
import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import io.accelerate.tracking.sync.sync.progress.ProgressListener;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static software.amazon.awssdk.services.s3.model.ChecksumAlgorithm.SHA256;

public class MultipartUploadingStrategy implements UploadingStrategy, Closeable {

    private static final long PART_SIZE_BYTES = 5L * 1024 * 1024; // 5 MiB
    private static final int MAX_CONCURRENCY = 4;
    private static final int STREAM_CHUNK_SIZE = 256 * 1024;      // 256 KiB

    private final S3AsyncClient s3;
    private final String bucket;
    private final String prefix; // logical "folder" prefix like "foo/bar/"
    private volatile ProgressListener listener;

    public MultipartUploadingStrategy(S3AsyncClient s3, String bucket, String prefix) {
        this.s3 = Objects.requireNonNull(s3, "s3");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public void setListener(ProgressListener listener) {
        this.listener = listener;
    }

    @Override
    public void upload(File file, String remoteFileName) throws DestinationOperationException, IOException {
        Objects.requireNonNull(file, "file");
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File does not exist or is not a regular file: " + file);
        }
        Objects.requireNonNull(remoteFileName, "remoteFileName");

        final String key = FormattingHelper.buildKey(this.prefix, remoteFileName);

        // 0) Preflight: only upload new files. If key already exists, exit early.
        if (objectExists(bucket, key)) {
            return;
        }

        final Path source = file.toPath();
        final long initialSize = Files.size(source);
        final boolean lockExists = FileHelper.lockFileExists(file);
        final long fullPartsAvailable = initialSize / PART_SIZE_BYTES;
        final long totalPartsIfUnlocked = (initialSize + PART_SIZE_BYTES - 1) / PART_SIZE_BYTES;

        // 1) Resolve or create MPU for this key (track if created-with-SHA256)
        final UploadSession session = resolveOrCreateUploadId(bucket, key);

        // 2) Discover already uploaded parts and bytes
        final List<Part> existingParts = listAllParts(bucket, key, session.uploadId());
        final Map<Integer, String> existingEtagsByPart =
                existingParts.stream().collect(Collectors.toMap(
                        Part::partNumber,
                        p -> FormattingHelper.sanitizeETag(p.eTag())        // <--- strip quotes here
                ));
        final long alreadyUploadedBytes = existingParts.stream().mapToLong(Part::size).sum();

        ProgressListener l = this.listener;
        if (l != null) {
            try { l.uploadFileStarted(file, session.uploadId(), alreadyUploadedBytes); } catch (Throwable ignored) {}
        }

        // 3) Decide parts for this run
        final int maxPartNumberThisRun;
        final long lastPartSizeThisRun;
        if (lockExists) {
            maxPartNumberThisRun = (int) fullPartsAvailable; // only full parts while locked
            lastPartSizeThisRun = PART_SIZE_BYTES;
        } else {
            maxPartNumberThisRun = (int) totalPartsIfUnlocked; // include tail
            if (initialSize == 0) {
                if (this.listener != null) {
                    try { this.listener.uploadFileFinished(file); } catch (Throwable ignored) {}
                }
                return;
            }
            long remainder = initialSize - (PART_SIZE_BYTES * (totalPartsIfUnlocked - 1));
            lastPartSizeThisRun = remainder == 0 ? PART_SIZE_BYTES : remainder;
        }

        final List<Integer> targetPartNumbers = IntStream.rangeClosed(1, maxPartNumberThisRun)
                .boxed().collect(Collectors.toList());
        final List<Integer> missingPartNumbers = targetPartNumbers.stream()
                .filter(pn -> !existingEtagsByPart.containsKey(pn))
                .collect(Collectors.toList());

        final List<CompletedPart> newCompletedParts = Collections.synchronizedList(new ArrayList<>());
        final List<CompletableFuture<?>> inFlight = new ArrayList<>();
        final AtomicLong uploadedSoFar = new AtomicLong(alreadyUploadedBytes);

        try {
            // 4) Upload missing parts, respecting concurrency
            for (Integer partNumber : missingPartNumbers) {
                while (inFlight.size() >= MAX_CONCURRENCY) {
                    CompletableFuture.anyOf(inFlight.toArray(new CompletableFuture[0])).join();
                    inFlight.removeIf(CompletableFuture::isDone);
                }

                final long offset = (long) (partNumber - 1) * PART_SIZE_BYTES;
                final long size = (partNumber == maxPartNumberThisRun) ? lastPartSizeThisRun : PART_SIZE_BYTES;
                if (size <= 0) continue;

                // Compute Base64(SHA-256) for this slice, include in request
                final String sha256Base64 = computeSha256Base64(source, offset, size);

                UploadPartRequest req = UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(session.uploadId())
                        .partNumber(partNumber)
                        .contentLength(size)
                        .checksumSHA256(sha256Base64) // per-part checksum
                        .build();

                Publisher<ByteBuffer> slice = new FileSlicePublisher(source, offset, size, STREAM_CHUNK_SIZE);
                AsyncRequestBody body = AsyncRequestBody.fromPublisher(slice);

                CompletableFuture<UploadPartResponse> fut = s3.uploadPart(req, body).thenApply(resp -> {
                    newCompletedParts.add(
                            CompletedPart.builder()
                                    .partNumber(partNumber)
                                    .checksumSHA256(sha256Base64)
                                    .eTag(FormattingHelper.sanitizeETag(resp.eTag()))   // <--- strip quotes here
                                    .build()
                    );
                    long current = uploadedSoFar.addAndGet(size);
                    ProgressListener pl = this.listener;
                    if (pl != null) {
                        try { pl.uploadFileProgress(session.uploadId(), current); } catch (Throwable ignored) {}
                    }
                    return resp;
                });

                inFlight.add(fut);
            }

            if (!inFlight.isEmpty()) {
                CompletableFuture.allOf(inFlight.toArray(new CompletableFuture[0])).join();
            }

            // 5) If locked, do not complete; exit so scheduler can run later
            if (lockExists) {
                return;
            }

            // 6) Complete: combine existing + new parts in order 1..maxPartNumberThisRun
            List<CompletedPart> allParts = new ArrayList<>(maxPartNumberThisRun);
            for (int pn = 1; pn <= maxPartNumberThisRun; pn++) {
                final int partNum = pn; // effectively final for lambda
                String etag = existingEtagsByPart.get(partNum);
                if (etag != null) {
                    allParts.add(CompletedPart.builder().partNumber(partNum).eTag(FormattingHelper.sanitizeETag(etag)).build());
                } else {
                    CompletedPart p = newCompletedParts.stream()
                            .filter(cp -> cp.partNumber() == partNum)
                            .findFirst()
                            .orElse(null);
                    if (p == null) {
                        // Missing part. Exit without completing so the next run can recover.
                        return;
                    }
                    allParts.add(p);
                }
            }

            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(session.uploadId())
                    .multipartUpload(CompletedMultipartUpload.builder().parts(allParts).build())
                    .build()
            ).join();

            if (this.listener != null) {
                try { this.listener.uploadFileFinished(file); } catch (Throwable ignored) {}
            }

        } catch (Throwable t) {
            // Do not abort to preserve resumability across scheduled runs
            if (t instanceof DestinationOperationException doe) throw doe;
            throw new DestinationOperationException("Multipart upload failed for key " + key, t);
        }
    }

    /** True if object already exists (HEAD 200). False on 404. Propagates other errors. */
    private boolean objectExists(String bucket, String key) throws DestinationOperationException {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).get();
            return true;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchKeyException) return false;
            if (cause instanceof S3Exception s3e && s3e.statusCode() == 404) return false;
            throw new DestinationOperationException("HeadObject failed for key " + key, cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new DestinationOperationException("Interrupted during HeadObject for key " + key, ie);
        }
    }

    /** UploadSession: uploadId plus whether we created it with SHA-256. */
    private record UploadSession(String uploadId, boolean createdWithSha256) {}

    /** Reuse existing MPU for this key if present, else create a new one. Filtered by exact-key prefix. */
    private UploadSession resolveOrCreateUploadId(String bucket, String key) throws DestinationOperationException {
        try {
            ListMultipartUploadsResponse listResp = s3
                    .listMultipartUploads(ListMultipartUploadsRequest.builder()
                            .bucket(bucket)
                            .prefix(key) // narrowed to this key
                            .build())
                    .get();

            for (MultipartUpload u : listResp.uploads()) {
                if (key.equals(u.key())) {
                    return new UploadSession(u.uploadId(), false);
                }
            }

            // New MPU: opt in to SHA-256 so S3 tracks an object-level checksum
            CreateMultipartUploadResponse createResp = s3
                    .createMultipartUpload(CreateMultipartUploadRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .checksumAlgorithm(SHA256)
                            .build())
                    .get();
            return new UploadSession(createResp.uploadId(), true);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new DestinationOperationException("Interrupted resolving uploadId", ie);
        } catch (ExecutionException ee) {
            throw new DestinationOperationException("Failed resolving uploadId", ee.getCause());
        }
    }

    /** Fetch all already uploaded parts for a given uploadId using async client, blocking loop. */
    private List<Part> listAllParts(String bucket, String key, String uploadId) throws DestinationOperationException {
        try {
            List<Part> parts = new ArrayList<>();
            Integer partNumberMarker = null;
            boolean truncated;
            do {
                ListPartsRequest.Builder b = ListPartsRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId);
                if (partNumberMarker != null) {
                    b = b.partNumberMarker(partNumberMarker);
                }
                ListPartsResponse resp = s3.listParts(b.build()).get();
                parts.addAll(resp.parts());
                truncated = Boolean.TRUE.equals(resp.isTruncated());
                partNumberMarker = resp.nextPartNumberMarker();
            } while (truncated);
            return parts;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new DestinationOperationException("Interrupted listing parts", ie);
        } catch (ExecutionException ee) {
            throw new DestinationOperationException("Failed listing parts", ee.getCause());
        }
    }

    @Override
    public void close() {
        // If this class owns the client, close it here.
    }

    /** Compute Base64(SHA-256) of a file slice [start, start+size). */
    private static String computeSha256Base64(Path file, long start, long size) {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ch.position(start);
            long remaining = size;
            ByteBuffer buf = ByteBuffer.allocate(256 * 1024);
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.capacity(), remaining);
                buf.clear();
                buf.limit(toRead);
                int n = ch.read(buf);
                if (n < 0) break;
                buf.flip();
                md.update(buf);
                remaining -= n;
            }
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("Failed computing SHA-256 for slice", e);
        }
    }

    /**
     * Streams a contiguous slice of a file as ByteBuffer chunks.
     * Opens the channel at subscribe-time and closes it on completion or error.
     * Honors request(n) for basic backpressure.
     */
    private static final class FileSlicePublisher implements Publisher<ByteBuffer> {
        private final Path file;
        private final long start;
        private final long size;
        private final int chunkSize;

        FileSlicePublisher(Path file, long start, long size, int chunkSize) {
            this.file = Objects.requireNonNull(file, "file");
            this.start = start;
            this.size = size;
            this.chunkSize = Math.max(1, chunkSize);
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> sub) {
            Objects.requireNonNull(sub, "subscriber");
            sub.onSubscribe(new FileSliceSubscription(sub, file, start, size, chunkSize));
        }

        private static final class FileSliceSubscription implements Subscription {
            private final Subscriber<? super ByteBuffer> sub;
            private final Path file;
            private final long endExclusive;
            private final int chunkSize;

            private FileChannel channel;
            private long position;
            private volatile boolean done;
            private volatile boolean cancelled;
            private volatile long requested;
            private static final AtomicLongFieldUpdater<FileSliceSubscription> REQUESTED_UPDATER =
                    AtomicLongFieldUpdater.newUpdater(FileSliceSubscription.class, "requested");

            FileSliceSubscription(Subscriber<? super ByteBuffer> sub, Path file, long start, long size, int chunkSize) {
                this.sub = sub;
                this.file = file;
                this.position = start;
                this.endExclusive = start + size;
                this.chunkSize = chunkSize;
            }

            @Override
            public void request(long n) {
                if (cancelled || done) return;
                if (n <= 0) {
                    onError(new IllegalArgumentException("non-positive request"));
                    return;
                }
                addRequest(n);
                drain();
            }

            @Override
            public void cancel() {
                cancelled = true;
                closeQuietly();
            }

            private void drain() {
                if (cancelled || done) return;
                try {
                    if (channel == null) {
                        channel = FileChannel.open(file, StandardOpenOption.READ);
                        channel.position(position);
                    }
                    while (requested > 0 && position < endExclusive && !cancelled) {
                        int toRead = (int) Math.min(chunkSize, endExclusive - position);
                        ByteBuffer buf = ByteBuffer.allocate(toRead);
                        int read = channel.read(buf);
                        if (read < 0) {
                            done = true;
                            closeQuietly();
                            sub.onComplete();
                            return;
                        }
                        buf.flip();
                        position += read;
                        produced(1);
                        sub.onNext(buf);
                    }
                    if (position >= endExclusive && !done && !cancelled) {
                        done = true;
                        closeQuietly();
                        sub.onComplete();
                    }
                } catch (Throwable t) {
                    onError(t);
                }
            }

            private void onError(Throwable t) {
                if (done) return;
                done = true;
                closeQuietly();
                sub.onError(t);
            }

            private void addRequest(long n) {
                long prev, next;
                do {
                    prev = requested;
                    next = prev + n;
                    if (next < 0) next = Long.MAX_VALUE;
                } while (!REQUESTED_UPDATER.compareAndSet(this, prev, next));
            }

            private void produced(long n) {
                long prev, next;
                do {
                    prev = requested;
                    next = prev - n;
                } while (!REQUESTED_UPDATER.compareAndSet(this, prev, next));
            }

            private void closeQuietly() {
                if (channel != null) {
                    try { channel.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

}
