package io.accelerate.tracking.sync.upload;

import io.accelerate.tracking.sync.sync.destination.DestinationOperationException;
import org.slf4j.Logger;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsResponse;
import software.amazon.awssdk.services.s3.model.MultipartUpload;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

public class MultipartUploadFinder {
    private static final Logger log = getLogger(MultipartUploadFinder.class);

    private final S3Client awsClient; // Use AWS SDK v2 S3Client
    private final String bucket;
    private final String prefix;

    public MultipartUploadFinder(S3Client awsClient, String bucket, String prefix) {
        this.awsClient = awsClient;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    public List<MultipartUpload> getAlreadyStartedMultipartUploads() throws DestinationOperationException {
        // Create the initial request
        ListMultipartUploadsRequest uploadsRequest = createListMultipartUploadsRequest();
        ListMultipartUploadsResponse multipartUploadResponse = listMultipartUploads(uploadsRequest);

        // Use Stream API for processing paginated responses
        Stream<ListMultipartUploadsResponse> stream = Stream.of(multipartUploadResponse)
                .flatMap(response -> {
                    try {
                        return this.streamNextListing(response);
                    } catch (DestinationOperationException ex) {
                        log.error("Failed to stream next listing: bucket={} prefix={} uploadIdMarker={}",
                                bucket, prefix, response.uploadIdMarker(), ex);
                        return null;
                    }
                }).filter(Objects::nonNull);

        // Flatten and map the results to a list of MultipartUpload
        return stream
                .map(ListMultipartUploadsResponse::uploads)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private ListMultipartUploadsRequest createListMultipartUploadsRequest() {
        // Create a request for multipart uploads with the bucket and prefix
        return ListMultipartUploadsRequest.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();
    }

    private ListMultipartUploadsResponse listMultipartUploads(ListMultipartUploadsRequest request) throws DestinationOperationException {
        try {
            // AWS SDK v2: Use S3Client to list multipart uploads
            return awsClient.listMultipartUploads(request);
        } catch (AwsServiceException ex) {
            throw new DestinationOperationException(
                    "Failed to list multipart uploads: bucket=" + request.bucket() + ", prefix=" + request.prefix(),
                    ex
            );
        }
    }

    private Stream<ListMultipartUploadsResponse> streamNextListing(ListMultipartUploadsResponse response) throws DestinationOperationException {
        // If there are no more pages, return the current response
        if (!response.isTruncated()) {
            return Stream.of(response);
        }

        // Otherwise, retrieve the next set of uploads
        ListMultipartUploadsRequest nextRequest = createListMultipartUploadsRequest()
                .toBuilder() // Create a new request builder
                .keyMarker(response.nextKeyMarker())
                .uploadIdMarker(response.nextUploadIdMarker())
                .build();

        ListMultipartUploadsResponse nextResponse = listMultipartUploads(nextRequest);

        Stream<ListMultipartUploadsResponse> head = Stream.of(response);
        Stream<ListMultipartUploadsResponse> tail = streamNextListing(nextResponse);

        // Concatenate the current response with subsequent responses
        return Stream.concat(head, tail);
    }
}