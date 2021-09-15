import java.io.IOException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
//import software.amazon.awssdk.services.ssm.model.ListComplianceSummariesRequest;
//import software.amazon.awssdk.services.ssm.model.ListComplianceSummariesResponse;
//import software.amazon.awssdk.services.ssm.model.ListResourceComplianceSummariesRequest;
//import software.amazon.awssdk.services.ssm.model.ListResourceComplianceSummariesResponse;
//import software.amazon.awssdk.services.ssm.model.SsmException;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3Client;

public class ListComplianceSummaries {


    public static void main(String[] args) throws IOException {

        Region region = Region.AP_SOUTHEAST_2;
        S3Client s3 = S3Client.builder().region(region).build();
        SsmClient ssmClient = SsmClient.builder()
                .region(region)
                .build();

        String bucket = "bucket" + System.currentTimeMillis();
        String key = "Compliance Summary";
        String key2 = "Resource Compliance Summary";
        
//        ObjectMapper mapper = new ObjectMapper();
//        String compSum = ssmClient.listComplianceSummaries().toString();
//
//        String jsonString = mapper.writerWithDefaultPrettyPrinter().
//                writeValueAsString(compSum);
//        System.out.println(jsonString);

        // Creation of S3 Bucket
        s3Setup(s3, bucket, region);

        System.out.println("Uploading object...");

        // Quick way of checking format of compliance summaries
        System.out.println(ssmClient.listComplianceSummaries());

        // Place listComplianceSummaries string into S3 Bucket
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                        .build(),
                RequestBody.fromString(ssmClient.listComplianceSummaries().toString()));

        // Place listResourceComplianceSummaries into seperate bucket
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key2)
                        .build(),
                RequestBody.fromString(ssmClient.listResourceComplianceSummaries().toString()));

        System.out.println("Upload complete");
        System.out.printf("%n");
        
        // Deletion of S3 bucket to minimise waste
        cleanUp(s3, bucket, key);

        System.out.println("Closing the connection to {S3}");
        // Close stream to ssmClient
        ssmClient.close();
        // Close stream to S3
        s3.close();
        System.out.println("Connection closed");
        System.out.println("Exiting...");
    }

    public static void s3Setup(S3Client s3Client, String bucketName, Region region) {
        try {
            s3Client.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(
                            CreateBucketConfiguration.builder()
                                    .locationConstraint(region.id())
                                    .build())
                    .build());

            System.out.println("Creating bucket: " + bucketName);
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            System.out.println(bucketName + " is ready.");
            System.out.printf("%n");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void cleanUp(S3Client s3Client, String bucketName, String keyName) {
        System.out.println("Cleaning up...");
        try {
            System.out.println("Deleting object: " + keyName);
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(keyName).build();
            s3Client.deleteObject(deleteObjectRequest);
            System.out.println(keyName +" has been deleted.");
            System.out.println("Deleting bucket: " + bucketName);
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
            s3Client.deleteBucket(deleteBucketRequest);
            System.out.println(bucketName +" has been deleted.");
            System.out.printf("%n");
        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        System.out.println("Cleanup complete");
        System.out.printf("%n");
    }
}
