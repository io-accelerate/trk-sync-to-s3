package io.accelerate.tracking.sync.sync.destination;

import io.accelerate.tracking.sync.upload.MultipartUploadResult;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.*;

import java.util.List;
import java.util.Optional;

public interface Destination {

    void startS3SyncSession() throws DestinationOperationException;

    void stopS3SyncSession() throws DestinationOperationException;

    List<String> filterUploadableFiles(List<String> paths) throws DestinationOperationException;

    /**
     * Initializes a multipart upload and returns the upload ID.
     *
     * @param remotePath The remote path where the object will be uploaded.
     * @return The upload ID of the multipart upload.
     * @throws DestinationOperationException If the initialization fails.
     */
    String initUploading(String remotePath) throws DestinationOperationException;

    /**
     * Retrieves the parts that have already been uploaded for a specific multipart upload.
     *
     * @param remotePath The remote path of the object.
     * @return A list of parts that have already been uploaded.
     * @throws DestinationOperationException If retrieving the uploaded parts fails.
     */
    List<Part> getAlreadyUploadedParts(String remotePath) throws DestinationOperationException;

    /**
     * Uploads a single part during a multipart upload.
     *
     * @param request The upload part request containing information about the part.
     *                
     * @return A result containing details about the uploaded part.
     * @throws DestinationOperationException If uploading the part fails.
     */
    MultipartUploadResult uploadMultiPart(UploadPartRequest request, RequestBody requestBody) throws DestinationOperationException;

    /**
     * Completes a multipart upload by finalizing all uploaded parts.
     *
     * @param remotePath The remote path of the object.
     * @param eTags      The list of completed parts with their ETags.
     * @param uploadId   The upload ID of the multipart upload to finalize.
     * @throws DestinationOperationException If the completion fails.
     */
    void commitMultipartUpload(String remotePath, List<CompletedPart> eTags, String uploadId) throws DestinationOperationException;

    /**
     * Creates an upload part request for an object in S3.
     *
     * @param remotePath The remote path of the object.
     * @return The upload part request.
     * @throws DestinationOperationException If creating the request fails.
     */
    UploadPartRequest createUploadPartRequest(String remotePath) throws DestinationOperationException;

    /**
     * Retrieves the name of the S3 bucket being used as the destination.
     *
     * @return The name of the S3 bucket.
     */
    String getBucketName();


    /**
     * Retrieves the upload ID of a potentially existing multipart upload.
     *
     * @param remotePath The remote path of the object.
     * @return An Optional containing the upload ID if an existing upload is found,
     * or an empty Optional if no such upload exists.
     */
    Optional<String> getExistingUploadId(String remotePath) throws DestinationOperationException;
}