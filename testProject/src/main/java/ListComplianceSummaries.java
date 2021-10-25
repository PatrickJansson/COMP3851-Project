import org.json.simple.JSONObject;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;
import software.amazon.awssdk.services.eventbridge.model.Target;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

//import software.amazon.awssdk.services.ssm.model.ListComplianceSummariesRequest;
//import software.amazon.awssdk.services.ssm.model.ListResourceComplianceSummariesRequest;
//import software.amazon.awssdk.services.ssm.model.ListResourceComplianceSummariesResponse;
//import software.amazon.awssdk.services.ssm.model.SsmException;

public class ListComplianceSummaries {


    public static void main(String[] args) throws IOException, InterruptedException {

        Region region = Region.AP_SOUTHEAST_2;
        S3Client s3 = S3Client.builder().region(region).build();
        SsmClient ssmClient = SsmClient.builder()
                .region(region)
                .build();

        String bucket = "bucket" + System.currentTimeMillis();
        String key = "ComplianceSummary.json";

        // Generate JSON
        String str = "";
        //ListComplianceSummariesResponse resp = ssmClient.listComplianceSummaries();
        ListResourceComplianceSummariesResponse resp2 = ssmClient.listResourceComplianceSummaries();
        for (ResourceComplianceSummaryItem summaryItem : resp2.resourceComplianceSummaryItems())
        {
            JSONObject summary = new JSONObject();
//            summary.put("Type", summaryItem.complianceType());
//            summary.put("CompliantCount", summaryItem.compliantSummary().compliantCount());
//            summary.put("NonCompliantCount", summaryItem.nonCompliantSummary().nonCompliantCount());
            summary.put("Type", summaryItem.complianceType());
            summary.put("CompliantCount", summaryItem.compliantSummary().compliantCount());
            summary.put("NonCompliantCount", summaryItem.nonCompliantSummary().nonCompliantCount());
            summary.put("OverallSeverity", summaryItem.overallSeverity());
            summary.put("Status", summaryItem.statusAsString());
            str += summary + "\n";
        }

        System.out.println("\nJSON\n" + str);

        s3Setup(s3, bucket, region);

        System.out.println("Uploading object...");

        // Place listComplianceSummaries string into S3 Bucket
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                        .build(),
                RequestBody.fromString(str));


        System.out.println("Upload complete");
        System.out.printf("%n");

        cleanUp(s3, bucket, key);

        System.out.println("Closing the connection to {S3}");

        s3.close();
        System.out.println("Connection closed");

        //-------------------------------------------------------------------------------------//

//        final String USAGE = "\n" +
//                "Usage:\n" +
//                "    DescribeRule <ruleName> \n\n" +
//                "Where:\n" +
//                "    ruleName - the name of the rule to create. \n";
//
//        if (args.length != 1) {
//            System.out.println(USAGE);
//            System.exit(1);
//        }

        String ruleName = "Test";
        EventBridgeClient eventBrClient = EventBridgeClient.builder()
                .region(region)
                .build();

        createEBRule(eventBrClient, ruleName);
        createTarget(eventBrClient, ruleName);
        eventBrClient.close();

        String title = "ComplianceSummaryOps";
        String source = "Systems Manager";
        String category = "Performance";
        String severity = "3";

        //System.out.println("The Id of the OpsItem is " +createNewOpsItem(ssmClient, title, source, category, severity));
        ssmClient.close();

        System.out.println("Exiting...");
    }

    public static void createEBRule(EventBridgeClient eventBrClient, String ruleName) {



        try {

            PutRuleRequest ruleRequest = PutRuleRequest.builder()
                    .name(ruleName)
                    .eventBusName("default")
                    .eventPattern("{\"source\":[\"aws.config\"],\"detail-type\":[\"Config Rules Compliance Change\"],\"detail\":{\"configRuleName\":[\"ec2-managedinstance-patch-compliance-status-check\", \"\"]}}")
                    .description("Create an Ops Item whenever there is a change in the compliance")
                    .build();

            PutRuleResponse ruleResponse = eventBrClient.putRule(ruleRequest);
            System.out.println("The ARN of the new rule is "+ ruleResponse.ruleArn());

        } catch (EventBridgeException e) {

            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static void createTarget(EventBridgeClient eventBrClient, String ruleName) {
        Map<String, String> map = new HashMap<>();
        map.put("instance", "$.detail.instance");
        map.put( "status", "$.detail.status");

        try {

            PutTargetsRequest targetRequest = PutTargetsRequest.builder()
                    .rule(ruleName)
                    .targets(Target.builder()
                            .id("ComplianceAlert")
                            .roleArn("arn:aws:iam::750512766618:role/service-role/Amazon_EventBridge_Invoke_Create_Ops_Item_626167026")
                            .arn("arn:aws:ssm:ap-southeast-2:750512766618:opsitem")
                            .inputTransformer(InputTransformer.builder()
                                    .inputPathsMap(map)
                                    //.inputTemplate("{<instance> is in state <status>}")
                                    .inputTemplate("{\"message\" : \"instance is <instance>\"}")
                                    .build())
                            .build())
                    .build();

            PutTargetsResponse targetsResponse = eventBrClient.putTargets(targetRequest);
            System.out.println("The target string is: " + targetsResponse.toString());

        } catch (EventBridgeException e) {

            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static String createNewOpsItem( SsmClient ssmClient,
                                           String title,
                                           String source,
                                           String category,
                                           String severity) {

        try {
            CreateOpsItemRequest opsItemRequest = CreateOpsItemRequest.builder()
                    .description("Created by the SSM Java API")
                    .title(title)
                    .source(source)
                    .category(category)
                    .severity(severity)
                    .build();

            CreateOpsItemResponse itemResponse = ssmClient.createOpsItem(opsItemRequest);
            return itemResponse.opsItemId();

        } catch (SsmException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return "";
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