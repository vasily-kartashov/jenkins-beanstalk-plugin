package com.kartashov.jenkins.beanstalk;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.elasticbeanstalk.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import javax.annotation.Nullable;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CloudService {

    private LoggerFacade logger;
    private AWSElasticBeanstalk elasticBeanstalk;
    private AmazonS3 s3;

    public CloudService(AWSCredentials credentials, Region region, LoggerFacade logger) {
        this.logger = logger;

        this.elasticBeanstalk = new AWSElasticBeanstalkClient(credentials);
        this.elasticBeanstalk.setRegion(region);

        this.s3 = new AmazonS3Client(credentials);
        this.s3.setRegion(region);
    }

    public List<EnvironmentDescription> getEnvironmentDescriptions() throws Exception {
        try {
            return elasticBeanstalk.describeEnvironments().getEnvironments();
        } catch (AmazonClientException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Nullable
    public EnvironmentDescription getEnvironmentDescriptionByCname(String cname) throws Exception {
        for (EnvironmentDescription environmentDescription : getEnvironmentDescriptions()) {
            if (environmentDescription.getCNAME().equalsIgnoreCase(cname)) {
                return environmentDescription;
            }
        }
        return null;
    }

    @Nullable
    public EnvironmentDescription getEnvironmentDescriptionByName(String name) throws Exception {
        for (EnvironmentDescription environmentDescription : getEnvironmentDescriptions()) {
            if (environmentDescription.getEnvironmentName().equalsIgnoreCase(name)) {
                return environmentDescription;
            }
        }
        return null;
    }

    private S3Location uploadArtifact(String bucketName, File artifact, String versionLabel) {
        final String name = artifact.getName();
        final int separator = name.lastIndexOf(".");
        final String key = name.substring(0, separator) + "-" + versionLabel + name.substring(separator);

        logger.bar();
        logger.info(Messages.artifact_start_uploading(artifact.getAbsolutePath()));
        logger.info(Messages.artifact_bucket_name(bucketName));
        logger.info(Messages.artifact_key(key));

        s3.putObject(bucketName, key, artifact);

        logger.info(Messages.artifact_uploaded());

        return new S3Location(bucketName, key);
    }

    public ApplicationVersionDescription createNewApplicationVersion(EnvironmentDescription environmentDescription,
                                                                     File artifact) throws Exception {

        final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmm");
        final String versionLabel = format.format(new Date());
        S3Location location = uploadArtifact(elasticBeanstalk.createStorageLocation().getS3Bucket(), artifact,
                versionLabel);

        CreateApplicationVersionRequest request = new CreateApplicationVersionRequest();
        request.setApplicationName(environmentDescription.getApplicationName());
        request.setSourceBundle(location);
        request.setVersionLabel(versionLabel);

        CreateApplicationVersionResult result = elasticBeanstalk.createApplicationVersion(request);
        return result.getApplicationVersion();
    }

    public void updateEnvironment(EnvironmentDescription environmentDescription, String versionLabel) throws Exception {

        logger.bar();
        logger.info(Messages.environment_start_updating(environmentDescription.getEnvironmentName()));

        UpdateEnvironmentRequest updateEnvironmentRequest = new UpdateEnvironmentRequest();
        updateEnvironmentRequest.setEnvironmentName(environmentDescription.getEnvironmentName());
        updateEnvironmentRequest.setVersionLabel(versionLabel);
        elasticBeanstalk.updateEnvironment(updateEnvironmentRequest);

        logger.info(Messages.environment_update_request_sent(environmentDescription.getEnvironmentName()));

        ensureEnvironmentHealth(environmentDescription.getEnvironmentName());
    }

    private EnvironmentDescription ensureEnvironmentHealth(String environmentName) throws Exception {
        boolean isReady = false;
        EnvironmentDescription environmentDescription = null;
        for (int attempt = 0; attempt < 120; attempt++) {
            logger.info(Messages.environment_waiting_for_green(environmentName));
            try {
                Thread.sleep(30 * 1000);
            } catch (InterruptedException e) {
                throw new Exception(e.getMessage());
            }
            environmentDescription = getEnvironmentDescriptionByName(environmentName);

            if (environmentDescription != null &&
                    environmentDescription.getHealth().equalsIgnoreCase(EnvironmentHealth.Green.toString())) {
                isReady = true;
                break;
            }
        }
        if (!isReady) {
            throw new Exception(Messages.environment_not_functional(environmentName));
        }
        logger.info(Messages.environment_healthy(environmentName));
        return environmentDescription;
    }

    public static class Exception extends java.lang.Exception {
        public Exception(String message) {
            super(message);
        }
    }
}
