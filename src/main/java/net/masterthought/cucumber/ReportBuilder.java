package net.masterthought.cucumber;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.exception.VelocityException;

import net.masterthought.cucumber.generators.ErrorPage;
import net.masterthought.cucumber.generators.FeatureOverviewPage;
import net.masterthought.cucumber.generators.FeatureReportPage;
import net.masterthought.cucumber.generators.StepOverviewPage;
import net.masterthought.cucumber.generators.TagOverviewPage;
import net.masterthought.cucumber.generators.TagReportPage;
import net.masterthought.cucumber.json.Feature;
import net.masterthought.cucumber.util.UnzipUtils;

public class ReportBuilder {

    private ReportInformation reportInformation;
    private List<String> jsonFiles;
    private File reportDirectory;
    private String buildNumber;
    private String buildProject;
    private String pluginUrlPath;
    private boolean flashCharts;
    private boolean runWithJenkins;
    private boolean highCharts;

    private Map<String, String> customHeader;

    public static final String VERSION = "cucumber-reporting-0.4.0";

    //Added to control parallel reports
    private static boolean parallel = false;

    public ReportInformation getReportInformation() {
        return reportInformation;
    }

    public void setRi(ReportInformation ri) {
        this.reportInformation = ri;
    }

    public static boolean isParallel(){
        return parallel;
    }

    public static void setParallel(boolean p){
       parallel = p;
    }

    public File getReportDirectory() {
        return reportDirectory;
    }

    public void setReportDirectory(File reportDirectory) {
        this.reportDirectory = reportDirectory;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getBuildProject() {
        return buildProject;
    }

    public void setBuildProject(String buildProject) {
        this.buildProject = buildProject;
    }

    public String getPluginUrlPath() {
        return pluginUrlPath;
    }

    public void setPluginUrlPath(String pluginUrlPath) {
        this.pluginUrlPath = pluginUrlPath;
    }

    public boolean isRunWithJenkins() {
        return runWithJenkins;
    }

    public void setRunWithJenkins(boolean runWithJenkins) {
        this.runWithJenkins = runWithJenkins;
    }

    public Map<String, String> getCustomHeader() {
        return customHeader;
    }

    public void setCustomHeader(Map<String, String> customHeader) {
        this.customHeader = customHeader;
    }

    public boolean isFlashCharts() {
        return flashCharts;
    }

    public void setFlashCharts(boolean flashCharts) {
        this.flashCharts = flashCharts;
    }

    public boolean isHighCharts() {
        return highCharts;
    }

    public void setHighCharts(boolean highCharts) {
        this.highCharts = highCharts;
    }

    public List<String> getJsonFiles() {
        return this.jsonFiles;
    }

    public ReportBuilder(List<String> jsonReports, File reportDirectory, String pluginUrlPath, String buildNumber,
            String buildProject, boolean skippedFails, boolean pendingFails, boolean undefinedFails,
            boolean missingFails, boolean flashCharts, boolean runWithJenkins, boolean highCharts,
            boolean parallelTesting) throws IOException, VelocityException {

        try {
            this.reportDirectory = reportDirectory;
            this.buildNumber = buildNumber;
            this.buildProject = buildProject;
            this.pluginUrlPath = getPluginUrlPath(pluginUrlPath);
            this.flashCharts = flashCharts;
            this.runWithJenkins = runWithJenkins;
            this.highCharts = highCharts;
            ReportBuilder.parallel = parallelTesting;
            this.jsonFiles = jsonReports;

            ConfigurationOptions configuration = ConfigurationOptions.instance();
            configuration.setSkippedFailsBuild(skippedFails);
            configuration.setPendingFailsBuild(pendingFails);
            configuration.setUndefinedFailsBuild(undefinedFails);
            configuration.setMissingFailsBuild(missingFails);

            // whatever happens we want to provide at least error page instead of empty report
        } catch (Exception exception) {
            generateErrorPage(exception);
            exception.printStackTrace();
        }
    }

    public boolean getBuildStatus() {
        return reportInformation.getTotalStepsFailed() == 0;
    }

    public void generateReports() throws IOException, VelocityException {
        try {
            ReportParser reportParser = new ReportParser(jsonFiles);
            this.reportInformation = new ReportInformation(reportParser.getFeatures());

            copyResource("themes", "blue.zip");
            copyResource("charts", "js.zip");
            if (flashCharts) {
                copyResource("charts", "flash_charts.zip");
            }

            //Added to correlate feature with each report
            setJsonFilesInFeatures();

            new FeatureOverviewPage(this).generatePage();
            new FeatureReportPage(this).generatePage();
            new TagReportPage(this).generatePage();
            new TagOverviewPage(this).generatePage();
            new StepOverviewPage(this).generatePage();
            // whatever happens we want to provide at least error page instead of empty report
        } catch (Exception exception) {
            generateErrorPage(exception);
        }
    }

    private void setJsonFilesInFeatures() {
        for (Map.Entry<String, List<Feature>> pairs : reportInformation.getFeatureMap().entrySet()) {
            List<Feature> featureList = pairs.getValue();

            for (Feature feature : featureList) {
                String jsonFile = pairs.getKey().split("/")[pairs.getKey().split("/").length - 1];
                feature.setJsonFile(jsonFile);               
            }
        }
    }

    private void copyResource(String resourceLocation, String resourceName) throws IOException, URISyntaxException {
        final File tmpResourcesArchive = File.createTempFile("temp", resourceName + ".zip");

        InputStream resourceArchiveInputStream = ReportBuilder.class.getResourceAsStream(resourceLocation + "/" + resourceName);
        if (resourceArchiveInputStream == null) {
            resourceArchiveInputStream = ReportBuilder.class.getResourceAsStream("/" + resourceLocation + "/" + resourceName);
        }
        OutputStream resourceArchiveOutputStream = new FileOutputStream(tmpResourcesArchive);
        try {
            IOUtils.copy(resourceArchiveInputStream, resourceArchiveOutputStream);
        } finally {
            IOUtils.closeQuietly(resourceArchiveInputStream);
            IOUtils.closeQuietly(resourceArchiveOutputStream);
        }
        UnzipUtils.unzipToFile(tmpResourcesArchive, reportDirectory);
        FileUtils.deleteQuietly(tmpResourcesArchive);
    }

    private String getPluginUrlPath(String path) {
        return path.isEmpty() ? "/" : path;
    }

    private void generateErrorPage(Exception exception) throws IOException {
        ErrorPage errorPage = new ErrorPage(this, exception);
        errorPage.generatePage();
        System.err.println(exception);
    }
}
