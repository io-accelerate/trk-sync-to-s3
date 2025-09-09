package io.accelerate.tracking.sync.testframework.rules;

import io.accelerate.tracking.sync.credentials.AWSSecretProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RemoteTestBucket extends TestBucket {

    public RemoteTestBucket() {
        Path privatePropertiesFile = Paths.get(".private", "aws-test-secrets");
        AWSSecretProperties secretsProvider = AWSSecretProperties.fromPlainTextFile(privatePropertiesFile);

        s3AsyncClient = secretsProvider.createClient();
        bucketName = secretsProvider.getS3Bucket();
        bucketPrefix = secretsProvider.getS3Prefix();
    }

}
