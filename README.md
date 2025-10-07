# trkl-sync-to-s3

This library that continuously syncs the contents of a folder to an S3 bucket. Optimised for streaming file formats (video, logs).

The library will aggresively upload content as it is being generated:
* Each chunk will be uploaded as a part in a multipart upload
* The multipart upload in kept open while the file is being generated 
* It is the responsibility of the generator to create a `.lock` with the same name.
* Once the file generation is completed and the `.lock` file removed, the multipart upload will be finalised.


## To use as a library

### Add as Maven dependency

Add a dependency to `tdl:sync-to-s3` in `compile` scope. See `bintray` shield for latest release number.
```xml
<dependency>
  <groupId>io.accelerate</groupId>
  <artifactId>sync-to-s3</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

### Configure AWS user with minimal permissions

**WIP** - TODO Add detailed IAM instructions

### Define sync source and destination

Configure the local folder as a `source` and define AWS S3 as the `destination`
```java
Source source = Source.getBuilder(/* Path */ pathToFolder)
  .traverseDirectories(true)
  .include(endsWith(".mp4")
  .exclude(startsWith(".")
  .exclude(matches("tmp.log"))
  .create();

Destination destination = Destination.getBuilder()
  .loadFromPath(/* Path */ pathToFile)
  .create();
```

Construct the `RemoteSync` and run. The `run` method can be invoked multiple times.
```java
remoteSync = new RemoteSync(source, destination);
remoteSync.run();
```

### Example source definitions

The source will be a set of filters that can be applied to a folder to obtain a list of files to be synced

**Default values** will not include .lock files and hidden files (. files)
```java
Source source = Source.getBuilder(/* Path */ pathToFolder)
  .includeAll()
  .create();
```

**Single file** can be selected using a matcher
```java
Filter filter = Filter.getBuilder().matches("file.txt");

Source source = Source.getBuilder(/* Path */ pathToFolder)
  .include(filter)
  .create();
```

**Multiple files** can be included if they match one of the matchers.
The list of included files can be further filtered via exclude matchers
```java
Filter includeFilter = Filter.getBuilder()
                        .endsWith(".mp4")
                        .endsWith(".log")
                        .create();

Filter excludeFilter = Filter.getBuilder()
                        .matches("tmp.log")
                        .create();

Source source = Source.getBuilder(/* Path */ pathToFolder)
  .include(includeFilter)
  .exclude(excludeFilter)
  .create();
```

By default the library will not **traverse directories**, if you need this behaviour than you can set the `traverseDirectories` flag to true
```java
Source source = Source.getBuilder(/* Path */ pathToFolder)
  .traverseDirectories(true)
  .includeAll()
  .create();
```

If no include matcher is specified then an **IllegalArgumentException** will be raised upon creation:
```java
Source source = Source.getBuilder(/* Path */ pathToFolder)
  .create();
```

## Development

### Prepare environment

Configuration for running this service should be placed in file `.private/aws-test-secrets` in Java Properties file format. The supported credential modes are shown below.

#### Static / STS credentials

```properties
trk_aws_access_key_id=ABCDEFGHIJKLM
trk_aws_secret_access_key=ABCDEFGHIJKLM
trk_aws_session_token=OPTIONAL_SESSION_TOKEN
trk_s3_region=ap-southeast-1
trk_s3_bucket=bucketname
trk_s3_prefix=prefix/
```

#### Web identity credentials

```properties
trk_oidc_jwt_token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
trk_oidc_role_arn=arn:aws:iam::123456789012:role/ExampleRole
trk_oidc_role_session_name=optional-session-name
trk_oidc_sts_region=optional-sts-region
trk_s3_region=ap-southeast-1
trk_s3_bucket=bucketname
trk_s3_prefix=prefix/
```

The values are:
* `trk_aws_access_key_id` - access key to the AWS account (static credentials only).
* `trk_aws_secret_access_key` - secret key to the AWS account (static credentials only).
* `trk_aws_session_token` - optional session token if temporary credentials are in use.
* `trk_oidc_jwt_token` - OIDC JWT used to exchange for AWS credentials (web identity only).
* `trk_oidc_role_arn` - IAM role to assume with the supplied OIDC token (web identity only).
* `trk_oidc_role_session_name` - optional session name for the AssumeRoleWithWebIdentity request.
* `trk_oidc_sts_region` - optional region to call STS in (defaults to AWS global endpoint).
* `trk_s3_region` - this contains the region that holds the S3 bucket.
* `trk_s3_bucket` or `trk_upload_bucket` - the bucket that will store the uploaded files.
* `trk_s3_prefix` - S3 prefix that will be added before all files

### Run tests

Start Minio as a container
```
docker run -d --rm \
  --name minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e "MINIO_ROOT_USER=minio_access_key" \
  -e "MINIO_ROOT_PASSWORD=minio_secret_key" \
  minio/minio:RELEASE.2025-09-07T16-13-09Z server /data --console-address ":9001"
```

Minio can be accessed via the normal AWS client
```
export AWS_ACCESS_KEY_ID=minio_access_key
export AWS_SECRET_ACCESS_KEY=minio_secret_key
export AWS_DEFAULT_REGION=us-east-1

aws --endpoint-url http://127.0.0.1:9000 s3api list-multipart-uploads --bucket localbucket
aws --endpoint-url http://127.0.0.1:9000 s3api list-parts  --bucket localbucket --key prefix/sample_small_file_to_upload.txt --upload-id <FILL>
aws --endpoint-url http://127.0.0.1:9000 s3api abort-multipart-upload  --bucket localbucket --key prefix/sample_small_file_to_upload.txt --upload-id <FILL>

aws --endpoint-url http://127.0.0.1:9000 s3api list-objects --bucket localbucket
```

Run the local tests
```
./gradlew test -i
```

### Build and run as command-line app
```bash
./gradlew clean shadowJar -i
java -Dlogback.configurationFile=`pwd`/logback.xml \
    -jar ./sync-to-s3-cli/build/libs/sync-to-s3-cli-0.0.17-all.jar \
    -c ./.private/aws-test-secrets \
    -d ./src/test/resources/test_a_1 \
    --filter "^[0-9a-zA-Z\\_]+\\.txt$"
```

### Install to mavenLocal

If you want to build the SNAPSHOT version locally you can install to the local Maven cache
```
./gradlew -x test clean install
```

### Inspect traffic with Charles Proxy

- Install Charles Proxy: https://www.charlesproxy.com/
- Enable SSL Proxying: https://www.charlesproxy.com/documentation/proxying/ssl-proxying/
- Add SSL host: `*.amazonaws.com`
- Export Charles certificate, `SSL Proxying > Save Charles Root Certificate`
- Import into Java Keystore
```
sudo keytool -import -alias charles \
  -file "${CERT_SAVE_LOCATION}/charles-ssl-proxying-certificate.pem" \
  -keystore "${JAVA_HOME}/jre/lib/security/cacerts" \
  -storepass changeit
```
- Traffic should appear in Charles

### Useful AWS commands

List multipart uploads
```
aws s3api list-multipart-uploads --bucket tdl-official-videos \
    > /tmp/uploads.aws
cat /tmp/uploads.aws | jq '.Uploads[] | {init:.Initiated, key: .Key, id:.UploadId}'
```

Abort multipart upload
```
 aws s3api abort-multipart-upload --bucket tdl-official-videos \
  --key CHK/frhh01/record-and-upload-20180609T172016.log \
  --upload-id qro14BxOJj1MfcCWd5U67BWgQwsCrRsKn5UqtN7PKAN753HShMSZR9KN11ySkm_ftLJMQoVO._KGb1Irrl3NjnLDerlsrtPt.iYR2YWynhXb1tnPRX5CkVOPNvoyq6A7tO8cyCcHiON8W3WArgGuMQ--
```

List parts
```
aws s3api list-parts \
  --bucket tdl-official-videos \
  --key "CHK/eijf01/sourcecode_20180611T071715.srcs" \
  --upload-id pDeAaCMyM9veZeS4t1sc7dZG9K58d3zVLPhoGFE_xc8I6jHatZ4EdLzpNZg2L6mHAe6s26AUiBFlqI0CDgwNCOG5b7am_iQjThOSgcoTu7fdGUQQa895yyPjxMxpu6wbADnf1JAEKVe6KQYSk.oC4Q-- \
  > /tmp/part.tags.aws
cat /tmp/part.tags.aws | jq '.Parts[] | {etag:.ETag, num:.PartNumber}'
```

Complete multipart
```
aws s3api complete-multipart-upload \
  --bucket tdl-official-videos \
  --key "CHK/sagp01/screencast_20180607T212124.mp4" \
  --multipart-upload '{"Parts":[{"ETag":"bee8593bf7085ce82a12708ade4b70b5","PartNumber":1},{"ETag":"d58e97a1c8aa3ed54ed1274e6972b428","PartNumber":2},{"ETag":"7ca7bf9efdd01ab39664711a574f0b48","PartNumber":3}]}' \
  --upload-id "jdB1Q.SRfhk0wdRalRHJNLvE8xEoiH5TiQPBrnG2_hkU1oc9wcQSQgM4FcEUmDxNuA2FGHUigd_0LwkovflgXupcQMXCuJ_xYML9ZtKlX4LS8PaXXxaNcA4WOexreZoZ.fZ_NxDHxqCbg15H6enZdg--"
```

### Local CLI

Build the local CLI
```shell
./gradlew clean shadowJar -i -x test
```

Retrieve a pair of creds and store in file, say
```shell
cat <CONFIG_FILE>
 ./use_temp_creds.sh <CONFIG_FILE> sts get-caller-identity
```

Try using the creds via the CLI
```shell
 ./use_temp_creds.sh <CONFIG_FILE> s3api 
```

Run against a dir
```shell
java -jar sync-to-s3-cli/build/libs/sync-to-s3-cli-*-all.jar -d <TARGET_DIR> -c <CONFIG_FILE>
```

### Release

Configure the version inside the "gradle.properties" file

Create publishing bundle into Maven Local
```bash
./gradlew publishToMavenLocal
```

Check Maven Local contains release version:
```
CURRENT_VERSION=$(cat gradle.properties | grep version | cut -d "=" -f2)

ls -l $HOME/.m2/repository/io/accelerate/sync-to-s3/${CURRENT_VERSION}
```

Publish to Maven Central Staging repo

### Publish to Maven Central - the manual way

At this point publishing to Maven Central from Gradle is only possible manually.
Things might have changed, check this page:
https://central.sonatype.org/publish/publish-portal-gradle/

Generate the Maven Central bundle:
```
./generateMavenCentralBundle.sh
```

Upload the bundle to Maven Central by clicking the "Publish Component" button.
https://central.sonatype.com/publishing

### To build artifacts in Github

Commit all changes then:
```bash
export RELEASE_TAG="v$(cat gradle.properties | cut -d= -f2)"
git tag -a "${RELEASE_TAG}" -m "${RELEASE_TAG}"
git push --tags
git push
```
