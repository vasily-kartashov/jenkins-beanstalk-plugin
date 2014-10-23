package com.kartashov.jenkins.beanstalk;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationVersionDescription;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentHealth;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;

public class DirectDeploymentPluginRecorder extends Recorder {

    private String profileName;
    private String regionId;
    private String environmentCname;
    private String cacheClusterId;
    private String relativeArtifactPath;

    @DataBoundConstructor
    public DirectDeploymentPluginRecorder(String profileName, String regionId, String environmentCname,
                                          String cacheClusterId, String relativeArtifactPath)
            throws IOException {
        super();
        this.profileName = profileName;
        this.regionId = regionId;
        this.environmentCname = environmentCname;
        this.cacheClusterId = cacheClusterId;
        this.relativeArtifactPath = relativeArtifactPath;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        LoggerFacade logger = new LoggerFacade(listener.getLogger());

        logger.box(Messages.build_direct_deployment_top_banner());

        if (!build.getWorkspace().child(relativeArtifactPath).exists()) {
            logger.error(Messages.artifact_missing_in_path(build.getWorkspace().child(relativeArtifactPath)));
            return false;
        }

        ConfigurationService configurationService = new ConfigurationService();
        AWSCredentials credentials = null;
        Region region = null;
        try {
            credentials = configurationService.getCredentials(profileName);
            region = configurationService.getRegion(regionId);
        } catch (ConfigurationService.Exception e) {
            logger.error(e.getMessage());
            return false;
        }
        CloudService service = new CloudService(credentials, region, logger);

        try {

            EnvironmentDescription environmentDescription = service.getEnvironmentDescriptionByCname(environmentCname);
            if (environmentDescription == null) {
                logger.error(Messages.environment_cname_not_found(environmentCname));
                return false;
            }
            if (!environmentDescription.getHealth().equalsIgnoreCase(EnvironmentHealth.Green.toString())) {
                logger.error(Messages.environment_not_healthy(environmentCname));
                return false;
            }

            File artifact = new File(build.getWorkspace().child(relativeArtifactPath).absolutize().toURI());
            ApplicationVersionDescription applicationVersionDescription =
                    service.createNewApplicationVersion(environmentDescription, artifact);

            service.updateEnvironment(environmentDescription, applicationVersionDescription.getVersionLabel());

        } catch (CloudService.Exception e) {
            logger.error(e.getMessage());
            return false;
        }

        logger.box(Messages.build_direct_deployment_bottom_banner());
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private ConfigurationService configurationService;

        public DescriptorImpl() {
            super(DirectDeploymentPluginRecorder.class);
            configurationService = new ConfigurationService();
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.build_direct_deployment_step_name();
        }

        @Override
        public DirectDeploymentPluginRecorder newInstance(StaplerRequest request, JSONObject formData) throws FormException {
            return request.bindJSON(DirectDeploymentPluginRecorder.class, formData);
        }

        public FormValidation doCheckProfileName(@QueryParameter String profileName) {
            try {
                configurationService.getCredentials(profileName);
            } catch (ConfigurationService.Exception e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckRegionId(@QueryParameter String regionId) {
            try {
                configurationService.getRegion(regionId);
            } catch (ConfigurationService.Exception e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckRelativeArtifactPath(@QueryParameter String relativeArtifactPath) {
            if (relativeArtifactPath == null || relativeArtifactPath.isEmpty()) {
                return FormValidation.error(Messages.artifact_empty_path());
            }
            if (!relativeArtifactPath.endsWith(".war")) {
                return FormValidation.error(Messages.artifact_wrong_extension(relativeArtifactPath));
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckEnvironmentCname(@QueryParameter String profileName,
                                                      @QueryParameter String regionId,
                                                      @QueryParameter String environmentCname) {
            if (environmentCname == null || environmentCname.isEmpty()) {
                return FormValidation.error(Messages.environment_empty_cname());
            }
            AWSCredentials credentials;
            Region region;
            try {
                credentials = configurationService.getCredentials(profileName);
                region = configurationService.getRegion(regionId);
            } catch (ConfigurationService.Exception e) {
                return FormValidation.ok(); // oddly enough this should be handled by other fields
            }
            CloudService service = new CloudService(credentials, region, new LoggerFacade(System.out));
            EnvironmentDescription environmentDescription = null;
            try {
                environmentDescription = service.getEnvironmentDescriptionByCname(environmentCname);
            } catch (CloudService.Exception e) {
                return FormValidation.error(e.getMessage());
            }
            if (environmentDescription == null) {
                return FormValidation.error(Messages.environment_cname_not_found(environmentCname));
            }
            if (environmentDescription.getHealth().equalsIgnoreCase(EnvironmentHealth.Green.toString())) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.environment_not_healthy(environmentCname));
            }
        }

        public ListBoxModel doFillRegionIdItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("", "");
            for (Regions r : Regions.values()) {
                items.add(r.getName(), r.getName());
            }
            return items;
        }
    }

    public String getProfileName() {
        return profileName;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getEnvironmentCname() {
        return environmentCname;
    }

    public String getCacheClusterId() {
        return cacheClusterId;
    }

    public String getRelativeArtifactPath() {
        return relativeArtifactPath;
    }
}
