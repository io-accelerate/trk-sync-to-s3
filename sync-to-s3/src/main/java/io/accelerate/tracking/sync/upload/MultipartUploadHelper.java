package io.accelerate.tracking.sync.upload;

import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.Part;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class MultipartUploadHelper {

    private MultipartUploadHelper() {
    }

    /**
     * Converts a list of already uploaded {@link Part} objects to a list of {@link CompletedPart} objects
     * to be used for completing a multipart upload.
     *
     * @param parts List of uploaded parts.
     * @return A list of {@link CompletedPart}.
     */
    static List<CompletedPart> convertPartsToCompletedParts(List<Part> parts) {
        return Optional.ofNullable(parts)
                .orElse(Collections.emptyList())
                .stream()
                .map(part -> CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(part.eTag())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Calculates the total uploaded size from a list of uploaded {@link Part} objects.
     *
     * @param parts List of parts.
     * @return The total size of uploaded parts in bytes.
     */
    static long getUploadedSize(List<Part> parts) {
        return Optional.ofNullable(parts)
                .orElse(Collections.emptyList())
                .stream()
                .mapToLong(Part::size)
                .sum();
    }

    /**
     * Gets the last uploaded part index from a list of {@link Part} objects.
     *
     * @param parts List of parts.
     * @return The part number of the last uploaded part, or 0 if no parts are uploaded.
     */
    static int getLastPartIndex(List<Part> parts) {
        return Optional.ofNullable(parts)
                .orElse(Collections.emptyList())
                .stream()
                .mapToInt(Part::partNumber)
                .max()
                .orElse(0);
    }

    /**
     * Identifies the missing (i.e., failed or skipped) part numbers in the uploaded sequence.
     *
     * @param parts List of uploaded parts.
     * @return A set of missing part numbers.
     */
    static Set<Integer> getFailedMiddlePartNumbers(List<Part> parts) {
        AtomicInteger lastPartNumber = new AtomicInteger(0);
        Set<Integer> uploadedParts = Optional.ofNullable(parts)
                .orElse(Collections.emptyList())
                .stream()
                .map(Part::partNumber)
                .peek(partNumber -> {
                    if (lastPartNumber.get() < partNumber) {
                        lastPartNumber.set(partNumber);
                    }
                })
                .collect(Collectors.toSet());

        return IntStream.range(1, lastPartNumber.get())
                .filter(partNumber -> !uploadedParts.contains(partNumber))
                .boxed()
                .collect(Collectors.toSet());
    }
}