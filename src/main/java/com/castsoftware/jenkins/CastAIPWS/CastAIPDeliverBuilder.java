package com.castsoftware.jenkins.CastAIPWS;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.tasks.Builder;

import java.io.IOException;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import javax.xml.rpc.ServiceException;
import javax.xml.soap.SOAPException;

import org.kohsuke.stapler.DataBoundConstructor;

import com.castsoftware.batch.CastWebService;
import com.castsoftware.batch.CastWebServiceServiceLocator; 
import com.castsoftware.dmtexplore.DmtExplore;
import com.castsoftware.dmtexplore.XmlData;
import com.castsoftware.dmtexplore.data.DeliveryData;
import com.castsoftware.exception.HelperException;
import com.castsoftware.jenkins.CastAIPWS.util.Constants;
import com.castsoftware.jenkins.CastAIPWS.util.Utils;
import com.castsoftware.jenkins.util.PublishEnvVarAction;
import com.castsoftware.util.GlobalProperties;
import com.castsoftware.util.GlobalPropertiesManager;
import com.castsoftware.vps.ValidationProbesService;

public class CastAIPDeliverBuilder extends Builder
{   
	private GlobalProperties globalProperties = GlobalPropertiesManager.getGlobalProperties();
	 
	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public CastAIPDeliverBuilder()
	{ 
	}
	 
	
	private String getPrevDmtVersion(String dmtFolder, String findApp, String rescanType)
	{  
		DmtExplore dmtExplore = new DmtExplore(dmtFolder, findApp);
		List<XmlData> dmtData = dmtExplore.getDmtData();
		String prevVersion = "";
		for (XmlData appData : dmtData) { // get the applications
			List<DeliveryData> deliveryData = appData.getDeliveries();
			Collections.sort(deliveryData);

			for (int idx = deliveryData.size() - 1; idx >= 0; idx--) 
			{
				DeliveryData delivery = deliveryData.get(idx);
				String deliveryStatus = delivery.getDeliveryStatus();
				if ("delivery.StatusReadyForAnalysis".equals(deliveryStatus) || "delivery.StatusReadyForAnalysisAndDeployed".equals(deliveryStatus)) 
				{
					String DelvName = delivery.getDeliveryName();
					if(rescanType.equals("QA"))
					{
						if(DelvName.startsWith("QA"))
						{
							prevVersion = delivery.getDeliveryName();
							break;
						}
					} 
					else
					{
						if(!DelvName.startsWith("QA"))
						{
							prevVersion = delivery.getDeliveryName();
							break;
						}
					}
				}
			}
		}
		
		if(prevVersion.equals(""))
		{
			for (XmlData appData : dmtData) { // get the applications
				List<DeliveryData> deliveryData = appData.getDeliveries();
				Collections.sort(deliveryData);

				for (int idx = deliveryData.size() - 1; idx >= 0; idx--) 
				{
					DeliveryData delivery = deliveryData.get(idx);
					String deliveryStatus = delivery.getDeliveryStatus();
					if ("delivery.StatusReadyForAnalysis".equals(deliveryStatus) || "delivery.StatusReadyForAnalysisAndDeployed".equals(deliveryStatus)) 
					{
						String DelvName = delivery.getDeliveryName();
						prevVersion = delivery.getDeliveryName();
						break;
					}
				}
			}
		}
		return prevVersion;
	}
	
	void setSchemaNamesInAOP(AbstractBuild build, BuildListener listener, String appName) throws IOException, InterruptedException
	{
    	EnvVars envVars = build.getEnvironment(listener);
		String webServiceAddress = envVars.get(Constants.CMS_WEB_SERVICE_ADDRESS);
	    String schemaPrefix = envVars.get(Constants.SCHEMA_PREFIX);
		CastWebServiceServiceLocator cbwsl = new CastWebServiceServiceLocator();
		cbwsl.setCastWebServicePortEndpointAddress(webServiceAddress);
		
		try {
			CastWebService cbws = cbwsl.getCastWebServicePort();
			
			String validateionProbURL = cbws.getValidationProbURL();
			if (validateionProbURL==null || validateionProbURL.isEmpty()) {
				listener.getLogger().println("Warning: Connection to AOP is not configured - schema names not updated");
			} else {	
				ValidationProbesService vps = new ValidationProbesService(validateionProbURL);
				
				vps.setSchemaNamesInAOP(appName, schemaPrefix);
				
				
			}
		} catch (ServiceException | UnsupportedOperationException | SOAPException | IOException e) {
			listener.getLogger().println("Error reading schema prefix from Jenkins");
		}
	}
	

