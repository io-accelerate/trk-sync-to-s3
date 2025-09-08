package io.accelerate.tracking.sync.upload;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import static org.mockito.Mockito.mock;

public class MultipartUploadResultTest {

    @Test
    public void test() {
        UploadPartRequest request = mock(UploadPartRequest.class);
        UploadPartResponse result = mock(UploadPartResponse.class);
        MultipartUploadResult res = new MultipartUploadResult(request, result);
        Assertions.assertEquals(res.getRequest(), request);
        Assertions.assertEquals(res.getResponse(), result);
    }   
}
