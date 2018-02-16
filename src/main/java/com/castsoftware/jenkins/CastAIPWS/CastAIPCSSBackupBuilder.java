package com.castsoftware.jenkins.CastAIPWS;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.tasks.Builder;

import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;

import javax.xml.rpc.ServiceException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.castsoftware.batch.CastWebService;
import com.castsoftware.batch.CastWebServiceServiceLocator;
import com.castsoftware.exception.HelperException;
import com.castsoftware.jenkins.CastAIPWS.CastAIPBuilder.DescriptorImpl;
import com.castsoftware.jenkins.CastAIPWS.util.Constants;
import com.castsoftware.jenkins.CastAIPWS.util.Utils;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link CastAIPCSSBackupBuilder} is created. The created instance is persisted to
 * the project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 *
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 *
 * @author Nevin Kaplan
 */
public class CastAIPCSSBackupBuilder extends Builder
{
	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public CastAIPCSSBackupBuilder()
	{
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException
	{
		long startTime = System.nanoTime();
		boolean retCode = true;
		int taskId;
		EnvVars envVars = build.getEnvironment(listener);

		int startAt;
		try {
			startAt = Integer.parseInt(envVars.get(Constants.START_AT));
		} catch (NumberFormatException e) {
			startAt=0;
		}
		if (startAt > Constants.RunBackup) {
			listener.getLogger().println(" ");
			listener.getLogger().println(String.format("${START_AT} = %d, skipping backup step.", startAt));
		} else {
			listener.getLogger().println(" ");
			listener.getLogger().println("Backup CAST Application database tripplet");

			String castDate = envVars.get(Constants.CAST_DATE);
			String webServiceAddress = envVars.get(Constants.CMS_WEB_SERVICE_ADDRESS);
			String castSchemaPrefix = envVars.get(Constants.SCHEMA_PREFIX);
			String appName = envVars.get(Constants.APPLICATION_NAME);
			String verName = envVars.get(Constants.VERSION_NAME);

			CastWebServiceServiceLocator cbwsl = new CastWebServiceServiceLocator();
			cbwsl.setCastWebServicePortEndpointAddress(webServiceAddress);
			try {
				CastWebService cbws = cbwsl.getCastWebServicePort();
				
				if (!Utils.validateWebServiceVersion(webServiceAddress, listener)) {
					return false;
				}
				
				Calendar cal = Utils.convertCastDate(castDate);
				
				taskId = cbws.runBackup(castSchemaPrefix, appName, verName, cal);
				if (taskId < 0) {
					listener.getLogger().println(String.format("Error: %s", cbws.getErrorMessage(-taskId)));
					return false;
				}

				if (!Utils.getLog(cbws, taskId, startTime, listener)) {
					return false;
				}

				
			} catch (ServiceException | HelperException | ParseException e) {
				listener.getLogger().println(
						String.format("%s error accured while backing up the tripplet", e.getMessage()));
			}

		}
		


		return retCode; 
	}		
	

	private void doCleanup(BuildListener listener)
	{
//		if (getDescriptor().getUseCleanup()) {
//			listener.getLogger().println("\n\n***** Running Cleanup Process *******");
//			File backupFolder = new File(getDescriptor().getBackupFolder());
//			File[] listOfFiles = backupFolder.listFiles();
//			int configDuration = getDescriptor().getDuration();
//
//			double convertNowToDays = new Date().getTime() / (1000 * 60 * 60 * 24);
//			for (int i = 0; i < listOfFiles.length; i++) {
//				File f = listOfFiles[i];
//				if (f.isFile()) // only delete files
//				{
//					double calcFileAgeInDays = f.lastModified() / (1000 * 60 * 60 * 24);
//					double fileAgeInDays = convertNowToDays - calcFileAgeInDays;
//					if (configDuration < fileAgeInDays) {
//						listener.getLogger().println("Deleting File " + f.getName());
//						f.delete();
//					}
//				}
//			}
//		}
//
	}

	public static boolean isWindows()
	{
		return (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0);
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
//	@Override
//	public DescriptorImpl getDescriptor()
//	{
//		return (DescriptorImpl) super.getDescriptor();
//	}

	/**
	 * Descriptor for {@link CastAIPCSSBackupBuilder}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See
	 * <tt>src/main/resources/hudson/plugins/hello_world/CastAIPCSSBackupBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
//	@Extension
//	// This indicates to Jenkins that this is an implementation of an extension
//	// point.
//	public static final class DescriptorImpl extends BuildStepDescriptor<Builder>
//	{
//		private boolean useCleanup;
//		private int duration;
//
//		// private boolean deleteLogFiles;
//		/**
//		 * In order to load the persisted global configuration, you have to call
//		 * load() in the constructor.
//		 */
//		public DescriptorImpl()
//		{
//			load();
//		}
//
//		/**
//		 * Performs on-the-fly validation of the form field 'name'.
//		 *
//		 * @param value
//		 *            This parameter receives the value that the user has typed.
//		 * @return Indicates the outcome of the validation. This is sent to the
//		 *         browser.
//		 *         <p>
//		 *         Note that returning {@link FormValidation#error(String)} does
//		 *         not prevent the form from being saved. It just means that a
//		 *         message will be displayed to the user.
//		 */
//		public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException
//		{
//			if (value.length() == 0) return FormValidation.error("Please set a name");
//			if (value.length() < 4) return FormValidation.warning("Isn't the name too short?");
//			return FormValidation.ok();
//		}
//
//		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> aClass)
//		{
//			// Indicates that this builder can be used with all kinds of project
//			// types
//			return true;
//		}
//
//		/**
//		 * This human readable name is used in the configuration screen.
//		 */
//		public String getDisplayName()
//		{
//			return "";  //don't want this module to show up on the Add Build Step dropdown
//			//return String.format("CAST AIP %d: CSS Backup", Constants.RunBackup)  ;
//		}
//
//		@Override
//		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException
//		{
//
//			final JSONObject cleanupJSON = formData.getJSONObject("useCleanup");
//			if ((cleanupJSON != null) && !(cleanupJSON.isNullObject())) {
//				this.useCleanup = true;
//				this.duration = cleanupJSON.getInt("duration");
//				// this.deleteLogFiles=cleanupJSON.getBoolean("deleteLogFiles");
//
//			} else {
//				this.useCleanup = false;
//			}
//
//			save();
//			return super.configure(req, formData);
//		}
//
//		public boolean getUseCleanup()
//		{
//			return useCleanup;
//		}
//
//		public int getDuration()
//		{
//			return duration;
//		}
//
//		/**
//		 * public boolean getDeleteLogFiles() { return deleteLogFiles; }
//		 **/
//		/****
//		 * public ListBoxModel doFillOppTypeItems() { return new ListBoxModel(
//		 * new Option("Backup","B", true), new Option("Restore","R", false));
//		 * 
//		 * }
//		 ****/
//
//	}
	/** end DescriptorImpl **/
}