	@SuppressWarnings("rawtypes")
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException,
			InterruptedException
	{ 
		
		int taskId;
		long startTime = System.nanoTime();

		EnvVars envVars = build.getEnvironment(listener);
		
        /*

		String cmsWebServiceAddress1 = envVars.get(Constants.CMS_WEB_SERVICE_ADDRESS); 
		CastWebServiceServiceLocator cbwsl1 = new CastWebServiceServiceLocator();
		cbwsl1.setCastWebServicePortEndpointAddress(cmsWebServiceAddress1);
		CastWebService cbws11 = null;
		try {
			cbws11 = cbwsl1.getCastWebServicePort();
		} catch (ServiceException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		String strQAScan = cbws11.getQAScanFlag();
		
		
		int startAt;
		try {
			startAt = Integer.parseInt(envVars.get(Constants.START_AT));
		} catch (NumberFormatException e) {
			startAt = 0;
		}
		
		String rescanType;
		try {
			rescanType = envVars.get(Constants.RESCAN_TYPE);
		} catch (Exception e) {
			rescanType = "PROD";
		}
		
		listener.getLogger().println(" ");
		if (startAt > Constants.RunDMT) {
			listener.getLogger().println(String.format("${START_AT} = %d, skipping delivery step.", startAt));
		} else {
			listener.getLogger().println("Deliver Application");

			String castDate = envVars.get(Constants.CAST_DATE);
			String dmtWebServiceAddress = envVars.get(Constants.DMT_WEB_SERVICE_ADDRESS);
			String cmsWebServiceAddress = envVars.get(Constants.CMS_WEB_SERVICE_ADDRESS); 
			String appName = envVars.get(Constants.APPLICATION_NAME,"");
			String schemaPrefix = envVars.get(Constants.SCHEMA_PREFIX,"");

			//cbws.setSchemaNamesInAOP(appName);
			
			String versionName = envVars.get(Constants.VERSION_NAME,"");
			 
			String workFlow = envVars.get(Constants.WORK_FLOW);
			
			String referenceVersion = "";
			
			if(strQAScan.toLowerCase().equals("true"))
			{
				if(rescanType.equals("QA"))
				{ 
					referenceVersion = envVars.get(Constants.REFERENCE_VERSION);
				}
				else
				{ 
					referenceVersion = envVars.get(Constants.REFERENCE_VERSION_PROD);
				}
			}
			else
			{
				referenceVersion = envVars.get(Constants.REFERENCE_VERSION);
			}

			
			
			
			
			boolean isUseJnlp = Boolean.parseBoolean(envVars.get(Constants.RUN_JNLP_DELIVERY));
			
			boolean failBuild = workFlow.trim().toLowerCase().equals("no");
			listener.getLogger().println("DMT Web Service: " + dmtWebServiceAddress);
			listener.getLogger().println("CMS Web Service: " + cmsWebServiceAddress);

			CastWebServiceServiceLocator cbwsld = new CastWebServiceServiceLocator();
			cbwsld.setCastWebServicePortEndpointAddress(dmtWebServiceAddress);
			
			CastWebServiceServiceLocator cbwsldd = new CastWebServiceServiceLocator();
			cbwsldd.setCastWebServicePortEndpointAddress(dmtWebServiceAddress);


			CastWebServiceServiceLocator cbwslc = new CastWebServiceServiceLocator(); 
			cbwslc.setCastWebServicePortEndpointAddress(dmtWebServiceAddress);
			
			
			
			try {
				CastWebServiceServiceLocator cbwsl = new CastWebServiceServiceLocator();
				cbwsl.setCastWebServicePortEndpointAddress(dmtWebServiceAddress);
				CastWebService cbws = cbwsl.getCastWebServicePort();
				cbws.setSchemaNamesInAOP(appName, schemaPrefix);
				if(strQAScan.toLowerCase().equals("true"))
				{
				listener.getLogger().println(String.format("Parsing Delivery folder to identify previous accepted version"));
				String deliveryFolder = cbws.getDMTDeliveryFolder();
				listener.getLogger().println(String.format("Delivery Folder: %s", deliveryFolder));
				listener.getLogger().println(String.format("App Name: %s", appName));
				
				
				
				listener.getLogger().println(String.format("Parsing start...."));
				String strPreviousVersion = "";
				strPreviousVersion =  cbws.getPrevDmtVersion(deliveryFolder ,appName ,rescanType);
				
				String validateionProbURL = cbws.getValidationProbURL();
				ValidationProbesService vps = null;
				listener.getLogger().println(String.format("AOP URL: %s", validateionProbURL));
				if(strPreviousVersion.equals(""))
				{
					if(rescanType.equals("QA"))
					{ 
						listener.getLogger().println(String.format("Issue finding previous version. `QA` prefixed (QA) DMT Accepted Delivery version must exist"));
					}
					else
					{ 
						listener.getLogger().println(String.format("Issue finding previous version. NON `QA` prefixed (PROD) DMT Accepted Delivery version must exist"));
					}
					
					if (validateionProbURL==null || validateionProbURL.isEmpty()) 
					{
						listener.getLogger().println("Warning: Connection to AOP is not configured - validation check has not been performed");
					} 
					else 
					{	
					vps = new ValidationProbesService(validateionProbURL);
					
					if (vps != null) 
						{
						cbws.UpdateRescanStatus(appName, versionName, castDate, "DMT - Error" , "DMT");
						}
					}
					return false;
					
				}
				else
				{
				listener.getLogger().println(String.format("Previous Version retrieved successfully: %s", strPreviousVersion)); 
				}
				referenceVersion = strPreviousVersion;
				}
				else
				{
					listener.getLogger().println(String.format("Parsing Delivery folder to identify previous accepted version"));
					String deliveryFolder = cbws.getDMTDeliveryFolder();
					listener.getLogger().println(String.format("Delivery Folder: %s", deliveryFolder));
					listener.getLogger().println(String.format("App Name: %s", appName));
					listener.getLogger().println(String.format("Rescan Type: %s", rescanType));
					
					
					listener.getLogger().println(String.format("Parsing start...."));
					String strPreviousVersion = "";
					strPreviousVersion =  cbws.getPrevDmtVersion(deliveryFolder ,appName ,"PROD");
					String validateionProbURL = cbws.getValidationProbURL();
					ValidationProbesService vps = null;
					listener.getLogger().println(String.format("AOP URL: %s", validateionProbURL));
					if(strPreviousVersion.equals(""))
					{
						listener.getLogger().println(String.format("Issue finding previous version. DMT Accepted Delivery version must exist"));
						
						if (validateionProbURL==null || validateionProbURL.isEmpty()) 
						{
							listener.getLogger().println("Warning: Connection to AOP is not configured - validation check has not been performed");
						} 
						else 
						{	
						vps = new ValidationProbesService(validateionProbURL);
						
						if (vps != null) 
							{
								cbws.UpdateRescanStatus(appName, versionName, castDate, "DMT - Error" , "DMT");
							}
						}
						return false;
						
					}
					else
					{
					listener.getLogger().println(String.format("Previous Version retrieved successfully: %s", strPreviousVersion)); 
					}
					referenceVersion = strPreviousVersion;
				}
				
				CastWebService cbwsd = cbwsld.getCastWebServicePort();
				CastWebService cbwsc = cbwslc.getCastWebServicePort();

				if (!Utils.validateWebServiceVersion(dmtWebServiceAddress, listener) ||
					 !Utils.validateWebServiceVersion(cmsWebServiceAddress, listener) ) {
					return false;
				}
  
				String appId = cbws.getApplicationUUID(appName);
				if (appId == null)
				{
					listener.getLogger().println("appId unavaliable");
					return false;
				}
				
				listener.getLogger().println("\nDelivery Manager Tool - DMT");
								
				Calendar cal = Utils.convertCastDate(castDate);
				startTime = System.nanoTime();
				
				// start delivery 
				if (isUseJnlp) // via aic portal
				{
					
					taskId = cbwsd.automateDeliveryJNLP(appId, appName, referenceVersion, versionName, cal);
				} else { // via cms
					taskId = cbwsd.deliveryManagerTool(appId, appName, referenceVersion, versionName, cal);
				}
				 
				if (taskId < 0) { // did the job start properly
					listener.getLogger().println(String.format("Error: %s", cbwsd.getErrorMessage(-taskId)));
					return false || failBuild;
				}

				//display logs and wait for completion code
				if (!Utils.getLog(cbwsd, taskId, startTime, listener))
				{
					cbwsd.DMTLogs(appId, appName, referenceVersion, versionName, cal);
					return false;
				}
 
				// run delivery report
				listener.getLogger().println(" ");
				listener.getLogger().println("Delivery Report");
				int retCode = 0;
				taskId = cbwsc.deliveryReport(appId, appName, referenceVersion, versionName, cal); 
				switch (taskId)
				{
					case -1:
						listener.getLogger().println("An exception has occured during the delivery report execution");
						listener.getLogger().println("See the CAST Batch Web Service mainlog for more information");
						listener.getLogger().println(String.format("Error: %s", cbwsc.getErrorMessage(-taskId)));
						break;
					case -2:
						listener.getLogger().println("Can't find java executor, please update CastAIPWS.properties file");
						break;
					case -3:
						listener.getLogger().println("Can't find CASTDeliveryReporter.jar file, please update CastAIPWS.properties file");
						break;
					case -4:
						listener.getLogger().println("Delivery folder has not been set, please update CastAIPWS.properties file");
						break;
					default:
						if (!Utils.getLog(cbwsc, taskId, startTime, listener)) {
							retCode = cbwsc.getTaskExitValue(taskId);
							
							if (retCode == -2) 
							{
								build.addAction(new PublishEnvVarAction("BUILD_STATUS", "Warning:  No Changes have been made for this delivery, analysis aborted"));
							} 
							
							return false;
						}						
						break;
				}
				listener.getLogger().println(" ");
				
			} catch (ServiceException | RemoteException | ParseException | HelperException e) {
				listener.getLogger().println(
						String.format("%s error accured while generating the packaging and delivering the code!",
								e.getMessage()));
				return false || failBuild;
			} catch (UnsupportedOperationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SOAPException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		//are there any remaining steps to run, if so run them now
		if (!Utils.runJobs(build, launcher, listener, this.getClass(), Constants.RunDMT))
		{
			return false;
		} 
		*/
		return true;
	}
	 
 
	
	/**
	 * Descriptor for {@link CastAIPBuilder}. Used as a singleton. The class is
	 * marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See
	 * <tt>src/main/resources/hudson/plugins/hello_world/CastDMTBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
//	@Extension
//	// This indicates to Jenkins that this is an implementation of an extension
//	// point.
//	public static final class DescriptorImpl extends BuildStepDescriptor<Builder>
//	{
//
//		private boolean useDMT;
//
//		public DescriptorImpl()
//		{
//			load();
//		}
//
//		@SuppressWarnings("rawtypes")
//		public boolean isApplicable(Class<? extends AbstractProject> aClass)
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
//			return "";
////			return String.format("CAST AIP %d: Deliver Application", Constants.RunDMT)  ;
//		}
//
//		@Override
//		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException
//		{
//			useDMT = formData.getBoolean("useDMT");
//			save();
//			return super.configure(req, formData);
//		}
//
//		public boolean getUseDMT()
//		{
//			return useDMT;
//		}
//	}
}
