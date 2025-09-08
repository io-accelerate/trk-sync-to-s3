package io.accelerate.tracking.sync.upload;

import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

public class MultipartUploadResult {

    private final UploadPartRequest request;
    private final UploadPartResponse response;

    public MultipartUploadResult(UploadPartRequest request, UploadPartResponse response) {
        this.request = request;
        this.response = response;
    }

    /**
     * Returns the upload part request associated with this result.
     *
     * @return The UploadPartRequest.
     */
    public UploadPartRequest getRequest() {
        return request;
    }

    /**
     * Returns the upload part response containing details about the uploaded part.
     *
     * @return The UploadPartResponse.
     */
    public UploadPartResponse getResponse() {
        return response;
    }

}