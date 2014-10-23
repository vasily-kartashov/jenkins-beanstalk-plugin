package com.kartashov.jenkins.beanstalk;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.kartashov.jenkins.beanstalk.Messages;

public class ConfigurationService {

    public AWSCredentials getCredentials(String profileName) throws Exception {
        if (profileName == null || profileName.isEmpty()) {
            throw new Exception(Messages.configuration_empty_profile_name());
        }
        ProfileCredentialsProvider profileCredentialsProvider = new ProfileCredentialsProvider(profileName);
        AWSCredentials credentials = null;
        try {
            credentials = profileCredentialsProvider.getCredentials();
        } catch (IllegalArgumentException e) {
            throw new Exception(Messages.configuration_profile_not_found(profileName));
        } catch (AmazonClientException e) {
            throw new Exception(e.getMessage());
        }
        if (credentials.getAWSAccessKeyId() == null || credentials.getAWSSecretKey() == null ||
                credentials.getAWSAccessKeyId().isEmpty() || credentials.getAWSSecretKey().isEmpty()) {
            throw new Exception(Messages.configuration_profile_not_found(profileName));
        }
        return credentials;
    }

    public Region getRegion(String regionId) throws Exception {
        if (regionId == null || regionId.isEmpty()) {
            throw new Exception(Messages.configuration_empty_region_id());
        }
        try {
            return Region.getRegion(Regions.fromName(regionId));
        } catch (IllegalArgumentException e) {
            throw new Exception(e.getMessage());
        } catch (AmazonClientException e) {
            throw new Exception(e.getMessage());
        }
    }

    public static class Exception extends java.lang.Exception {
        public Exception(String message) {
            super(message);
        }
    }
}
