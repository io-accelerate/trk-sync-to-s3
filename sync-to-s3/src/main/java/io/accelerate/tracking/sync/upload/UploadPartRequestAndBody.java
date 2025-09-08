package io.accelerate.tracking.sync.upload;

import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.core.sync.RequestBody;

public class UploadPartRequestAndBody {
    private final UploadPartRequest uploadPartRequest;
    private final RequestBody requestBody;

    public UploadPartRequestAndBody(UploadPartRequest uploadPartRequest, RequestBody requestBody) {
        this.uploadPartRequest = uploadPartRequest;
        this.requestBody = requestBody;
    }

    public UploadPartRequest getUploadPartRequest() {
        return uploadPartRequest;
    }

    public RequestBody getRequestBody() {
        return requestBody;
    }

    @Override
    public String toString() {
        return "UploadPartRequestAndBody{" +
               "uploadPartRequest=" + uploadPartRequest +
               ", requestBody=" + requestBody +
               '}';
    }
}
