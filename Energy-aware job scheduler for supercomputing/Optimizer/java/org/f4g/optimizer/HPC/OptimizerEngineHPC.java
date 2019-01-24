/**
* ============================== Header ============================== 
* file:          OptimizerEngineHPC.java
* project:       FIT4Green/Optimizer
* created:       26 nov. 2010 by cdupont
* last modified: $LastChangedDate: 2012-06-01 18:44:55 +0300 (pe, 01 kesä 2012) $ by $LastChangedBy: f4g.julichde $
* revision:      $LastChangedRevision: 1483 $
* 
* short description:
*   This class contains the algorithm for HPC computing.
*   
* ============================= /Header ==============================
*/

package org.f4g.optimizer.HPC;


import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import javax.xml.bind.JAXBElement;

import org.apache.commons.jxpath.JXPathContext;
import org.f4g.controller.IController;
import org.f4g.core.Constants;
import org.f4g.optimizer.ICostEstimator;
import org.f4g.optimizer.OptimizerEngine;
import org.f4g.optimizer.utils.Utils;
import org.f4g.power.IPowerCalculator;
import org.f4g.schema.actions.ActionRequestType;
import org.f4g.schema.actions.ActionRequestType.ActionList;
import org.f4g.schema.actions.ObjectFactory;
import org.f4g.schema.actions.PowerOffActionType;
import org.f4g.schema.actions.PowerOnActionType;
import org.f4g.schema.actions.StandByActionType;
import org.f4g.schema.actions.StartJobActionType;
import org.f4g.schema.allocation.AllocationRequestType;
import org.f4g.schema.allocation.AllocationResponseType;
import org.f4g.schema.allocation.HpcClusterAllocationResponseType;
import org.f4g.schema.allocation.HpcClusterAllocationType;
import org.f4g.schema.constraints.optimizerconstraints.BufferedNodesPolicyType;
import org.f4g.schema.constraints.optimizerconstraints.EnergyEstimationPolicyType;
import org.f4g.schema.constraints.optimizerconstraints.FairsharePolicyType;
import org.f4g.schema.constraints.optimizerconstraints.HPCPolicyType;
import org.f4g.schema.constraints.optimizerconstraints.NodeAccessPolicyType;
import org.f4g.schema.constraints.optimizerconstraints.PriorityPolicyType;
import org.f4g.schema.constraints.optimizerconstraints.SchedulingAlgorithmPolicyType;
import org.f4g.schema.constraints.optimizerconstraints.SingleSideFederationPolicyType;
import org.f4g.schema.constraints.optimizerconstraints.ThresholdPolicyType;
import org.f4g.schema.metamodel.ApplicationBenchmarkType;
import org.f4g.schema.metamodel.BladeServerType;
import org.f4g.schema.metamodel.CPUType;
import org.f4g.schema.metamodel.CoreLoadType;
import org.f4g.schema.metamodel.CoreType;
import org.f4g.schema.metamodel.CpuUsageType;
import org.f4g.schema.metamodel.DatacenterType;
import org.f4g.schema.metamodel.EnclosureType;
import org.f4g.schema.metamodel.FIT4GreenType;
import org.f4g.schema.metamodel.FanType;
import org.f4g.schema.metamodel.FrameworkCapabilitiesType;
import org.f4g.schema.metamodel.HardDiskType;
import org.f4g.schema.metamodel.IDREFS;
import org.f4g.schema.metamodel.IoRateType;
import org.f4g.schema.metamodel.JobPriorityType;
import org.f4g.schema.metamodel.JobStatusType;
import org.f4g.schema.metamodel.JobTimeType;
import org.f4g.schema.metamodel.JobType;
import org.f4g.schema.metamodel.MainboardType;
import org.f4g.schema.metamodel.MemoryUsageType;
import org.f4g.schema.metamodel.NodeStatusType;
import org.f4g.schema.metamodel.NrOfCoresType;
import org.f4g.schema.metamodel.NrOfNodesType;
import org.f4g.schema.metamodel.PowerType;
import org.f4g.schema.metamodel.QueueType;
import org.f4g.schema.metamodel.RAMStickType;
import org.f4g.schema.metamodel.RPMType;
import org.f4g.schema.metamodel.RackType;
import org.f4g.schema.metamodel.RackableServerType;
import org.f4g.schema.metamodel.ServerType;
import org.f4g.schema.metamodel.SiteType;
import org.f4g.schema.metamodel.SoftwareLicenseType;



/**
 * This class contains the algorithm for HPC computing.
 * 
 * @author cdupont
 *
 */
public class OptimizerEngineHPC extends OptimizerEngine{
	
	static List<JobType> jobList = new LinkedList<JobType>(); // List of jobs in the queue
	static List<ServerType> srvList = new LinkedList<ServerType> (); // List of Servers
	static List<JobType> runningList = new LinkedList<JobType>(); // List of running jobs
	static List<String> jobIdList = new ArrayList<String>();
	static List<Boolean> reservedList = new ArrayList<Boolean> (); 
	static List<Double> serverMemoryList = new ArrayList<Double> ();
	static List<Integer> serverCoresList = new ArrayList<Integer> ();
	static List<Integer> serverCoresInUseList = new ArrayList<Integer> ();
	static List<Cluster> clusterList = new LinkedList<Cluster> ();
	ActionRequestType actionRequest = new ActionRequestType(); 
	ActionList actionList = new ActionList();
	FIT4GreenType model = null;
	List<PowerOnActionType> serverOns = new ArrayList<PowerOnActionType> ();
	List<PowerOffActionType> serverOffs = new ArrayList<PowerOffActionType> ();

	static int scheduling = 0; // 0 = fifo, 1 = bff, 2 = bbf
	static int threshold = 0;
	static boolean poweroff = false;
	static boolean defaultscheduling = false;
	static boolean multipleJobsPerNode = true;
	static int bufferedIdleNodeSize = 0;
	static boolean shutDownReserved = true;
	static boolean federation = false;
	
	double fanRPMpercentage = 0.0;
	double hddReadRatepercentage = 0.0;
	double hddWriteRatepercentage = 0.0;
	
	String FAIRSHARE_TABLES_DIR;
	private FairshareTableMgmt fsPolicy = null;
	
	private HPCPolicyType policies;
	
	private int FSWEIGHT;
	private int CREDWEIGHT;
	private int SERVWEIGHT;

	/**
	 * @param main
	 */
	public OptimizerEngineHPC(IController controller, IPowerCalculator powerCalculator, ICostEstimator costEstimator) {
		super(controller, powerCalculator, costEstimator);
		
		//Properties prop = new Properties();
		
		// Set the simulation flag as false, considering measured power
		powerCalculator.setSimulationFlag(false);
	    
	    try
	    {
//	    	InputStream is = this.getClass().getClassLoader().getResourceAsStream("config/OptimizerEngineHPC.properties");
//	    	prop.load(is);
//	    	scheduling = Integer.parseInt(prop.getProperty("scheduling").trim());
//	    	threshold = Integer.parseInt(prop.getProperty("threshold").trim());
//	    	poweroff = Boolean.parseBoolean(prop.getProperty("poweroff").trim());
//	    	defaultscheduling = Boolean.parseBoolean(prop.getProperty("defaultscheduling").trim());
//	    	multipleJobsPerNode = Boolean.parseBoolean(prop.getProperty("multipleJobsPerNode").trim());
//	    	bufferedIdleNodeSize = Integer.parseInt(prop.getProperty("bufferedIdleNodeSize").trim());
//	    	shutDownReserved = Boolean.parseBoolean(prop.getProperty("shutDownReserved").trim());
//	    	federation = Boolean.parseBoolean(prop.getProperty("federation").trim());	    	

	    	String currentSlaClusterPathName = org.f4g.core.Configuration
	    	.get(Constants.SLA_CLUSTER_FILE_PATH);
	    	log.trace("SLA pathname:" + currentSlaClusterPathName);
	    	SLAReader slaReader = new SLAReader(currentSlaClusterPathName);							
			policies = slaReader.getPolicies();				
			List<HPCPolicyType.Policy> policiyList = policies.getPolicy();
			ListIterator<HPCPolicyType.Policy> lstIter = policiyList.listIterator();
			while (lstIter.hasNext()){
				HPCPolicyType.Policy policy = lstIter.next();
	    		
	    		PriorityPolicyType prPolicyType = policy.getPriorityPolicy();
	    		if(prPolicyType!=null){	    		
	    			FSWEIGHT = prPolicyType.getFSWEIGHT();
	    			CREDWEIGHT = prPolicyType.getCREDWEIGHT();
	    			SERVWEIGHT = prPolicyType.getSERVWEIGHT();
	    		}
	    		
	    		FairsharePolicyType fsPolicyType = policy.getFairsharePolicy();
	    		if(fsPolicyType!=null){	    		
	    			FAIRSHARE_TABLES_DIR = fsPolicyType.getFSUSAGEDATADIR();
	    			log.info("FAIRSHARE_TABLES_DIR : " + FAIRSHARE_TABLES_DIR);
	    			if(fsPolicy==null){
	    				fsPolicy = new FairshareTableMgmt(fsPolicyType.getFSDEPTH(), 
	    						fsPolicyType.getFSPOLICY().value(), 
	    						fsPolicyType.getFSINTERVAL(), 
	    						fsPolicyType.getFSDECAY(),
	    						FAIRSHARE_TABLES_DIR );
	    				fsPolicy.setWeights(fsPolicyType.getFSUSERWEIGHT(), 0, 0, fsPolicyType.getFSQUEUEWEIGHT());
	    				fsPolicy.setTargets(fsPolicyType.getFSUSERTARGET(), 0, 0, fsPolicyType.getFSQUEUETARGET());
	    			}		
	    		} 
	    		
	    		NodeAccessPolicyType nodeAccessPolicyType = policy.getNodeAccessPolicy();
	    		if(nodeAccessPolicyType!=null){	    		
	    			multipleJobsPerNode = nodeAccessPolicyType.isSharedNodes();
	    		} 
	    		
	    		SchedulingAlgorithmPolicyType schedulingAlgorithmPolicy = policy.getSchedulingAlgorithmPolicy();
	    		if(schedulingAlgorithmPolicy!=null){	    		
	    			scheduling = schedulingAlgorithmPolicy.getSchedulingAlgorithm();
	    		} 
	    		
	    		ThresholdPolicyType thresholdPolicy = policy.getThresholdPolicy();
	    		if(thresholdPolicy!=null){	    		
	    			threshold = thresholdPolicy.getThreshold();
	    		} 
	    		
	    		BufferedNodesPolicyType bufferedNodesPolicy = policy.getBufferedNodesPolicy();
	    		if(bufferedNodesPolicy!=null){	    		
	    			bufferedIdleNodeSize = bufferedNodesPolicy.getBufferedNodes();
	    		} 
	    		
	    		SingleSideFederationPolicyType singleSideFederationPolicy = policy.getSingleSideFederationPolicy();
	    		if(singleSideFederationPolicy!=null){	    		
	    			federation = singleSideFederationPolicy.isFederation();
	    		} 
	    		
	    		defaultscheduling=false;
	    		shutDownReserved=false;
	    		poweroff=true;
	    		
	    		log.debug("Scheduling parameter is set as: " + scheduling);
		    	log.debug("Threshold parameter is set as: " + threshold);
		    	log.debug("Poweroff parameter is set as: " + poweroff);
		    	log.debug("defaultscheduling parameter is set as: " + defaultscheduling);
		    	log.debug("multipleJobsPerNode parameter is set as: " + multipleJobsPerNode);
		    	log.debug("bufferedIdleNodeSize parameter is set as: " + bufferedIdleNodeSize);
		    	log.debug("shutDownReserved parameter is set as: " + shutDownReserved);
		    	log.debug("federation parameter is set as: " + federation);
		    	
		    	EnergyEstimationPolicyType energyEstimationPolicy = policy.getEnergyEstimationPolicy();
	    		if(energyEstimationPolicy!=null){	    		
	    			fanRPMpercentage = energyEstimationPolicy.getFanRPMpercentage();
	    			log.debug("fanRPMpercentage: " + fanRPMpercentage);
	    			hddReadRatepercentage = energyEstimationPolicy.getHddReadRatepercentage();
	    			log.debug("hddReadRatepercentage: " + hddReadRatepercentage);
	    			hddWriteRatepercentage = energyEstimationPolicy.getHddWriteRatepercentage();
	    			log.debug("hddWriteRatepercentage: " + hddWriteRatepercentage);
	    		} 
	    	}

	    }
	    catch (Exception e)
	    {
	    	// Some error with handling files, setting the scheduling as fifo
	    	scheduling = 0;
	    	threshold = 50;
	    	poweroff = false;
	    	defaultscheduling = false;
	    	multipleJobsPerNode = true;
	    	bufferedIdleNodeSize = 0;
	    	shutDownReserved = true;
	    	log.debug("Error with handling files, setting the scheduling as fifo: " + scheduling);
	    	log.debug("Error with handling files, setting the threshold as: " + threshold);
	    	log.debug("Error with handling files, setting the poweroff as: " + poweroff);
	    	log.debug("Error with handling files, setting the defaultscheduling as: " + defaultscheduling);
	    	log.debug("Error with handling files, setting the multipleJobsPerNode as: " + multipleJobsPerNode);
	    	log.debug("Error with handling files, setting the bufferedIdleNodeSize as: " + bufferedIdleNodeSize);
	    	log.debug("Error with handling files, setting the shutDownReserved as: " + shutDownReserved);
	    	log.debug("Error with handling files, setting the federation as: " + federation);
	    }
	}
	
	/**
	 * Gets a best cluster for a job in the federated scenario
	 * 
	 * @param int nodes, int cores, long memory, long wallTime, FIT4GreenType model
	 * @return Data Centre ID
	 */
	public String getBestCluster(HpcClusterAllocationType hpcRequest, FIT4GreenType model) {
		
		log.debug("OptimizerEngineHPC: getBestCluster: Getting best cluster for the job");

		String dcID = new String();
		if(hpcRequest.isEnergyAware() == false)
		{
			log.debug("OptimizerEngineHPC: Using fast as possible scheduling");
			dcID = getDCfastAsPossible(hpcRequest, model);
			log.debug("OptimizerEngineHPC: Selected cluster for job execution: " +dcID);
		}
		else if(hpcRequest.isEnergyAware() == true)
		{
			log.debug("OptimizerEngineHPC: Using energy aware scheduling");
			dcID = getDCpowerCalculator(hpcRequest, model);
			log.debug("OptimizerEngineHPC: Selected cluster for job execution: " +dcID);
		}
		return dcID;
			
	}
	
	/**
	 * Gets a best cluster fast as possible for a job in the federated scenario
	 * 
	 * @param int nodes, int cores, long memory, long wallTime, FIT4GreenType model
	 * @return Data Centre ID
	 */
	
	public String getDCfastAsPossible(HpcClusterAllocationType hpcRequest, FIT4GreenType model) {
		
		String dcID = new String();
				
		String tempString = hpcRequest.getSuitableClusters();
		String[] suitableClusters = tempString.split(" ");

		JobType myJob = new JobType();
		NrOfCoresType nOfCores = new NrOfCoresType();
		nOfCores.setValue(hpcRequest.getNeededCoresPerNode());
		myJob.setNeededCoresPerNode(nOfCores);
		
		MemoryUsageType neededMemory = new MemoryUsageType ();
		neededMemory.setValue(hpcRequest.getNeededMemory());
		myJob.setNeededMemory(neededMemory);
		
		NrOfNodesType nrOfNodes = new NrOfNodesType ();
		nrOfNodes.setValue(hpcRequest.getNumberOfNodes());
		myJob.setNumberOfNodes(nrOfNodes);
		
		JobTimeType wallTime = new JobTimeType ();
		wallTime.setValue(hpcRequest.getWallTime());
		myJob.setWallTime(wallTime);
		
		//int candidateQueueSize = -1;
		boolean isSuitable = false;
		//long latestFinish = hpcRequest.getLatestFinish();
		long estimatedLatestFinish;
		long estimatedWaitTime;
		long candidateFinish = -1;
		
		int dcRank = 0;
		int smallestRank = 0;
		double C = 0.0;
		
		// Get the smallest rank from metamodel
		smallestRank = getSmallestRank(hpcRequest.getBenchmarkName(), model);
		
		// Go through all the data centers of the meta-model
		String query = "//datacenter";
		JXPathContext context = JXPathContext.newContext(model);
		Iterator elements = context.iterate(query);
		
        // Iteration over the "datacenter" items
        while (elements.hasNext())
        {       	
        	Object element = elements.next();
        	
        	//Find out if the data centre is suitable for executing the job
        	String tempID = ((DatacenterType)element).getFrameworkCapabilities().get(0).getId();
        	isSuitable = isSuitableCluster(tempID, suitableClusters);
        	estimatedLatestFinish = 0;
        	estimatedWaitTime = 0;
        		
        	if(isSuitable)
        	{
        		log.debug("Data Centre is suitable: " +tempID);
        		dcRank = 0;
        		C = 0.0;
        		dcRank = getDCRank(hpcRequest.getBenchmarkName(), ((DatacenterType)element));
    			log.debug("smallestRank: " + smallestRank);
    			log.debug("dcRank: " + dcRank);
    			C = (double)smallestRank/(double)dcRank;
    			log.debug("C: " + C);
        		
				//Get the estimated wait time
				estimatedWaitTime = (long) (getEstimatedWaitTime(((DatacenterType)element), myJob) * 1.1);					
				estimatedLatestFinish = getEpochTime() + estimatedWaitTime + (long)(myJob.getWallTime().getValue() * C);
				log.debug("Data Centre estimatedWaitTime: " +estimatedWaitTime);
				log.debug("Data Centre estimatedLatestFinish: " +estimatedLatestFinish);
				
				// If the estimated waitTime is zero,
				// the data centre can start job execution immediately --> select it.
				if(estimatedWaitTime == 0)
				{
					log.debug("Resources were found and the size of the queue in the data centre is zero.");
					dcID = ((DatacenterType)element).getFrameworkCapabilities().get(0).getId();
					break;
				}
				//If no data centre was found that can execute the job immediately, find the data centre
				//that can start job execution in a minimal delay, i.e., has the smallest queue size.
				else if(estimatedLatestFinish < candidateFinish || candidateFinish == -1) {
					log.debug("estimatedLatestFinish in the data centre: " +getEpochDate(estimatedLatestFinish));
					candidateFinish = estimatedLatestFinish;
					dcID = ((DatacenterType)element).getFrameworkCapabilities().get(0).getId();
					log.debug("Selecting data center: "+dcID +", with the lowest estimated wait time.");
				}
//				else if(queueSize < candidateQueueSize || candidateQueueSize == -1)
//				{
//					log.debug("Size of the queue in the data centre: " +queueSize); 
//					log.debug("Resources found: " +resourcesFound);
//					candidateQueueSize = queueSize;
//					dcID = ((DatacenterType)element).getFrameworkCapabilities().get(0).getId();
//				}
        	}
        }
		
		return dcID;
		
	}
	
	
	/**
	 * Gets a best cluster energy aware by utilizing the power calculator 
	 * for a job in the federated scenario
	 * 
	 * @param int nodes, int cores, long memory, long wallTime, FIT4GreenType model
	 * @return Data Centre ID
	 */
	
	public String getDCpowerCalculator(HpcClusterAllocationType hpcRequest, FIT4GreenType model) {
		
		String dcID = new String();
		double jobPower = 0.0;
		double candidateJobPower = -1;
		//int queueSize = 0;
		//int numServers = 0;
		//double threshold = 0.0;
		double optObjective = 0.0;
		boolean clusterFound = false;
		//int candidateQueueSize = -1;
		long estimatedWaitTime;
		long estimatedLatestFinish;
		long candidateFinish = -1;
		long latestFinish = hpcRequest.getLatestFinish();
		
		int dcRank = 0;
		int smallestRank = 0;
		double C = 0.0;
		double energyChange = 0.0;
		
		// Get the smallest rank from metamodel
		smallestRank = getSmallestRank(hpcRequest.getBenchmarkName(), model);
		log.debug("Smallest rank: "+smallestRank);
		
		JobType myJob = new JobType();

		NrOfCoresType nOfCores = new NrOfCoresType();
		nOfCores.setValue(hpcRequest.getNeededCoresPerNode());
		myJob.setNeededCoresPerNode(nOfCores);
		
		MemoryUsageType neededMemory = new MemoryUsageType ();
		neededMemory.setValue(hpcRequest.getNeededMemory());
		myJob.setNeededMemory(neededMemory);
		
		NrOfNodesType nrOfNodes = new NrOfNodesType ();
		nrOfNodes.setValue(hpcRequest.getNumberOfNodes());
		myJob.setNumberOfNodes(nrOfNodes);
		
		JobTimeType wallTime = new JobTimeType ();
		wallTime.setValue(hpcRequest.getWallTime());
		myJob.setWallTime(wallTime);
						
		
		String tempString = hpcRequest.getSuitableClusters();
		String[] suitableClusters = tempString.split(" ");
		boolean isSuitable = false;
		
		log.debug("Optimization objective: " +getOptiObjective());
		
		// Go through all the sites of the meta-model
		String query = "//site";
		JXPathContext context = JXPathContext.newContext(model);
		Iterator elements = context.iterate(query);
		
        // Iteration over the "site" items
        while (elements.hasNext())
        {       	
        	Object element = elements.next();
        	
        	//Use either PUE or CUE based on the optimization objective
        	if(getOptiObjective().toString().matches("Power"))
        		optObjective = ((SiteType)element).getPUE().getValue();
        	else
        		optObjective = ((SiteType)element).getCUE().getValue();
        		
			Iterator<DatacenterType> dcItr = ((SiteType)element).getDatacenter().iterator();
			while(dcItr.hasNext()) 
			{
	    		//queueSize = 0;
	    		//numServers = 0;
	    		//threshold = 0.0;
				dcRank = 0;
	    		jobPower = 0.0;
	    		estimatedLatestFinish = 0;
	    		estimatedWaitTime = 0;
	    		C = 0.0;
	    		energyChange = 0.0;
	    		
				DatacenterType dcObject = dcItr.next();
				dcRank = getDCRank(hpcRequest.getBenchmarkName(), dcObject);
				//numServers = getClusterNumServers(dcObject);
				//queueSize = getQueueSize(dcObject);
				//log.debug("Cluster queueSize: " +queueSize + ", numServers: " +numServers);
				
	        	//Find out if the data centre is suitable for executing the job
	        	String tempID = dcObject.getFrameworkCapabilities().get(0).getId();
	        	isSuitable = isSuitableCluster(tempID, suitableClusters);
	        	if(isSuitable)
	        	{				
	        		// Andre: Find representative compute node 
	        		// Iteration over the Racks
	        		ServerType serverObject = null;
	        		boolean found_testserver = false;
	            	Iterator<RackType> rackIter = dcObject.getRack().iterator();
					while(rackIter.hasNext() && !found_testserver)
					{
						RackType rackType = rackIter.next();
						
						// Iteration over the "Enclosure" objects
						Iterator<EnclosureType> enclosureIter = rackType.getEnclosure().iterator();
						while(enclosureIter.hasNext() && !found_testserver)
						{
							EnclosureType enclosureType = enclosureIter.next();
							
							// Iteration over the "BladeServer" objects
							Iterator<BladeServerType> bladeIter = enclosureType.getBladeServer().iterator();
							while(bladeIter.hasNext() && !found_testserver)
							{
								ServerType bladeServer = bladeIter.next();								
								// The node is the RMS
					    		if(bladeServer.getName().toString().matches("HPC_COMPUTE_NODE"))
					    		{
					    			serverObject = bladeServer;
					    			found_testserver = true;
					    			break;
					    		}
							}
						}
						
						// Iteration over the "RackableServer" objects
						Iterator<RackableServerType> serverIter = rackType.getRackableServer().iterator();
						while(serverIter.hasNext() && !found_testserver)
						{
							ServerType server = serverIter.next();
				        	// The node is the RMS
				    		if(server.getName().toString().matches("HPC_COMPUTE_NODE"))
				    		{
				    			serverObject = server;
				    			found_testserver = true;
				    			break;
				    		}
						}
					} 
					// The node is a compute node
	        		if(serverObject.getName().toString().matches("HPC_COMPUTE_NODE"))
	        		{
	        					        					      			
	        			//Estimate the power consumption of the job in the cluster
	        			// by using a single server (all servers are homogeneous)
	        			ServerType tempServer = getServerIdle(serverObject, powerCalculator);
	        			
	        			double idlePower = 0.0;
	        			idlePower = computeIdlePower(tempServer, powerCalculator);
	        					
	        			log.debug("Idle power consumption of a server: " +idlePower);
	        			
	        			int serverCores = Utils.getNbCores(tempServer);
	        			int CPUs = getNbCPU(tempServer);
	        			int coresPerCPU = getNbCoresCPU(tempServer);
	        			
	        			log.debug("Number of total cores: " + serverCores);
	        			log.debug("Number of CPUs: " + CPUs );
	        			log.debug("Number of cores per CPU: " + coresPerCPU);
	        					        					        					        					        				        					        			
						// Server in the cluster should have enough cores
	        			if(serverCores >= myJob.getNeededCoresPerNode().getValue())
	        			{		        				
	        				jobPower = computePowerForJob(tempServer, myJob, powerCalculator);
	        				
	        			}
	        			log.debug("Change in power: " +(jobPower - idlePower));
	        			// Compute with job walltime and CUE to get emissions estimate
	        			log.debug("smallestRank: " + smallestRank);
	        			log.debug("dcRank: " + dcRank);
	        			C = (double)smallestRank/(double)dcRank;
	        			log.debug("C: " + C);
	        			jobPower = jobPower * myJob.getWallTime().getValue() * C * optObjective;
	        			log.debug("Job Energy Estimate: " + jobPower);
	        			energyChange = idlePower * myJob.getWallTime().getValue() * C * optObjective;
	        			log.debug("Idle Energy Estimate: " + energyChange);
	        			energyChange = jobPower - energyChange;
	        			log.debug("Change in energy: " + energyChange);
	        			
	        			//break;
	        		}
	        		//Andre: End
					
					
					
	        		
//	        		Iterator<RackableServerType> serverItr = dcObject .getRack().get(0).getRackableServer().iterator();					
//					while(serverItr.hasNext()) 
//					{
//						ServerType serverObject = serverItr.next();
//
//		        		// The node is a compute node
//		        		if(serverObject.getName().toString().matches("HPC_COMPUTE_NODE"))
//		        		{
//		        					        					      			
//		        			//Estimate the power consumption of the job in the cluster
//		        			// by using a single server (all servers are homogeneous)
//		        			ServerType tempServer = getServerIdle(serverObject, powerCalculator);
//		        			
//		        			double idlePower = 0.0;
//		        			idlePower = computeIdlePower(tempServer, powerCalculator);
//		        					
//		        			log.debug("Idle power consumption of a server: " +idlePower);
//		        			
//		        			int serverCores = Utils.getNbCores(tempServer);
//		        			int CPUs = getNbCPU(tempServer);
//		        			int coresPerCPU = getNbCoresCPU(tempServer);
//		        			
//		        			log.debug("Number of total cores: " + serverCores);
//		        			log.debug("Number of CPUs: " + CPUs );
//		        			log.debug("Number of cores per CPU: " + coresPerCPU);
//		        					        					        					        					        				        					        			
//							// Server in the cluster should have enough cores
//		        			if(serverCores >= myJob.getNeededCoresPerNode().getValue())
//		        			{		        				
//		        				jobPower = computePowerForJob(tempServer, myJob, powerCalculator);
//		        				
//		        			}
//		        			log.debug("Change in power: " +(jobPower - idlePower));
//		        			// Compute with job walltime and CUE to get emissions estimate
//		        			log.debug("smallestRank: " + smallestRank);
//		        			log.debug("dcRank: " + dcRank);
//		        			C = (double)smallestRank/(double)dcRank;
//		        			log.debug("C: " + C);
//		        			jobPower = jobPower * myJob.getWallTime().getValue() * C * optObjective;
//		        			log.debug("Job Energy Estimate: " + jobPower);
//		        			energyChange = idlePower * myJob.getWallTime().getValue() * C * optObjective;
//		        			log.debug("Idle Energy Estimate: " + energyChange);
//		        			energyChange = jobPower - energyChange;
//		        			log.debug("Change in energy: " + energyChange);
//		        			
//		        			break;
//		        		}
//						
//					}
					//threshold = (double)queueSize/numServers;
					
					//Get the estimated wait time
					estimatedWaitTime = (long) (getEstimatedWaitTime(dcObject, myJob) * 1.1);					
					estimatedLatestFinish = getEpochTime() + estimatedWaitTime + (long)(myJob.getWallTime().getValue() * C);
					log.debug("Estimated wait time of the cluster: " +estimatedWaitTime);
					log.debug("Estimated start time of the job: " + getEpochDate(getEpochTime() + estimatedWaitTime));
					log.debug("Estimated Latest Finish date of the Job: " + getEpochDate(estimatedLatestFinish));
					log.debug("User defined latest Finish date of the Job: " + getEpochDate(latestFinish));									
					
				
					
					//If the threshold is below 0.7, select the cluster
					// and set clusterFound = true
					//if(threshold < 0.7)
					
					//If the estimated Latest Finish time of the job is smaller or equal
					//to the user defined latest finish time, select the cluster
					//and set clusterFound = true
					if(estimatedLatestFinish <= latestFinish || latestFinish == 0)
					{
						if(jobPower < candidateJobPower || candidateJobPower == -1)
						{
							candidateJobPower = jobPower;
							dcID = dcObject.getFrameworkCapabilities().get(0).getId();
							log.debug("Estimated latest finish <= latestFinish");
							log.debug("Selecting cluster with the lowest energy estimate: " +dcID);
							clusterFound = true;
						}					
					}
					//If no cluster has been found where estimated Latest Finish time of the job
					//is smaller or equal to the user defined latest finish time,
					//select a cluster with the smallest estimated Latest Finish time.
					else if(clusterFound == false)
					{
						
						if(estimatedLatestFinish < candidateFinish || candidateFinish == -1)
						{
							log.debug("estimatedLatestFinish in the data centre: " +getEpochDate(estimatedLatestFinish));
							candidateFinish = estimatedLatestFinish;
							dcID = dcObject.getFrameworkCapabilities().get(0).getId();
							log.debug("Selecting cluster with the lowest estimatedLatestFinish: " +dcID);
						}
						
//						if(queueSize < candidateQueueSize || candidateQueueSize == -1)
//						{
//							log.debug("Size of the queue in the data centre: " +queueSize); 
//							candidateQueueSize = queueSize;
//							dcID = dcObject.getFrameworkCapabilities().get(0).getId();
//						}
						
					}
	        	}
			}
        }
		
		return dcID;
		
	}
		
	/**
	 * Used for testing purposes
	 * 
	 */
	
	public void testAllocateResource()
	{
		AllocationRequestType request = new AllocationRequestType();
		//Creates a request
		HpcClusterAllocationType hpcRequest = new HpcClusterAllocationType();
		hpcRequest.setNeededCoresPerNode(4);
		hpcRequest.setNeededMemory(2);
		hpcRequest.setNumberOfNodes(1);
		hpcRequest.setWallTime(600);
		hpcRequest.setEnergyAware(true);
		hpcRequest.setLatestFinish(getEpochTime() + 500);
		//hpcRequest.setSuitableClusters("FzjComJufit FzjComJuggle ComFzjDune");
		hpcRequest.setSuitableClusters("FzjComJuggle FzjComJufit ComVTTDune");
		hpcRequest.setBenchmarkName("Linpack");
		
		org.f4g.schema.allocation.ObjectFactory allocationFactory = new org.f4g.schema.allocation.ObjectFactory();		
		request.setRequest((allocationFactory.createHpcClusterAllocation(hpcRequest)));
		
		AllocationResponseType response = allocateResource(request, getModelCopy());

		HpcClusterAllocationResponseType hpcResponse = (HpcClusterAllocationResponseType)response.getResponse().getValue();
		
		log.debug("Selected cluster ID: " +hpcResponse.getClusterId());
	}
	
	
	private void addJob(ServerType servertype){
		/// Form temporary data structures for the queue and running jobs		
		Iterator<QueueType> queueIterator = servertype.getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
		while(queueIterator.hasNext()){
			QueueType queueType = queueIterator.next();
			Iterator<JobType> itr = queueType.getJobs().iterator();
			while(itr.hasNext()) 
			{
				// Retrieve the job from the meta-model and place it into a list
				JobType jobObject = itr.next();
				enqueue(jobObject);
//				if(!jobObject.getStatus().equals(JobStatusType.RESERVED)){
//					enqueue(jobObject);
//				}				
			}
		}
	}
	
	private void addNode(ServerType servertype){
		
		double serverMemory;	
		
		// Create a temporary server structure
		ServerType tempServer = servertype;   			
		serverMemory = 0;				
		
		// Iteration over the RAMStick objects
		Iterator<RAMStickType> iter = tempServer.getMainboard().get(0).getRAMStick().iterator();
		while(iter.hasNext())
		{
			RAMStickType tempObject =  iter.next();
			serverMemory = serverMemory + tempObject.getSize().getValue();
			
		}	
		

		try {
			
			Iterator<Object> jobIter = tempServer.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
			while(jobIter.hasNext())
			{
				JobType tempJob =  (JobType) jobIter.next();
				serverMemory = serverMemory - tempJob.getNeededMemory().getValue();				
			}
		}
		catch (NullPointerException e)
		{
			//log.debug("No job ref associated.");
		}
		tempServer.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(serverMemory);
		
		NodeStatusType status = tempServer.getNativeOperatingSystem().getNode().get(0).getStatus();								
		if(status.toString().matches("RESERVED") || status.toString().matches("POWERING_ON") || status.toString().matches("POWERING_OFF") || status.toString().matches("OFF"))
		{
			//Do Nothing
		}
		else
		{
			srvList.add(tempServer); 			
		}
		
								
	}
	
		
	/**
	 * Handles a request for a global optimization
	 * 
	 * @param model the f4g model
	 * @return none
	 */
	@Override
	public void runGlobalOptimization(FIT4GreenType model) {
		
		//TODO: this function should be able to distinguish which cluster wants global optimization
		
		log.debug("OptimizerEngineHPC: runGlobalOptimization: Performing Global Optimization");		
		setModelCopy(model);
		
		//Just used for testing purposes
		//testAllocateResource();
		
				
		// Clear the actionList in the beginning, 
		// to make sure that it is empty before creating any actions
		try {		
			actionRequest.getActionList().getAction().clear();
			actionList.getAction().clear();
		}
		catch (NullPointerException e)
		{
			
		}

		
		if(federation == true)
		{
		
			// Go through the meta-model and forms temporary data structures for
			// the job queue, jobs that are currently running, and servers

			
			log.debug("federation is true.");
	
			String myQuery = "//datacenter";
			JXPathContext context = JXPathContext.newContext(getModelCopy());
			Iterator elements = context.iterate(myQuery);
			
	        // Iteration over the "dataCenter" items
	        while (elements.hasNext())
	        {
	    			    		
	        	DatacenterType datacenter = (DatacenterType)elements.next();
	        	if(datacenter.getFrameworkCapabilities().get(0).getStatus().toString().matches("RUNNING"))
	    		{
	            	log.debug("OptimizerEngineHPC: Performing Global Optimization on Data Centre: " +datacenter.getFrameworkCapabilities().get(0).getId());		
	            	// Iteration over the Racks
	            	Iterator<RackType> rackIter = datacenter.getRack().iterator();
					while(rackIter.hasNext())
					{
						RackType rackType = rackIter.next();
						// Iteration over the "Enclosure" objects
						Iterator<EnclosureType> enclosureIter = rackType.getEnclosure().iterator();
						while(enclosureIter.hasNext())
						{
							EnclosureType enclosureType = enclosureIter.next();
							
							// Iteration over the "BladeServer" objects
							Iterator<BladeServerType> bladeIter = enclosureType.getBladeServer().iterator();
							while(bladeIter.hasNext())
							{
								ServerType bladeServer = bladeIter.next();								
								// The node is the RMS
					    		if(bladeServer.getName().toString().matches("HPC_RESOURCE_MANAGEMENT"))
					    		{
					    			addJob(bladeServer);
					    		}
					    		else
					    		{
					    			addNode(bladeServer);
					    		}
							}
						}
						
						// Iteration over the "RackableServer" objects
						Iterator<RackableServerType> serverIter = rackType.getRackableServer().iterator();
						while(serverIter.hasNext())
						{
							ServerType server = serverIter.next();
				        	// The node is the RMS
				    		if(server.getName().toString().matches("HPC_RESOURCE_MANAGEMENT"))
				    		{
				    			addJob(server);
				    		}
				    		else
				    		{
				    			addNode(server);
				    		}
						}
						
					} 
	    		}
	    		
	        	//TODO: solve better, to get correct site
	        //	SiteType site = model.getSite().get(0);
	    		Cluster tempCluster = new Cluster();
	    		tempCluster.clusterSrvList = srvList;
	    	//	tempCluster.name = site.getName();
	    		   		
	    		//Add licenses and name to the cluster type
//				if(!(site.getSoftwareLicense().isEmpty()))
//				{
//					for(SoftwareLicenseType license : site.getSoftwareLicense())
//					{
//						tempCluster.licenseList.add(license);
//					}
//				}
	    		
	    		clusterList.add(tempCluster);
	    		
	    	    log.debug("srvList size: "+srvList.size());
	        
		        log.debug("DATA STRUCTURES BEFORE SCHEDULING AND OPTIMIZATION:");
				
		        // Print the queue and the serverList, used for debugging
				printQueue();
				printServerList();
							
				if(defaultscheduling == false)
				{
					// Go through the job queue and check which jobs can be executed
					log.debug("Default scheduling is NOT active..");
					log.debug("Multiple jobs per node: " +multipleJobsPerNode);
					dequeue();
				}
				else
				{
					// Actions generated when the default scheduler is used alongside F4G
					log.debug("Default scheduling is active..");
					log.debug("Multiple jobs per node: " +multipleJobsPerNode);
					// Checks if nodes need to be woken up for immediate job execution
					fit4greenActions();
				}
				
				checkIdleServers(datacenter, srvList);
								
				// Print necessary lists after going through the queue, used for debugging
				log.debug("DATA STRUCTURES AFTER SCHEDULING AND OPTIMIZATION:");
				printQueue();
				printServerList();
				
						
				// Clear all temporary data structures
				jobList.clear();
				runningList.clear();
				srvList.clear();
				jobIdList.clear();
				reservedList.clear();
				serverMemoryList.clear();
				serverCoresList.clear();
				serverCoresInUseList.clear();
				clusterList.clear();
							
				// Set the action list to the actionRequest
				actionRequest.setActionList(actionList);
					
					
				// Check if there are any actions to execute
				boolean isActionListEmpty = checkActionList();
				
				if(isActionListEmpty == false)
				{
					printActionList();
					//Report the power before optimization
					//DatacenterType tempdatacenter = Utils.getFirstDatacenter(model);
					double powerSaved = 0.0;
					double powerFormerly = 0.0;
					//double powerFormerly = powerCalculator.computePowerDatacenter(tempdatacenter).getActualConsumption();
					log.debug("datacenter power consumption before:" + powerFormerly);
					PowerType powerBefore = new PowerType();
					powerBefore.setValue(powerFormerly);
					
					PowerType powerAfter = new PowerType();
					powerAfter.setValue(0.0);
					log.debug("powerSaved: " + powerSaved);
					actionRequest.setComputedPowerBefore(powerBefore);
					actionRequest.setComputedPowerAfter(powerAfter);
					// invoke the Controller
					controller.executeActionList(actionRequest);
				}
				else
				{
					log.debug("The action list is empty, no actions to be done.");
					
					// Create a dummy job
					JAXBElement<StartJobActionType>  dummyJob = (new ObjectFactory()).createStartJob(new StartJobActionType());
					dummyJob.getValue().getNodeName().add("EndofOptimization");
					dummyJob.getValue().setFrameworkName(datacenter.getFrameworkCapabilities().get(0).getFrameworkName());
					dummyJob.getValue().setJobID("EndofOptimization");
					actionList.getAction().add(dummyJob);
					
					log.debug("Dummy job action framework name: "+dummyJob.getValue().getFrameworkName());
					log.debug("Dummy job action Job ID: "+dummyJob.getValue().getJobID());
					log.debug("Dummy job action Node Name: "+dummyJob.getValue().getNodeName().get(0));
												
					controller.executeActionList(actionRequest);
				}
											
				// Clear the actionList
				actionRequest.getActionList().getAction().clear();
				actionList.getAction().clear();
	      
	        }
		}
		else
		{
			log.debug("federation is NOT active..");
			createTemporaryDataStructures(model);
			log.debug("DATA STRUCTURES BEFORE SCHEDULING AND OPTIMIZATION:");
			
	        // Print the queue and the serverList, used for debugging
			printQueue();
			printServerList();
			
			if(defaultscheduling == false)
			{
				// Go through the job queue and check which jobs can be executed
				log.debug("Default scheduling is NOT active..");
				log.debug("Multiple jobs per node: " +multipleJobsPerNode);
				dequeue();
			}
			else
			{
				// Actions generated when the default scheduler is used alongside F4G
				log.debug("Default scheduling is active..");
				log.debug("Multiple jobs per node: " +multipleJobsPerNode);
				// Checks if nodes need to be woken up for immediate job execution
				fit4greenActions();
			}
			
			for(int i = 0; i < clusterList.size(); i++)
			{
								
				checkIdleServers(model.getSite().get(0).getDatacenter().get(0), clusterList.get(i).getSrvList()); // Check if some servers can be shut down
			}

								
			// Print necessary lists after going through the queue, used for debugging
			log.debug("DATA STRUCTURES AFTER SCHEDULING AND OPTIMIZATION:");
			printQueue();
			printServerList();
			
					
			// Clear all temporary data structures
			jobList.clear();
			runningList.clear();
			srvList.clear();
			jobIdList.clear();
			reservedList.clear();
			serverMemoryList.clear();
			serverCoresList.clear();
			serverCoresInUseList.clear();
			clusterList.clear();
						
			// Set the action list to the actionRequest
			actionRequest.setActionList(actionList);
				
				
			// Check if there are any actions to execute
			boolean isActionListEmpty = checkActionList();
			
			if(isActionListEmpty == false)
			{
				printActionList();
				//Report the power before optimization
				//DatacenterType tempdatacenter = Utils.getFirstDatacenter(model);
				double powerSaved = 0.0;
				double powerFormerly = 0.0;
				//double powerFormerly = powerCalculator.computePowerDatacenter(tempdatacenter).getActualConsumption();
				log.debug("datacenter power consumption before:" + powerFormerly);
				PowerType powerBefore = new PowerType();
				powerBefore.setValue(powerFormerly);
				
				PowerType powerAfter = new PowerType();
				powerAfter.setValue(0.0);
				log.debug("powerSaved: " + powerSaved);
				actionRequest.setComputedPowerBefore(powerBefore);
				actionRequest.setComputedPowerAfter(powerAfter);
				// invoke the Controller
				controller.executeActionList(actionRequest);
			}
			else
			{
				log.debug("The action list is empty, no actions to be done.");
				
				// Create a dummy job
				JAXBElement<StartJobActionType>  dummyJob = (new ObjectFactory()).createStartJob(new StartJobActionType());
				dummyJob.getValue().getNodeName().add("EndofOptimization");
				dummyJob.getValue().setFrameworkName(model.getSite().get(0).getDatacenter().get(0).getFrameworkCapabilities().get(0).getFrameworkName());
				dummyJob.getValue().setJobID("EndofOptimization");
				actionList.getAction().add(dummyJob);
				
				log.debug("Dummy job action framework name: "+dummyJob.getValue().getFrameworkName());
				log.debug("Dummy job action Job ID: "+dummyJob.getValue().getJobID());
				log.debug("Dummy job action Node Name: "+dummyJob.getValue().getNodeName().get(0));
											
				controller.executeActionList(actionRequest);
			}
										
			// Clear the actionList
			actionRequest.getActionList().getAction().clear();
			actionList.getAction().clear();
			
			
			
		}
		
	}

	/**
	 * Handles a request for resource allocation
	 * 
	 * @param allocationRequest Data structure describing the resource allocation request 
	 * @return A data structure representing the result of the allocation
	 */
	@Override
	public AllocationResponseType allocateResource(AllocationRequestType allocationRequest, FIT4GreenType model) {
		
		log.debug("Processing request: " + allocationRequest);
		log.debug(" Request Operation: " + allocationRequest.getRequest().getValue());
		setModelCopy(model);
		
		// The allocation request must be of type HPCClusterAllocation
		if (allocationRequest == null ||
				allocationRequest.getRequest() == null ||	
				! (allocationRequest.getRequest().getValue() instanceof HpcClusterAllocationType)) {

				log.warn("Allocation request is not correct for HPC");
				return null;
		}
		
		HpcClusterAllocationType hpcRequest = (HpcClusterAllocationType)allocationRequest.getRequest().getValue();
		
		log.debug(" Request Nodes: " + hpcRequest.getNumberOfNodes());
		log.debug(" Request Cores: " + hpcRequest.getNeededCoresPerNode());
		log.debug(" Request Memory: " + hpcRequest.getNeededMemory());
		log.debug(" Request WallTime: " + hpcRequest.getWallTime());
				
		//Based on the request values, get a suitable cluster
		String clusterID = new String();
		clusterID = getBestCluster(hpcRequest, model);
		
		//Creates a response
		HpcClusterAllocationResponseType hpcResponse = new HpcClusterAllocationResponseType();	
		hpcResponse.setClusterId(clusterID);
		
		org.f4g.schema.allocation.ObjectFactory allocationFactory = new org.f4g.schema.allocation.ObjectFactory();
		
		AllocationResponseType response  = new AllocationResponseType();
		response.setResponse((allocationFactory.createHpcClusterAllocationResponse(hpcResponse)));
					
		//response.setAllocationLog("OptimizerHPC: Allocated: expr=" + allocationRequest.getExpression() + ", value=" + allocationRequest.getValue());
		return response;
	}
	
	/**
	 * Method for searching servers
	 * 
	 * @param requirements for a job 
	 * @return servers found for the job
	 */
	
	public int[] searchServers(JobType myJob, int numNodes, double memory, int numCores, int[] serverNumber, List<ServerType> tempSrvList)
	{
		
		//String jobProperties = myJob.getPropertiesOfNodes().get(0).value();
		
		
		// Initialize all elements as -1
		for(int i = 0; i < numNodes ; i++)
		{
			serverNumber[i] = -1;
		}
		int coresInUse = 0;
		int availableCores = 0;
		int candidateCores;
		
		
		// Search for appropriate servers
		// This search assumes that multiple jobs can be placed on a node
		if(multipleJobsPerNode == true)
		{
			for(int i = 0; i < numNodes; i++)
			{
				candidateCores = -1;
				//log.debug("Searching for number: " +i);
				
				for(int j = 0; j < tempSrvList.size(); j++)
				{
					ServerType server = tempSrvList.get(j);
					//coresInUse = serverCoresInUseList.get(j);
					coresInUse = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue();
					
					//availableCores = serverCoresList.get(j) - coresInUse;
					availableCores = Utils.getNbCores(server) - coresInUse;
					
					NodeStatusType status = server.getNativeOperatingSystem().getNode().get(0).getStatus();
					double availableMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
					boolean reserved = server.getNativeOperatingSystem().getNode().get(0).isReserved();
					
					//log.debug("Server cores: " +serverCoresList.get(j));
					//log.debug("Available cores: " +availableCores); .toString().matches("IDLE")
					//log.debug("Cores in use: " +coresInUse);
					
					// The server must have enough free cores and enough memory
					// Status can be even running
					if(reserved == false && availableMemory >= memory && availableCores >= numCores)
					{
						
						//log.debug("Candidate cores: " +candidateCores);	
						//Select the server with the least amount of available cores
						if(availableCores <= candidateCores || candidateCores == -1)
						{						
							serverNumber[i] = j;
							candidateCores = availableCores;
							//log.debug("Selecting server: " + j +" CoresInUse: " +(serverCoresInUseList.get(j) + numCores));				
							continue;
						}	
						else if((availableCores == candidateCores)  && (status.toString().matches("IDLE")))
						{
							serverNumber[i] = j;
							candidateCores = availableCores;
							//log.debug("Selecting server: " + j +" CoresInUse: " +(serverCoresInUseList.get(j) + numCores));				
							continue;
						}
					}
				}// for j
				
				if(serverNumber[i] != -1)
				{
					tempSrvList.get(serverNumber[i]).getNativeOperatingSystem().getNode().get(0).setReserved(true);
					//reservedList.set(serverNumber[i], true);
				}
				
			} // for i
		}
		//Else: only one job per node is possible
				
		else
		{
							
			for(int i = 0; i < numNodes; i++)
			{
				
				for(int j = 0; j < tempSrvList.size(); j++)
				{
																
					ServerType server = tempSrvList.get(j);
					//coresInUse = serverCoresInUseList.get(j);
					//availableCores = serverCoresList.get(j) - coresInUse;
					
					NodeStatusType status = server.getNativeOperatingSystem().getNode().get(0).getStatus();
					double availableMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
					boolean reserved = server.getNativeOperatingSystem().getNode().get(0).isReserved();
					// The server must have enough free cores and enough memory
					// Status cannot be running && availableCores >= numCores
					// node properties should match job properties
					//if(reservedList.get(j) == false && serverMemoryList.get(j) >= memory  && nodeProperties.matches(jobProperties))
					if(reserved == false && availableMemory >= memory)
					{
						if(status.toString().matches("IDLE"))
						{
							serverNumber[i] = j;
							break;
						}
						else if (status.toString().matches("DOWN") || status.toString().matches("STANDBY") || status.toString().matches("HYBERNATED"))
						{
							serverNumber[i] = j;
							continue; // Continue searching for a server that is idle.
						}
						else if (status.toString().matches("RUNNING"))
						{
							//Do nothing, since the server is already reserved.
						}

					}
				}// for j
				
				if(serverNumber[i] != -1)
				{
					tempSrvList.get(serverNumber[i]).getNativeOperatingSystem().getNode().get(0).setReserved(true);
					//tempSrvList.get(serverNumber[i]).getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("RESERVED"));
					//reservedList.set(serverNumber[i], true);
				}
				
			} // for i
		}
		
//		for(int i = 0; i < numNodes ; i++)
//		{
//			log.debug("Server number: "+serverNumber[i]);
//		}
				
		return serverNumber;
	}
	
	/**
	 * Method for checking if servers were found
	 * 
	 * @param serverNumber[], serversFound, number of nodes a job requires 
	 * @return boolean serversFound
	 */
	
	public boolean checkServerNumber(int[] serverNumber, boolean serversFound, int numNodes, List<ServerType> tempSrvList)
	{
		serversFound = true;
		// Check if all needed servers were found
		for(int i = 0; i < numNodes ; i++)
		{
			if(serverNumber[i] == -1)
			{
				serversFound = false;
			}
			else
			{
				// set reserved to false
				tempSrvList.get(serverNumber[i]).getNativeOperatingSystem().getNode().get(0).setReserved(false);
				//serversFound = true;
				//reservedList.set(serverNumber[i], false);
			}
		}
	
		return serversFound;
	}
	
	/**
	 * Method for placing a JobType object into jobList
	 * Forms the queue based on the job priority
	 * @param JobType
	 */
	public void enqueue(JobType job)
	{
		
		int jobPriority = job.getPriority().getValue();
		long jobCompTime = job.getTimeOfStart().getValue() + job.getWallTime().getValue();
		int tempPriority;
		long tempCompTime;
		boolean jobInserted;
		
		jobInserted = false;
		
		// If the job is queued, add it into the job queue
		if(job.getStatus().toString().matches("QUEUED"))
		{
			// If the list is empty, just place it at the end
			if(jobList.isEmpty())
			{
				jobList.add(job);
			}	
			else
			{
				// Form the queue based on the priority values,
				// high priority jobs going into the top
				for(int i = 0; i < jobList.size(); i++)
				{
					JobType tempjob = jobList.get(i);
					tempPriority = tempjob.getPriority().getValue();
					if(jobPriority > tempPriority)
					{
						jobList.add(i, job);
						jobInserted = true;
						break;
					}				
				}
				if(jobInserted == false)
				{
					jobList.add(job);
				}		
			}
		}
		// Else: add the job into the running jobs list
		else if(job.getStatus().toString().matches("RUNNING"))
		{
			// If the list is empty, just place it at the end
			if(runningList.isEmpty())
			{
				runningList.add(job);
			}	
			else
			{
				// Form queue based on priority
				for(int i = 0; i < runningList.size(); i++)
				{
					JobType tempjob = runningList.get(i);
					tempCompTime = tempjob.getTimeOfStart().getValue() + tempjob.getWallTime().getValue();
					if(jobCompTime < tempCompTime)
					{
						runningList.add(i, job);
						jobInserted = true;
						break;
					}				
				}
				if(jobInserted == false)
				{
					runningList.add(job);
				}		
			}
		}
	}	
	
	/**
	 * Method for dequeuing a job from queue
	 * 
	 */
	
	public void dequeue()
	{
		// Priority FIFO
		if(scheduling == 0)
		{
			if(jobList.isEmpty())
			{
				log.debug("Queue empty.");				
			}
			else
			{
				findResources(); // Find resources for the first job in the queue
				log.debug("Finished checking the queue, now checking for idle servers...");
			}
		}
		// Backfill first fit algorithm
		else if (scheduling == 1)
		{
			if(jobList.isEmpty())
			{
				log.debug("Queue empty.");
			}
			else
			{
				findResources(); // Find resources for the first job in the queue
				
				// Do backfilling if the list is not empty and there is more than one element in the queue
				if(!jobList.isEmpty() && jobList.size() > 1)
				{
					log.debug("Executing backfill first fit..");					
					int index = 1; // Index starts from 1, since the 1st element in the queue is 0
					
					do
					{
						
						JobType temp_job = jobList.get(index); // Pop a job from the queue
						boolean resourcesFound = true;
						int nodes;
						nodes = temp_job.getNumberOfNodes().getValue();
						int[] serverNumber = new int[nodes];
						
						//TODO: license check + update of the licenses to the Cluster object
						boolean licenseAvailable = true;
						boolean licenseRequired = false;
						//Check if the job requires a license
						try
						{
							String requestedSoftware = temp_job.getRequestedSoftware();
							//log.debug("Requested software is: " +requestedSoftware);
							if(!(requestedSoftware.isEmpty()))
							{
								licenseAvailable = isLicensesAvailable(temp_job);
								if(licenseAvailable == true)
								{
									licenseRequired = true;
								}
							}
							
						}
						catch(NullPointerException e)
						{
					
							// no license required
							//log.debug("no license required.");
							licenseAvailable = true;
						}
						
						if(licenseAvailable == true)
						{
							//1. time
							// Get estimated for first job in each cluster
							// form temp data structrure for servers in the cluster: tempSrvList
							// time = getEstimatedStartTime(tempSrvList);
							// time > newJobFinish
							//2. resources
							// searchServers(tempSrvList);
							// Go through each cluster and check if resources are found
							for(int clusterIndex = 0; clusterIndex < clusterList.size(); clusterIndex++)
							{
								log.debug("Cluster: " +clusterList.get(clusterIndex).getName());
								// Search for appropriate servers
								searchServers(temp_job, nodes, temp_job.getNeededMemory().getValue(), temp_job.getNeededCoresPerNode().getValue(), serverNumber, clusterList.get(clusterIndex).getSrvList());
								
								// Check if the servers were found
								resourcesFound = checkServerNumber(serverNumber, resourcesFound, nodes, clusterList.get(clusterIndex).getSrvList());
								
								if (resourcesFound == false)
								{
									// No servers found, continue the search
									log.debug("No servers were found for the job index: " +index);
								}
								else
								{
									log.debug("Servers were found for the job index: " +index);
									
									// Get the estimated completion time of the new job
									long compTime = getEpochTime() + temp_job.getWallTime().getValue();
															
									// Get the estimated start time of the 1st job in the queue
									long startTime;
									startTime = getEstimatedStartTime(clusterList.get(clusterIndex).getSrvList());
									log.debug("Estimated start time of the 1st job: " +startTime);
									log.debug("Completion time of the new job: " + compTime);
									
									// Check if the job can be executed before the 1st job
									if (startTime > compTime)
									{
										// Decrease the licenses
										if(licenseRequired == true)
											decreaseLicenses(temp_job);
										
										log.debug("The job can be executed before the 1st element in the queue.");										
										temp_job.getTimeOfStart().setValue(getEpochTime());
																																		
										// Generate actions that the server needs to do
										for(int i = 0; i < nodes; i++)
										{
											ServerType server = clusterList.get(clusterIndex).getSrvList().get(serverNumber[i]);
											if(server.getNativeOperatingSystem().getNode().get(0).getJobRef() != null){
												server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(temp_job);
											}
											else {
												IDREFS jobRef = new IDREFS();						
												server.getNativeOperatingSystem().getNode().get(0).setJobRef(jobRef);
												server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(temp_job);
											}
									
											server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("RUNNING"));
											//Set new value for used cores
											NrOfCoresType usedCores = new NrOfCoresType ();
											int usedCoresValue = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue() + temp_job.getNeededCoresPerNode().getValue();
											usedCores.setValue(usedCoresValue);
											
											server.getNativeOperatingSystem().getNode().get(0).setCoresInUse(usedCores);
											double tempMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
											server.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(tempMemory - temp_job.getNeededMemory().getValue());
											//serverCoresInUseList.set(serverNumber[i], (serverCoresInUseList.get(serverNumber[i]) + temp_job.getNeededCoresPerNode().getValue()));
											//serverMemoryList.set(serverNumber[i], (serverMemoryList.get(serverNumber[i]) - temp_job.getNeededMemory().getValue()));
											
											log.debug("Startup server: " + serverNumber[i]);
											createActionRequest("START_JOB", server.getFrameworkID(), temp_job.getId());
		
		
										}
	
										jobList.remove(index); // remove the job from the temporary queue
										
										// Include the job into the running jobs list
										temp_job.setStatus(JobStatusType.valueOf("RUNNING"));
										enqueue(temp_job);
																												
										// Decrement the index, to continue to the next element in the queue
										index--;
										
										break; // Break from the clusterList loop, since the job can be started

									}
									else
									{
										log.debug("The job cannot be executed before the 1st element.");
									}									
								}
							}
						}
						else
						{
							log.debug("Not enough licenses available.");
						}
						// increment the index
						index++;
						
						// Continue backfill first fit until the list becomes empty, 
						// end of the queue is reached, or there are no resources available
					} while(!jobList.isEmpty() && index != jobList.size() && isResourcesAvailable() == true);
				}
				log.debug("Backfill first fit finished.");				
			}
			
		}
		// Backfill Best Fit algorithm
		else if (scheduling == 2)
		{
			if(jobList.isEmpty())
			{
				log.debug("Queue empty.");
			}
			else
			{			
				// Find resources for the 1st job in the queue
				findResources();
				
				// Continue back fill best fit if the list is not empty 
				// and there is more than one element in the queue
				if(!jobList.isEmpty() && jobList.size() > 1)
				{
					log.debug("Executing backfill best fit...");
					
					// Go  through the queue:
					
					// 1. Check if the job can be executed with the free resources
					// - what resources are needed
					// - can  the job be executed before the 1st job in the queue
					
					// 2. Check what resources are left free after the job is allocated
					// - how many nodes, cores, memory are left free
					
					// 3. Go through the queue and compare "spare parts"
					
					// 4. Select the job that uses most of the free resources
					
					// 5. repeat parts 1-4 until no suitable job is found or the queue runs empty.
					
					boolean stopSearch;
					boolean jobFound;
					int index;
					int freeNodes;
					double freeMemory;
					int freeCores;
					
					do
					{						
						jobFound = false;
						index = 1;
						
						// Add info about the optimal job
						OptimalJob OptJob = new OptimalJob();
						
						do
						{
							freeNodes = freeCores = 0;
							freeMemory = 0;
														
							JobType temp_job = jobList.get(index); // Pop a job from queue
							boolean resourcesFound = true;
							int nodes;
							nodes = temp_job.getNumberOfNodes().getValue();
							int[] serverNumber = new int[nodes];
							
							//TODO: license check + update of the licenses to the Cluster object
							boolean licenseAvailable = true;
							boolean licenseRequired = false;
							//Check if the job requires a license
							try
							{
								String requestedSoftware = temp_job.getRequestedSoftware();
								//log.debug("Requested software is: " +requestedSoftware);
								if(!(requestedSoftware.isEmpty()))
								{
									licenseAvailable = isLicensesAvailable(temp_job);
									if(licenseAvailable == true)
									{
										licenseRequired = true;
									}
								}
								
							}
							catch(NullPointerException e)
							{
								// no license required
								//log.debug("no license required.");
								licenseAvailable = true;
							}
							
							if(licenseAvailable == true)
							{
								
								for(int clusterIndex = 0; clusterIndex < clusterList.size(); clusterIndex++)
								{
									log.debug("Cluster: " +clusterList.get(clusterIndex).getName());
							
									// Search for appropriate servers
									searchServers(temp_job, nodes, temp_job.getNeededMemory().getValue(), temp_job.getNeededCoresPerNode().getValue(), serverNumber, clusterList.get(clusterIndex).getSrvList());
									
									// Check if the servers were found
									resourcesFound = checkServerNumber(serverNumber, resourcesFound, nodes, clusterList.get(clusterIndex).getSrvList());
									
									if (resourcesFound == false)
									{
										// No servers found, continue the search
										log.debug("No servers were found for the job index: " +index);
									}
									else
									{
										log.debug("Servers were found for the job index: " +index);
										
										// Get the estimated completion time of the new job
										long compTime = getEpochTime() + temp_job.getWallTime().getValue();
												
										// Get the estimated start time of the 1st job in the queue
										long startTime;
										startTime = getEstimatedStartTime(clusterList.get(clusterIndex).getSrvList());
										log.debug("Estimated start time of the 1st job: " + startTime);
										log.debug("Completion time of the new job: " + compTime);
										
										// Check if the job can be executed
										if (startTime > compTime)
										{
											log.debug("The job can be executed before the 1st element in the queue.");
											jobFound = true;
																												
											// Calculate what resources are left free if the job is executed
											freeNodes = getFreeNodes(nodes, temp_job.getNeededCoresPerNode().getValue(), clusterList.get(clusterIndex).getSrvList());
											freeMemory = getFreeMemory(temp_job.getNeededMemory().getValue(), nodes, clusterList.get(clusterIndex).getSrvList());
											freeCores = getFreeCores(temp_job.getNeededCoresPerNode().getValue(), nodes, clusterList.get(clusterIndex).getSrvList());
											
											// Set this job as the optimal job if it has less free resources than the optimal job
											if(freeNodes < OptJob.getFreeNodes() || OptJob.getFreeNodes() == -1)
											{
												log.debug("The job is selected as the optimal job. Nodes.");
												if(licenseRequired == true)
												{
													OptJob.setLicenseRequired(true);
													OptJob.setRequestedSoftware(temp_job.getRequestedSoftware());
												}
												OptJob.setJobIndex(index);
												OptJob.setFreeNodes(freeNodes);
												OptJob.setFreeMemory(freeMemory);
												OptJob.setFreeCores(freeCores);
												OptJob.setClusterIndex(clusterIndex);
												addOptimalJob(OptJob, nodes, temp_job.getNeededMemory().getValue(), temp_job.getNeededCoresPerNode().getValue(), temp_job.getWallTime().getValue(), serverNumber);
											}
											else if(freeNodes == OptJob.getFreeNodes())
											{
												if(freeCores < OptJob.getFreeCores() || OptJob.getFreeCores() == -1)
												{
													log.debug("The job is selected as the optimal job. Cores.");
													if(licenseRequired == true)
													{
														OptJob.setLicenseRequired(true);
														OptJob.setRequestedSoftware(temp_job.getRequestedSoftware());
													}
													OptJob.setJobIndex(index);
													OptJob.setFreeNodes(freeNodes);
													OptJob.setFreeMemory(freeMemory);
													OptJob.setFreeCores(freeCores);
													OptJob.setClusterIndex(clusterIndex);
													// Add optimal job
													addOptimalJob(OptJob, nodes, temp_job.getNeededMemory().getValue(), temp_job.getNeededCoresPerNode().getValue(), temp_job.getWallTime().getValue(), serverNumber);
												}
												else if(freeCores == OptJob.getFreeCores() )
												{
													if(freeMemory < OptJob.getFreeMemory() || OptJob.getFreeMemory() == -1)
													{
														log.debug("The job is selected as the optimal job. Memory.");
														if(licenseRequired == true)
														{
															OptJob.setLicenseRequired(true);
															OptJob.setRequestedSoftware(temp_job.getRequestedSoftware());
														}
														OptJob.setJobIndex(index);
														OptJob.setFreeNodes(freeNodes);
														OptJob.setFreeMemory(freeMemory);
														OptJob.setFreeCores(freeCores);
														OptJob.setClusterIndex(clusterIndex);
														// Add optimal job
														addOptimalJob(OptJob, nodes, temp_job.getNeededMemory().getValue(), temp_job.getNeededCoresPerNode().getValue(), temp_job.getWallTime().getValue(), serverNumber);
													}
												}
											}
										}							
										// Else: the job cannot be executed
										else
										{
											log.debug("The job cannot be executed before the 1st element.");
										}
										
									}
								}
							}
							else
							{
								log.debug("Not enough licenses available.");
							}

							// Increment the index
							index++;
							
							// Continue until the queue becomes empty or end of the queue is reached
						} while(!jobList.isEmpty() && index != jobList.size());
						
						// If a job was found, add it into running jobs list, and generate needed actions
						if(jobFound == true)
						{
							stopSearch = false;
																					
							// Include the optimal job into the running jobs list
							JobType newJob = new JobType();
							newJob.setStatus(JobStatusType.valueOf("RUNNING"));
							
							JobTimeType newStart = new JobTimeType ();
							newStart.setValue(getEpochTime());
							newJob.setTimeOfStart(newStart);
							
							JobPriorityType newPriority = new JobPriorityType ();
							newPriority.setValue(0);							
							newJob.setPriority(newPriority);
														
							NrOfCoresType newCores = new NrOfCoresType ();
							newCores.setValue(OptJob.getCores());
							newJob.setNeededCoresPerNode(newCores);
							
							MemoryUsageType newMemory = new MemoryUsageType ();
							newMemory.setValue(OptJob.getMemory());
							newJob.setNeededMemory(newMemory);
							
							NrOfNodesType newNodes = new NrOfNodesType ();
							newNodes.setValue(OptJob.getNodes());
							newJob.setNumberOfNodes(newNodes);
							
							JobTimeType newWall = new JobTimeType ();
							newWall.setValue(OptJob.getWallTime());
							newJob.setWallTime(newWall);
							
							newJob.setRequestedSoftware(OptJob.getRequestedSoftware());
							
							newJob.setId(jobList.get(OptJob.getJobIndex()).getId());
							enqueue(newJob);
							
							// Decrease the licenses
							if(OptJob.getLicenseRequired() == true)
								decreaseLicenses(newJob);
							
							//boolean generateStartJob = true;
							// Go through the selected servers and check if there are servers
							// that are not powered ON. If all the servers are not ON, just generate
							// a POWER_ON action to the OFF servers. Start Job action should be generated
							// when all servers are available.
							
//							for(int i = 0; i < OptJob.getNodes(); i++)
//							{
//								ServerType server = srvList.get(OptJob.getServerNumber(i));
//								if (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("OFF") || server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY"))
//								{
//									generateStartJob = false;
//								}
//							}
							
							// Generate actions that the server needs to do
							for(int i = 0; i < OptJob.getNodes(); i++)
							{
								ServerType server = clusterList.get(OptJob.getClusterIndex()).getSrvList().get(OptJob.getServerNumber(i));
								if(server.getNativeOperatingSystem().getNode().get(0).getJobRef() != null){
									server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(newJob);
								}
								else {
									IDREFS jobRef = new IDREFS();						
									server.getNativeOperatingSystem().getNode().get(0).setJobRef(jobRef);
									server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(newJob);
								}

								server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("RUNNING"));
								//Set new value for used cores
								NrOfCoresType usedCores = new NrOfCoresType ();
								int usedCoresValue = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue() + newJob.getNeededCoresPerNode().getValue();
								usedCores.setValue(usedCoresValue);
								
								server.getNativeOperatingSystem().getNode().get(0).setCoresInUse(usedCores);
								double tempMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
								server.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(tempMemory - newJob.getNeededMemory().getValue());														
								//serverCoresInUseList.set(OptJob.getServerNumber(i), (serverCoresInUseList.get(OptJob.getServerNumber(i)) + newJob.getNeededCoresPerNode().getValue()));
								//serverMemoryList.set(OptJob.getServerNumber(i), (serverMemoryList.get(OptJob.getServerNumber(i)) - newJob.getNeededMemory().getValue()));
								log.debug("Startup server: " + OptJob.getServerNumber(i));
								createActionRequest("START_JOB", server.getFrameworkID(), newJob.getId());

							}

							// Generate actions that the server needs to do
//							for(int i = 0; i < OptJob.getNodes(); i++)
//							{
//								ServerType server = srvList.get(OptJob.getServerNumber(i));
//								if(server.getNativeOperatingSystem().getNode().get(0).getJobRef() != null){
//									server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(newJob);
//								}
//								else {
//									IDREFS jobRef = new IDREFS();						
//									server.getNativeOperatingSystem().getNode().get(0).setJobRef(jobRef);
//									server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(newJob);
//								}
//								//server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(newJob);
//								if(server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE") || server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("RUNNING"))
//								{
//									server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("RUNNING"));
//									serverCoresInUseList.set(OptJob.getServerNumber(i), (serverCoresInUseList.get(OptJob.getServerNumber(i)) + newJob.getNeededCoresPerNode().getValue()));
//									serverMemoryList.set(OptJob.getServerNumber(i), (serverMemoryList.get(OptJob.getServerNumber(i)) - newJob.getNeededMemory().getValue()));
//									if(generateStartJob == true)
//									{
//										log.debug("Startup server: " + OptJob.getServerNumber(i));
//										createActionRequest("START_JOB", OptJob.getServerNumber(i), newJob.getId());
//										
//									}
//								}
//								else if (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("OFF") || server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY") || server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED"))
//								{						
//									log.debug("POWER_ON server: " + OptJob.getServerNumber(i));
//									//reservedList.set(OptJob.getServerNumber(i), true);
//									serverCoresInUseList.set(OptJob.getServerNumber(i), (serverCoresInUseList.get(OptJob.getServerNumber(i)) + newJob.getNeededCoresPerNode().getValue()));
//									serverMemoryList.set(OptJob.getServerNumber(i), (serverMemoryList.get(OptJob.getServerNumber(i)) - newJob.getNeededMemory().getValue()));
//									//server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("RESERVED"));
//									createActionRequest("POWER_ON", OptJob.getServerNumber(i), "");
//								}	
//							}
							jobList.remove(OptJob.getJobIndex()); // remove the optimal job from the temporary queue						
						}
						// Else: No job was found, stop the search for jobs
						else
						{
							stopSearch = true;
						}
						
						// Continue until no job is found and there are no resources available
					} while(stopSearch == false && isResourcesAvailable() == true  && jobList.size() > 1);
					
					log.debug("Backfill best fit finished.");				
				}
			}			
		}		
	}
	
	/**
	 * Method for checking if the 1st element in the queue 
	 * can be executed with the current nodes
	 * 
	 */
	
	public void findResources()
	{
		// Extract jobs from the queue until the queue becomes empty
		// or no suitable servers are found for the 1st job in the queue
		boolean resourcesFound;
		do 
		{
			log.debug("Popping a job from queue.");
			JobType temp_job = jobList.get(0);
			resourcesFound = true;
			int nodes;
			nodes = temp_job.getNumberOfNodes().getValue();
			int[] serverNumber = new int[nodes];
			
			//TODO: license check + update of the licenses to the Cluster object
			boolean licenseAvailable = true;
			boolean licenseRequired = false;
			
			//Check if the job requires a license
			try
			{
				String requestedSoftware = temp_job.getRequestedSoftware();
				//log.debug("Requested software is: " +requestedSoftware);
				if(!(requestedSoftware.isEmpty()))
				{
					licenseAvailable = isLicensesAvailable(temp_job);
					if(licenseAvailable == true)
					{
						licenseRequired = true;
					}
				}
				
			}
			catch(NullPointerException e)
			{
		
				// no license required
				//log.debug("no license required.");
				licenseAvailable = true;
			}
			
			if(licenseAvailable == true)
			{
									
				// Go through each cluster and check if resources are found
				for(int clusterIndex = 0; clusterIndex < clusterList.size(); clusterIndex++)
				{
																	
					// Search for appropriate servers
					searchServers(temp_job, nodes, temp_job.getNeededMemory().getValue(), temp_job.getNeededCoresPerNode().getValue(), serverNumber, clusterList.get(clusterIndex).getSrvList());
					// Check if the servers were found
					resourcesFound = checkServerNumber(serverNumber, resourcesFound, nodes, clusterList.get(clusterIndex).getSrvList());				
					
					if(resourcesFound == false)
					{
						log.debug("Suitable servers were NOT found within cluster: "+clusterList.get(clusterIndex).getName());
					}
					else
					{
						log.debug("Suitable servers were found within cluster: "+clusterList.get(clusterIndex).getName());
						
						if(licenseRequired == true)
							decreaseLicenses(temp_job);
														
						temp_job.getTimeOfStart().setValue(getEpochTime());				
						// Generate actions that the server needs to do with F4G scheduler
						if (defaultscheduling == false)
						{
								for(int i = 0; i < nodes; i++)
								{
									//ServerType server = srvList.get(serverNumber[i]);
									ServerType server = clusterList.get(clusterIndex).getSrvList().get(serverNumber[i]);
									if(server.getNativeOperatingSystem().getNode().get(0).getJobRef() != null){
										server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(temp_job);
									}
									else {
										IDREFS jobRef = new IDREFS();						
										server.getNativeOperatingSystem().getNode().get(0).setJobRef(jobRef);
										server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(temp_job);
									}
									
									server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("RUNNING"));
									//Set new value for used cores
									NrOfCoresType usedCores = new NrOfCoresType ();
									int usedCoresValue = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue() + temp_job.getNeededCoresPerNode().getValue();
									usedCores.setValue(usedCoresValue);
									
									server.getNativeOperatingSystem().getNode().get(0).setCoresInUse(usedCores);
									double tempMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
									server.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(tempMemory - temp_job.getNeededMemory().getValue());
									//serverCoresInUseList.set(serverNumber[i], (serverCoresInUseList.get(serverNumber[i]) + temp_job.getNeededCoresPerNode().getValue()));
									//serverMemoryList.set(serverNumber[i], (serverMemoryList.get(serverNumber[i]) - temp_job.getNeededMemory().getValue()));
				
									log.debug("Startup server: " + serverNumber[i]);
									createActionRequest("START_JOB", server.getFrameworkID(), temp_job.getId());
								}
							}
							// Actions with default scheduler
							else
							{
								for(int i = 0; i < nodes; i++)
								{
																										
									ServerType server = srvList.get(serverNumber[i]);
									if (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("DOWN") || server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY") || server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED"))
									{
										log.debug("wake up server: " + serverNumber[i]);
										createActionRequest("POWER_ON", server.getFrameworkID(), "");
									}
									
									if(server.getNativeOperatingSystem().getNode().get(0).getJobRef() != null){
										server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(temp_job);
									}
									else {
										IDREFS jobRef = new IDREFS();						
										server.getNativeOperatingSystem().getNode().get(0).setJobRef(jobRef);
										server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(temp_job);
									}
									server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("RUNNING"));
									
									//Set new value for used cores
									NrOfCoresType usedCores = new NrOfCoresType ();
									int usedCoresValue = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue() + temp_job.getNeededCoresPerNode().getValue();
									usedCores.setValue(usedCoresValue);
									
									server.getNativeOperatingSystem().getNode().get(0).setCoresInUse(usedCores);
									double tempMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
									server.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(tempMemory - temp_job.getNeededMemory().getValue());
									
									//serverCoresInUseList.set(serverNumber[i], (serverCoresInUseList.get(serverNumber[i]) + temp_job.getNeededCoresPerNode().getValue()));
									//serverMemoryList.set(serverNumber[i], (serverMemoryList.get(serverNumber[i]) - temp_job.getNeededMemory().getValue()));
															
								}
							}
														
							// Include the job into the running jobs list
							temp_job.setStatus(JobStatusType.valueOf("RUNNING"));
							enqueue(temp_job);
							
							jobList.remove(0); // remove the job from the temporary queue
							break; // break from the loop once resources were found for the job
	
					}	
				}
			}
			//Else: no licenses available
			else
			{
				log.debug("Not enough licenses available.");
				resourcesFound = false; // set to false to break from the loop
			}
			
		} while (jobList.isEmpty() == false && resourcesFound == true);
	}
	
	/**
	 * Method for adding info about a optimal job
	 * 
	 */
	public void addOptimalJob(OptimalJob OptJob, int nodes, double memory, int cores, long wallTime, int [] serverNumber)
	{		
		OptJob.clearServerNumber();		
		OptJob.setNodes(nodes);
		OptJob.setMemory(memory);
		OptJob.setCores(cores);
		OptJob.setWallTime(wallTime);
		
		for(int i = 0; i < nodes; i++)
		{
			OptJob.addServerNumber(serverNumber[i]);
		}		
	}
	
	/**
	 * Creates a set of action requests, to be forwarded to the Controller.
	 * 
	 * @param actionType, serverIndex
	 * @return none
	 */
	public void createActionRequest(String actionType, String serverName, String jobID){
		
		// Get the name corresponding to the serverIndex
		//ServerType server = srvList.get(serverIndex);
		//String serverName = server.getFrameworkID();
						
		//String myQuery = "//rackableServer";	
		//JXPathContext context = JXPathContext.newContext(model);
		//Iterator elements = context.iterate(myQuery);
		
		for(SiteType site : model.getSite())
		{		
			for(DatacenterType datacenter : site.getDatacenter())
			{
				//log.debug("Framework name: " + datacenter.getFrameworkCapabilities().get(0).getFrameworkName());
				//log.debug("serverName: " + serverName);
				
				//Andre: Iterating over all ServerType
				//read all potential tempServers
				List <ServerType> tempServers = new ArrayList<ServerType>();
				
				// Iteration over the Racks
            	Iterator<RackType> rackIter = datacenter.getRack().iterator();
				while(rackIter.hasNext())
				{
					RackType rackType = rackIter.next();
					
					// Iteration over the "Enclosure" objects
					Iterator<EnclosureType> enclosureIter = rackType.getEnclosure().iterator();
					while(enclosureIter.hasNext())
					{
						EnclosureType enclosureType = enclosureIter.next();						
						tempServers.addAll(enclosureType.getBladeServer());
					}
					
					tempServers.addAll(rackType.getRackableServer());
				}
				//End Andre: Iterating over all ServerType
    		
				for(ServerType tempServer : tempServers)
				//for(ServerType tempServer : datacenter.getRack().get(0).getRackableServer())
				{
					//log.debug("server ID: " + tempServer.getFrameworkID());
					if(tempServer.getFrameworkID().matches(serverName))
		        	{ 
						log.debug("Adding an action to server ID: " + tempServer.getFrameworkID());
						if(actionType.equals("POWER_ON"))
		        		{       		
			        		log.debug("Adding power on action");
			        		
			    			JAXBElement<PowerOnActionType>  pOn = (new ObjectFactory()).createPowerOn(new PowerOnActionType());
			    			pOn.getValue().setNodeName(tempServer.getFrameworkID());
			    			
			    			//DatacenterType datacenter = Utils.getFirstDatacenter(model);
			    			//String frameworkName = datacenter.getFrameworkCapabilities().get(0).getFrameworkName();	
			    			FrameworkCapabilitiesType framework = (FrameworkCapabilitiesType) tempServer.getFrameworkRef();   			
			    			String frameworkName = framework.getFrameworkName();
			    			
			    			pOn.getValue().setFrameworkName(frameworkName);  			
			    			actionList.getAction().add(pOn);
	
			    			
		        		}
		        		else if(actionType.equals("STANDBY"))
		        		{
		        			log.debug("Adding a standby action");
		        				        			
		        			JAXBElement<StandByActionType>  stdBy = (new ObjectFactory()).createStandBy(new StandByActionType());
		        			stdBy.getValue().setNodeName(tempServer.getFrameworkID());
			    			
			    			FrameworkCapabilitiesType framework = (FrameworkCapabilitiesType) tempServer.getFrameworkRef();   			
			    			String frameworkName = framework.getFrameworkName();
			    			
			    			//log.debug("Server Framework ID: " +tempServer.getFrameworkID());
			    			//log.debug("frameworkName: " +frameworkName);
			    			
			    			stdBy.getValue().setFrameworkName(frameworkName);			
			    			actionList.getAction().add(stdBy);
		        		} 
		        		else if(actionType.equals("POWER_OFF"))
		        		{
		        			log.debug("Adding a power off action");
		        			
		        			JAXBElement<PowerOffActionType>  pOff = (new ObjectFactory()).createPowerOff(new PowerOffActionType());
			    			pOff.getValue().setNodeName(tempServer.getFrameworkID());
			    			
			    			FrameworkCapabilitiesType framework = (FrameworkCapabilitiesType) tempServer.getFrameworkRef();   			
			    			String frameworkName = framework.getFrameworkName();
			    			
			    			pOff.getValue().setFrameworkName(frameworkName);			
			    			actionList.getAction().add(pOff);
		        		}        		
		        		else if(actionType.equals("START_JOB"))
		        		{
		        				        			
		        			// If there is already an action containing this jobId
		        			if(jobIdList.contains(jobID))
		        			{
		        				log.debug("JobIdList has the jobId." + actionRequest.getActionList().getAction().size());
			        			for(int i = 0; i < actionRequest.getActionList().getAction().size(); i++)
			        			{
			        				if(actionRequest.getActionList().getAction().get(i).getDeclaredType().getSimpleName().toString().matches("StartJobActionType"))
			        				{
			        					Object obj = actionRequest.getActionList().getAction().get(i).getValue();
			        					String actionJobId = ((StartJobActionType)obj).getJobID();	    
			        					if(jobID.matches(actionJobId))
			        					{
			        						// Add the server to the Start Job action
			        						((StartJobActionType)obj).getNodeName().add(tempServer.getFrameworkID());
			        					}
			        				}
			        			}
		        			}
		        			// Else: create a new start job action
		        			else
		        			{
		        				log.debug("Adding jobId to jobIdList.");
		        				jobIdList.add(jobID);
		        				JAXBElement<StartJobActionType>  startJob = (new ObjectFactory()).createStartJob(new StartJobActionType());
		        				startJob.getValue().getNodeName().add(tempServer.getFrameworkID());
		        				startJob.getValue().setJobID(jobID);
		        				
		    	    			//DatacenterType datacenter = Utils.getFirstDatacenter(model);
		    	    			//String frameworkName = datacenter.getFrameworkCapabilities().get(0).getFrameworkName();
		    	    			FrameworkCapabilitiesType framework = (FrameworkCapabilitiesType) tempServer.getFrameworkRef();   			
		    	    			String frameworkName = framework.getFrameworkName();	
		    	    			startJob.getValue().setFrameworkName(frameworkName);			
		    	    			actionList.getAction().add(startJob);
		        			}      			     			      			
		        		}   
		        	}
				}
				
			}
		}
		
		// Add the actionList to the actionRequest
		actionRequest.setActionList(actionList);
		
	}
		
	/**
	 * Method for checking the queue 
	 * if there are any idle servers
	 * and can they be shut down
	 *
	 */	
	
	public void checkIdleServers(DatacenterType datacenter, List<ServerType> tempSrvList)
	{		
		boolean shutDown;
		long startTime = 0;
		boolean idleServers  = false;
		int numberOfIdle = 0;
		int numberOfReserved = 0;
		int powerOffNodes = 0;
		int largestJobNodes = 0;
		int poweredOnNodes = 0;

		if(defaultscheduling == true)
			largestJobNodes = getLargestJobNodes();
		poweredOnNodes = getPoweredOnNodes ();
		
		if(poweroff == true)
		{
		
			log.debug("Checking for idle servers..");
			
			// Go through the serverList and check if there are any idle servers
			for(int i = 0; i < tempSrvList.size(); i++)
			{
				if(tempSrvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE"))
				{
					idleServers = true;
					numberOfIdle++;
						//break;
				}
//				else if(tempSrvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("RESERVED"))
//				{
//					idleServers = true;
//					numberOfReserved++;				
//				}
			}
			
			//Check how many servers can be powered off			
			log.debug("numberOfIdle: "+numberOfIdle);
			//log.debug("numberOfReserved: "+numberOfReserved);
			
			if(defaultscheduling == true)
			{
				if(shutDownReserved == true)
					powerOffNodes = (numberOfIdle + numberOfReserved) - bufferedIdleNodeSize;
				else
					powerOffNodes = numberOfIdle - bufferedIdleNodeSize;
			}
			else
			{
				powerOffNodes = numberOfIdle - bufferedIdleNodeSize;
			}
			
			log.debug("powerOffNodes: "+powerOffNodes);
			
			// Make sure no servers are shut down which are needed by the largest job
			if(defaultscheduling == true)
			{
				if(largestJobNodes > poweredOnNodes)
				{
					powerOffNodes = 0;
				}
				else
				{
					if(largestJobNodes > (poweredOnNodes-powerOffNodes))
					{
						powerOffNodes = 0;
					}
				}
				log.debug("powerOffNodes after: "+powerOffNodes); 
			}
												
			if(idleServers == true && powerOffNodes > 0)
			{				
				if(jobList.isEmpty())
				{
					shutDown = true;
				}
				else
				{
					// Get the estimated start time of the 1st job in the queue
					startTime = getEstimatedStartTime(tempSrvList) - getEpochTime();	// seconds between current time and estimated start time
					
					if(startTime < 0)
						startTime = 0;
					
					log.debug("start Time : "+ startTime);
					log.debug("start date: " +getEpochDate((startTime+getEpochTime())));
					log.debug("threshold : "+ threshold);
					
					// Generate a stdby/suspend action if the startTime is larger than threshold
					if((startTime) < threshold)
					{
						shutDown = false;
					}
					else
					{
						shutDown =  true;
					}
					
				}
				
				//If default scheduling is used
				if(defaultscheduling == true)
				{
					// Go through the idle nodes of the cluster that have NOT been reserved for a job
					for (int i = 0; i < tempSrvList.size(); i++)
					{
						ServerType server = tempSrvList.get(i);
						if(server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE"))
						{				
							//if(shutDown == true && powerOffNodes != 0)
							// Estimated start time not needed, since the node is not reserved
							if(powerOffNodes != 0)
							{
								powerOffNodes--;
								createActionRequest("STANDBY", server.getFrameworkID(), "0");
								server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("STANDBY"));
								log.debug("Setting server to standby: " + i);
							}					
						}
					}
					// Go through the idle nodes of the cluster that have a reservation
					if(powerOffNodes != 0 && shutDown == true && shutDownReserved == true)
					{
						for (int i = 0; i < srvList.size(); i++)
						{
							ServerType server = srvList.get(i);
							if(server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("RESERVED"))
							{				
								if(shutDown == true && powerOffNodes != 0)
								{
									powerOffNodes--;
									createActionRequest("STANDBY", server.getFrameworkID(), "0");
									server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("STANDBY"));
									log.debug("Setting server to standby: " + i);
								}					
							}
						}
					}
				}
				//Else: F4G is doing the scheduling
				else
				{
					// Go through the idle nodes of the cluster
					for (int i = 0; i < tempSrvList.size(); i++)
					{
						ServerType server = tempSrvList.get(i);
						if(server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE"))
						{				
							if(shutDown == true && powerOffNodes != 0)
							{
								powerOffNodes--;
								createActionRequest("STANDBY", server.getFrameworkID(), "0");
								server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("STANDBY"));
								log.debug("Setting server to standby: " + i);
							}					
						}
					}					
				}
			}
		}
	}
	
	/**
	 * Method for checking the queue 
	 * if there are available resources
	 * 
	 */	
	
	public boolean isResourcesAvailable()
	{		
		boolean available = false;
//		for (int i = 0; i < srvList.size(); i++)
//		{
//			ServerType server = srvList.get(i);
//			if((server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("DOWN")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED")))
//			{				
//				available = true;
//				break;
//			}
//			else if(server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue() < serverCoresList.get(i))
//			{
//				available = true;
//				break;
//			}
//		}
		for(int i = 0; i < clusterList.size(); i++)
		{
			for(int j = 0; j < clusterList.get(i).getSrvList().size(); j++)
			{
				ServerType server = clusterList.get(i).getSrvList().get(j);
				if((server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("DOWN")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED")))
				{				
					available = true;
					break;
				}
			}
		}
		return available;
	}
	
	public int getFreeNodes(int nodes, int cores, List<ServerType> tempSrvList)
	{		
		int freeNodes = 0;
		int coresAvailable = 0;
		int coresInUse = 0;
		int serverCores = 0;

		for (int i = 0; i < tempSrvList.size(); i++)
		{
			ServerType server = tempSrvList.get(i);
			if(multipleJobsPerNode == false)
			{
				if((server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("OFF")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED")))
				{
					freeNodes++;
	
				}
				
			}
			else
			{
				coresInUse = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue();
				serverCores = Utils.getNbCores(server);
				// If the server has free cores it is considered free
				if(coresInUse < serverCores)
				{
					coresAvailable = (serverCores - coresInUse);
					// If the number of free cores is above zero, then a node is considered free
					// if this job would be allocated
					if((coresAvailable - cores) > 0)
						freeNodes++;
				}
				
			}
		}
		freeNodes = freeNodes - nodes;
		log.debug("Amount of Free nodes after job: " +freeNodes);
		return freeNodes;
	}
	
	public int getFreeCores(int cores, int nodes, List<ServerType> tempSrvList)
	{
		int freeCores = 0;
		int serverCores = 0;
		// Free cores: NumberOfCores - CoresInUse
		for (int i = 0; i < tempSrvList.size(); i++)
		{
			ServerType server = tempSrvList.get(i);
			serverCores = Utils.getNbCores(server);
			
			if(multipleJobsPerNode == false)
			{
				if((server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("OFF")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED")))
				{
					freeCores = freeCores + serverCores; ;
	
				}
				
			}
			else
			{
				int coresInUse = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue();
				freeCores = freeCores + (Utils.getNbCores(server) - coresInUse);
			}
			
						
		}
//		log.debug("Amount of Free cores before job: " +freeCores);
//		log.debug("Amount of needed cores for job: " +cores);
//		log.debug("Amount of needed nodes for job: " +nodes);
		freeCores = freeCores - (cores * nodes);
		log.debug("Amount of Free cores after job: " +freeCores);
		return freeCores;			
	}
	
	public double getFreeMemory(double memory, int nodes, List<ServerType> tempSrvList)
	{
		double freeMemory = 0;
		double tempMemory = 0;
		int serverCores = 0;
		int coresInUse = 0;
		// Check what resources are currently free
		for (int i = 0; i < tempSrvList.size(); i++)
		{
			ServerType server = tempSrvList.get(i);
			serverCores = Utils.getNbCores(server);
			coresInUse = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue();
			
			
			if(multipleJobsPerNode == false)
			{
				if((server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("OFF")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY")) || (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED")))
				{
					//tempMemory = getServerMemory(server);
					tempMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
					freeMemory = freeMemory + tempMemory;
	
				}
				
			}
			else
			{
				if(coresInUse < serverCores)
				{
					tempMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
					freeMemory = freeMemory + tempMemory;
				}
				
			}
			
		}
		//log.debug("Amount of freeMemory before job: " +freeMemory);
		freeMemory = freeMemory - (memory * nodes);
		log.debug("Amount of freeMemory after job: " +freeMemory);
		return freeMemory;				
	}
		
	
	/**
	 * Gets the estimated start time of 
	 * the first job in the queue
	 * @param none
	 * @return estimated start time of the 1st job in queue
	 * 
	 */	
	
	public long getEstimatedStartTime(List<ServerType> tempSrvList)
	{	
		JobType job = jobList.get(0); // Get the 1st job in the queue
		int numNodes = job.getNumberOfNodes().getValue();
		int numCores = job.getNeededCoresPerNode().getValue();
		int availableNodes = 0;
		int availableCores = 0;
		long startTime = 0;				
		Vector<Long> compTime = new Vector<Long> ();
		Vector<Long> serverCompTime = new Vector<Long> ();
		long serverStartTime = 0;
		List<JobType> tempJobList = new LinkedList<JobType>(); // List of jobs in the queue
		boolean licenseRequired = false;
		String licenseName = "";
		//String siteName = job.getSiteName();
		Long licensefinishTime = (long) 0;
		//TODO: estimated start time for job requesting a license?
		
		// Check what license the job needs
		//Check if the job requires a license
				
		try
		{			
			licenseName = job.getRequestedSoftware();
			//log.debug("1st Job requires software: " +licenseName);
			
			if(!(licenseName.isEmpty()))
			{
				licenseRequired = true;
			}				
		}
		catch(NullPointerException e)
		{
			// no license required
			//log.debug("no license required for 1st job.");
			licenseRequired = false;
		}
						
		//1. check for idle servers
		for(int i = 0; i < tempSrvList.size(); i++)
		{
			if(tempSrvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE"))
			{
				compTime.add((long) 0); // since the server is idle, it is immediately available
				availableNodes++;
				if(availableNodes == numNodes)
					break;
			}		
		}
		//2. check for servers in standby mode
		//standby/hybernated servers could have a jobRef associated
		if(availableNodes < numNodes)
		{
			for(int i = 0; i < tempSrvList.size(); i++)
			{
				if(tempSrvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY") && tempSrvList.get(i).getNativeOperatingSystem().getNode().get(0).isReserved() == false)
				{
					compTime.add((long) 10); // since the server is in standby, it will be available in 5s
					availableNodes++;
					if(availableNodes == numNodes)
						break;
				}		
			}
			//3. check servers in hybernation
			if(availableNodes < numNodes)
			{
				for(int i = 0; i < tempSrvList.size(); i++)
				{
					if(tempSrvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED") && tempSrvList.get(i).getNativeOperatingSystem().getNode().get(0).isReserved()  == false)
					{
						compTime.add((long) 90); // since the server is hybernated, it will be available in 90s
						availableNodes++;
						if(availableNodes == numNodes)
							break;
					}		
				}
			}
		}
		
		
		log.debug("Idle/sleeping/hybernated servers: " +availableNodes);
		double time = 0.0;
		for (int i = 0; i < compTime.size(); i++)
		{
			time = time + compTime.get(i);
		}
		log.debug("startUp time for Idle/sleeping/hybernated servers: " +time);
		log.debug("Needed nodes for the first job in queue: " + numNodes);
		
		//4. check running jobs
		if(availableNodes < numNodes)
		{			
			//Go through each running server, and define when it will be ready
			//for the job execution. Finally get the servers that have the smallest
			//comptime and select them
						
			// Multiple jobs per node is possible
			if(multipleJobsPerNode == true)
			{
				
				// Go through the servers that are running a job
				for(int i = 0; i < tempSrvList.size(); i++)
				{				
	
					ServerType server = tempSrvList.get(i);
					NodeStatusType serverStatus = server.getNativeOperatingSystem().getNode().get(0).getStatus();
					serverStartTime = 0;
					log.debug("Server name: " +server.getFrameworkID() + " status: " +serverStatus.toString());
					
					//if(serverStatus.toString().matches("RUNNING") || serverStatus.toString().matches("RESERVED"))
					if(serverStatus.toString().matches("RUNNING"))	
					{
						// Go through the jobs that the server is running
						availableCores = 0;
						//availableCores = serverCoresList.get(i) - serverCoresInUseList.get(i);
						availableCores = Utils.getNbCores(server) - server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue();
						log.debug("Need cores: " +numCores);
						log.debug("availableCores without jobs: " +availableCores);
						
						if(availableCores >= numCores)
						{
							if(serverStatus.toString().matches("RUNNING"))
								serverCompTime.add((long) 0); // since there are enough cores, it is immediately available
							//else if(serverStatus.toString().matches("RESERVED"))
							//	serverCompTime.add((long) 10); 
							
						}
						
						else
						{
																
							// Go through the jobs that the server holds and form a temporary job list
							// which is sorted according to the completion times of the jobs
							Iterator<Object> jobIter = server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
							while(jobIter.hasNext())
							{						
								//Get the jobs to a temporary list
								JobType serverJob =  (JobType) jobIter.next();	
								boolean jobInserted = false;
								double serverJobCompTime = 0;
								
								if(serverStatus.toString().matches("RUNNING"))
									serverJobCompTime = serverJob.getTimeOfStart().getValue() + serverJob.getWallTime().getValue();
								//else if(serverStatus.toString().matches("RESERVED"))
								//	serverJobCompTime = serverJob.getTimeOfStart().getValue() + serverJob.getWallTime().getValue() + 10;
									
								
								// If the list is empty, just place it at the end
								if(tempJobList.isEmpty())
								{
									tempJobList.add(serverJob);
								}	
								else
								{
									// Form the list based on the completion time values,
									// smallest completion times going into the top
									for(int j = 0; j < tempJobList.size(); j++)
									{
										JobType tempJob = tempJobList.get(j);
										double tempCompTime = tempJob.getTimeOfStart().getValue() + tempJob.getWallTime().getValue();
										if(serverJobCompTime < tempCompTime)
										{
											tempJobList.add(j, serverJob);
											jobInserted = true;
											break;
										}				
									}
									if(jobInserted == false)
									{
										tempJobList.add(serverJob);
									}		
								}
										
							}
							
							for(int k = 0; k < tempJobList.size(); k++)
							{
								JobType tempJob = tempJobList.get(k);
								log.debug("tempJob index: " + i + " cores: " + tempJob.getNeededCoresPerNode() + " comptime: " + (tempJob.getTimeOfStart().getValue()+tempJob.getWallTime().getValue()));
	
							}
																			
							// Go through the temporary job list and add the number of cores the job holds
							// to the amount of available cores
							for(int k = 0; k < tempJobList.size(); k++)
							{
								JobType tempJob = tempJobList.get(k);
								availableCores = availableCores + tempJob.getNeededCoresPerNode().getValue();
								serverStartTime = serverStartTime + ((tempJob.getTimeOfStart().getValue()+tempJob.getWallTime().getValue()) - serverStartTime);
								
								// If there are enough cores available the node is considered free
								// at this moment in time
								if(availableCores  >= numCores)
								{
									serverCompTime.add(serverStartTime);
									tempJobList.clear();
									break;
								}
							}						
						}
					}
				}
			}
			//Else: only single job per node is possible
			else
			{
				// Go through the servers that are running a job
				for(int i = 0; i < tempSrvList.size(); i++)
				{				
	
					ServerType server = tempSrvList.get(i);
					NodeStatusType serverStatus = server.getNativeOperatingSystem().getNode().get(0).getStatus();
					serverStartTime = 0;
					log.debug("Server name: " +server.getFrameworkID() + " status: " +serverStatus.toString());
					
					//if(serverStatus.toString().matches("RUNNING") || serverStatus.toString().matches("RESERVED"))
					if(serverStatus.toString().matches("RUNNING"))
					{
						// Go through the jobs that the server holds and form a temporary job list
						// which is sorted according to the completion times of the jobs
						Iterator<Object> jobIter = server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
						while(jobIter.hasNext())
						{
							JobType serverJob = (JobType) jobIter.next();
							// Get the finish time of the single job the server holds
							serverStartTime = serverStartTime + ((serverJob.getTimeOfStart().getValue()+serverJob.getWallTime().getValue()) - serverStartTime);
							// Add the job finish time to the serverCompTime vector
							serverCompTime.add(serverStartTime);
						}
					}
				}
				
			}
			
			//Sort the server comptime list with smallest values first
			// and count the number of available nodes
			// add the values to the comptime vector
								
			Collections.sort(serverCompTime);
			
			for (int i = 0; i < serverCompTime.size(); i++)
			{
				log.debug("serverCompTime index: " + i + " Value: " + serverCompTime.get(i));
				compTime.add(serverCompTime.get(i));
				availableNodes++;
				if(availableNodes == numNodes)
					break;
			}					
			
		}
						
		for (int i = 0; i < compTime.size(); i++)
		{
			log.debug("CompTime index: " + i + " Value: " + compTime.get(i));
			startTime = startTime + (compTime.get(i) - startTime);
		}
		

	    long minimum = -1;
		
		// Check which running job has the license and when it finishes
		if(licenseRequired == true)
		{
			for(int i = 0; i < runningList.size(); i++)
			{
				JobType tempjob = runningList.get(i);
				try
				{
					if(tempjob.getRequestedSoftware().matches(licenseName))
					{
		
						licensefinishTime = tempjob.getTimeOfStart().getValue() + tempjob.getWallTime().getValue();
						//log.debug("License: "+tempjob.getRequestedSoftware() +" finish time: "+licensefinishTime);
						if(licensefinishTime < minimum || minimum == -1)
						{
							minimum = licensefinishTime;							
						}

					}
											
				}
				catch(NullPointerException e)
				{
					// no license
					
				}
			}
			
			log.debug("License finish time: "+minimum);
			
			//Start time is the maximum
			startTime = Math.max(startTime, minimum);
		}
		
		
		//log.debug("Needed nodes for the first job in queue: " + numNodes);
		//log.debug("Estimated start Time: " + startTime);
		
		compTime.clear();
		serverCompTime.clear();
						
		return startTime;
	}
	
	/**
	 * Gets the current system time in seconds (epoch time). 
	 */
	
	public long getEpochTime()
	{
		long epoch = System.currentTimeMillis()/1000;	
		return epoch;
	}
	
	/**
	 * Converts an epoch time to a date format.
	 */
	
	public Date getEpochDate(long epoch)
	{
	    Date epochDate = new Date(epoch*1000);
	    return epochDate;
	}
	
	/**
	 * Checks if actionList is empty.
	 */
	
	public boolean checkActionList()
	{
		boolean empty;
		try
		{
			actionRequest.getActionList().getAction().size();
			log.debug("Size of action list: " +actionRequest.getActionList().getAction().size());
			if(actionRequest.getActionList().getAction().size() == 0)
				empty = true;
			else
				empty = false;
			
		}
		catch (NullPointerException e)
		{
			empty = true;
		}
		return empty;
	}
		
	/**
	 * Utility Method for printing the contents of  the 
	 * queue 
	 * 
	 */
	
	public void printQueue()
	{
		for(int i = 0; i < jobList.size(); i++)
		{
			JobType tempjob = jobList.get(i);
			log.debug("JobList index: "+ i + " priority: "+ tempjob.getPriority().getValue() + " numNodes: " + tempjob.getNumberOfNodes().getValue() + " numCores: " +tempjob.getNeededCoresPerNode().getValue() + " memory " +tempjob.getNeededMemory().getValue() + " wallTime: " +tempjob.getWallTime().getValue() + " compTime: " +(tempjob.getTimeOfStart().getValue()+ tempjob.getWallTime().getValue()) + " jobID: " +tempjob.getId());
		}
		System.out.println("");
		for(int i = 0; i < runningList.size(); i++)
		{
			JobType tempjob = runningList.get(i);
			log.debug("Running Job index: "+ i + " priority: "+ tempjob.getPriority().getValue() + " numNodes: " + tempjob.getNumberOfNodes().getValue() + " numCores: " +tempjob.getNeededCoresPerNode().getValue() + " memory " +tempjob.getNeededMemory().getValue() + " wallTime: " +tempjob.getWallTime().getValue() + " jobID: " +tempjob.getId());			
		}
	}
	
	/**
	 * Utility Method for printing the contents of  the 
	 * ServerData structure
	 * 
	 */
	
	public void printServerList()
	{
//		for(int i = 0; i < srvList.size(); i++)
//		{
//			ServerType tempServer = srvList.get(i);
//			String state = tempServer.getNativeOperatingSystem().getNode().get(0).getStatus().toString();					
//			int coresInUse = serverCoresInUseList.get(i);
//			double compTime = 0;
//			
//			log.debug("Server index: "+ i + " state: "+ state + " serverMemory: "+ serverMemoryList.get(i) + " coresInUse: " +coresInUse);
//			// Iteration over the JobType objects
//			try
//			{
//				Iterator<Object> jobIter = tempServer.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
//				while(jobIter.hasNext())
//				{
//					JobType tempJob =  (JobType) jobIter.next();
//					compTime = tempJob.getTimeOfStart().getValue() + tempJob.getWallTime().getValue();
//					log.debug(" job ID: " +tempJob.getId());
//					log.debug(" completion time: " +compTime );				
//				}
//			}
//			catch(NullPointerException e)
//			{
//				log.debug("No jobs with this server.");	
//				
//			}
//		}
		
		for(int i = 0; i < clusterList.size(); i++)
		{
			log.debug("Cluster: " +clusterList.get(i).getName());
			
			for(int licenseIndex = 0; licenseIndex < clusterList.get(i).getLicenseList().size(); licenseIndex++)
			{
				SoftwareLicenseType license = clusterList.get(i).getLicenseList().get(licenseIndex);
				log.debug("License name: "+license.getName() +" NumberOfAvailable: "+license.getNumberOfAvailable());
			}
			for(int j = 0; j < clusterList.get(i).getSrvList().size(); j++)
			{
				ServerType tempServer = clusterList.get(i).getSrvList().get(j);
				String state = tempServer.getNativeOperatingSystem().getNode().get(0).getStatus().toString();
				int coresInUse = tempServer.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue();
				double memory = tempServer.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
				double compTime = 0;
				log.debug("Server index: "+ j + " state: "+ state + " serverMemory: "+ memory  + " coresInUse: " +coresInUse);
				// Iteration over the JobType objects
				try
				{
					Iterator<Object> jobIter = tempServer.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
					while(jobIter.hasNext())
					{
						JobType tempJob =  (JobType) jobIter.next();
						compTime = tempJob.getTimeOfStart().getValue() + tempJob.getWallTime().getValue();
						log.debug(" job ID: " +tempJob.getId());
						log.debug(" completion time: " +compTime );				
					}
				}
				catch(NullPointerException e)
				{
					//log.debug("No jobs with this server.");	
					
				}
			}
			
		}
		
	}
	
	/**
	 * Utility Method for printing the contents of  the 
	 * actionList 
	 * 
	 */
	
	public void printActionList()
	{
		for(int i = 0; i < actionRequest.getActionList().getAction().size(); i++)
		{
			if(actionRequest.getActionList().getAction().get(i).getDeclaredType().getSimpleName().toString().matches("PowerOffActionType"))
			{
				Object element = actionRequest.getActionList().getAction().get(i).getValue();
				log.debug("ActionList index: " +i +" Type: Power off NodeName: " +((PowerOffActionType)element).getNodeName() +" FrameworkName: " +((PowerOffActionType)element).getFrameworkName());			
			}
			else if(actionRequest.getActionList().getAction().get(i).getDeclaredType().getSimpleName().toString().matches("PowerOnActionType"))
			{
				Object element = actionRequest.getActionList().getAction().get(i).getValue();
				log.debug("ActionList index: " +i +" Type: Power on. NodeName: " +((PowerOnActionType)element).getNodeName());
			}
			else if(actionRequest.getActionList().getAction().get(i).getDeclaredType().getSimpleName().toString().matches("StandByActionType"))
			{
				Object element = actionRequest.getActionList().getAction().get(i).getValue();
				log.debug("ActionList index: " +i +" Type: Standby. NodeName: " +((StandByActionType)element).getNodeName());
			}
			else if(actionRequest.getActionList().getAction().get(i).getDeclaredType().getSimpleName().toString().matches("StartJobActionType"))
			{
				Object element = actionRequest.getActionList().getAction().get(i).getValue();	
				log.debug("ActionList index: " +i +" Type: Start job. NodeName: " +((StartJobActionType)element).getNodeName() + " Job ID: " + ((StartJobActionType)element).getJobID());
			}
		}						
	}
	
	/**
	 * Utility Method for getting the number of CPUs
	 * of a server 
	 * 
	 */
	
	public static int getNbCPU(ServerType server) {
		int cpus=0;
		for(MainboardType mainboard : server.getMainboard())
			cpus += mainboard.getCPU().size();
		return cpus;
	}
	
	/**
	 * Utility Method for getting the number of Cores
	 * per CPU of a server 
	 * 
	 */
	
	public static int getNbCoresCPU(ServerType server) {
		int coresPerCPU=0;
		for(MainboardType mainboard : server.getMainboard())
		{
			CPUType cpu = mainboard.getCPU().get(0);
			coresPerCPU = cpu.getCore().size();
		}
			
		return coresPerCPU;
	}
	
	
	/**
	 * Get the server in idle state (suppress every loads) 
	 * 
	 */
	public static ServerType getServerIdle(ServerType server, IPowerCalculator powerCalculator) {
	    
	    	ServerType myServer = (ServerType) server.clone();
	    	    	    
	    	//zeroing the CPU loads, core loads, memory usage and HDD write/read rate
	    	MainboardType mainboard = myServer.getMainboard().get(0);
	    	if(mainboard != null) {
	    		
	    		// Zero the memory usage
	    		mainboard.setMemoryUsage(new MemoryUsageType (0.0));	    		
	    		// Zero the CPU loads, core loads
	    		for(CPUType cpu : mainboard.getCPU()) {
	    			cpu.setCpuUsage(new CpuUsageType(0.0) );
	    			
	    			for(CoreType core : cpu.getCore()) {
	    				core.setCoreLoad(new CoreLoadType(0.0) );
	    			}    			
	    		}  
	    		// Zero the HDD write/read rate
	    		for(HardDiskType hdd : mainboard.getHardDisk()) {
	    			hdd.setReadRate(new IoRateType(0.0));
	    			hdd.setWriteRate(new IoRateType(0.0));
	    		}
	    	}
	    		    		    	
	    	//Zero the FAN actual RPM
	    	if(myServer instanceof RackableServerType) {
	    		RackableServerType myRackableServer = (RackableServerType) myServer;   		
	    		for(FanType fan : myRackableServer.getFan()) {
	    			fan.setActualRPM(new RPMType(0));
	    		}
	    	}
	    		    		    	
	    	return myServer; 
	}
	
	/**
	 * Compute the power induced by one Job on a server
	 * 
	 */
    public double computePowerForJob(ServerType server, JobType myJob, IPowerCalculator powerCalculator) {
    	
    	CoreLoadType coreLoad = new CoreLoadType ();
    	MemoryUsageType memUsage = new MemoryUsageType();
		memUsage.setValue(myJob.getNeededMemory().getValue());
		
		
//		double fanRPMpercentage = 0.0;
//		double hddReadRatepercentage = 0.0;
//		double hddWriteRatepercentage = 0.0;
//					
//		log.debug("Simulation flag: " +powerCalculator.getSimulationFlag());
//		
//		Properties prop = new Properties();
//	    try
//	    {
//	    	InputStream is = this.getClass().getClassLoader().getResourceAsStream("config/OptimizerEngineHPC.properties");
//	    	prop.load(is);
//	    	fanRPMpercentage = Double.parseDouble(prop.getProperty("fanRPMpercentage").trim());
//	    	hddReadRatepercentage = Double.parseDouble(prop.getProperty("hddReadRatepercentage").trim());
//	    	hddWriteRatepercentage = Double.parseDouble(prop.getProperty("hddWriteRatepercentage").trim());
//	    	log.debug("fanRPMpercentage is set as: " + fanRPMpercentage);
//	    	log.debug("hddReadRatepercentage is set as: " + hddReadRatepercentage);
//	    	log.debug("hddWriteRatepercentage is set as: " + hddWriteRatepercentage);
//	    }
//	    catch (Exception e)
//	    {
//	    	// Some error with handling files
//	    	log.debug("Error with handling files...");
//	    }
    	   	
    	MainboardType mainboard = server.getMainboard().get(0);
    	if(mainboard != null) {
    		
    		//1. Set memory usage
    		mainboard.setMemoryUsage(memUsage);
    		
    		//2. Set Hard disk read/write rate
    		for(HardDiskType hdd : mainboard.getHardDisk()) {
    			hdd.setReadRate(new IoRateType(hddReadRatepercentage * hdd.getMaxReadRate().getValue()));
    			hdd.setWriteRate(new IoRateType(hddWriteRatepercentage * hdd.getMaxWriteRate().getValue()));
    		}
    	}
				
		//3. Set actual RPM caused by the job
    	if(server instanceof RackableServerType) {
    		RackableServerType myRackableServer = (RackableServerType) server;
    		//log.debug("PSU Load: " +myRackableServer.getPSU().get(0).getLoad().getValue());
    		for(FanType fan : myRackableServer.getFan()) {
    			fan.setActualRPM(new RPMType((int)(fanRPMpercentage * fan.getMaxRPM().getValue())));
    		}
    	}
    	    		
		//4. Set core load of the job
		int CPUs = getNbCPU(server);
		int coresPerCPU = getNbCoresCPU(server);
		double jobPower = 0.0;
		Vector<Double> estimatedPower = new Vector<Double> ();		
		double estimateLoad = 0.0;
		
		//double requested_cores = myJob.getNeededCoresPerNode().getValue();
		double total_cores = CPUs * coresPerCPU;
		//double core_per_node_factor = requested_cores / total_cores;
		server.setMeasuredPower(null);
		
		
		//Compute the estimated power consumption with different core loads
		for(int index = 0; index < 10; index++)
		{
			estimateLoad+=10.0;
			log.debug("Estimated load: " +estimateLoad);
			coreLoad.setValue(estimateLoad);
			int cores = myJob.getNeededCoresPerNode().getValue();
			int loadedCores = 0;
					   						        			
			//Set estimated coreLoad to needed cores
			for(int i = 0; i < CPUs; i++)
			{
				if(loadedCores == cores)
					break;
				for(int j = 0; j < coresPerCPU; j++)
				{
    				server.getMainboard().get(0).getCPU().get(i).getCore().get(j).setCoreLoad(coreLoad);
    				//log.debug("Core Load: " +server.getMainboard().get(0).getCPU().get(i).getCore().get(j).getCoreLoad().getValue());
    				
    				loadedCores++;
    				if(loadedCores == cores)
    					break;
				}
			}
			jobPower = powerCalculator.computePowerServer(server).getActualConsumption();
			estimatedPower.add(jobPower);
		}
		double sum = 0.0;
		jobPower = 0.0;
		
		for(int i= 0; i < estimatedPower.size(); i++)
			sum+=estimatedPower.get(i);

		jobPower = sum/estimatedPower.size();		
		log.debug("Average power consumption of a server: " +jobPower);
		//log.debug("Idle power consumption of a server: " +idlePower);
		
		//jobPower *= core_per_node_factor;
		//log.debug("core_per_node_factor: " +core_per_node_factor);
		log.debug("total_cores: " +total_cores);
		log.debug("Average power consumption of a server using " +myJob.getNeededCoresPerNode().getValue() + " cores: " +jobPower);
		
		return jobPower;
    	
    	
    }
    
	/**
	 * Compute the idle power on a server
	 * 
	 */
    public double computeIdlePower(ServerType server, IPowerCalculator powerCalculator) {
    	
		
		ServerType tempServer = (ServerType) server.clone();
		double idlePower = 0.0;
		
		double fanRPMpercentage = 0.0;
		double hddReadRatepercentage = 0.0;
		double hddWriteRatepercentage = 0.0;
					
		log.debug("Simulation flag: " +powerCalculator.getSimulationFlag());
		
		Properties prop = new Properties();
	    try
	    {
	    	InputStream is = this.getClass().getClassLoader().getResourceAsStream("config/OptimizerEngineHPC.properties");
	    	prop.load(is);
	    	fanRPMpercentage = Double.parseDouble(prop.getProperty("fanRPMpercentage").trim());
	    	hddReadRatepercentage = Double.parseDouble(prop.getProperty("hddReadRatepercentage").trim());
	    	hddWriteRatepercentage = Double.parseDouble(prop.getProperty("hddWriteRatepercentage").trim());
	    	log.debug("fanRPMpercentage is set as: " + fanRPMpercentage);
	    	log.debug("hddReadRatepercentage is set as: " + hddReadRatepercentage);
	    	log.debug("hddWriteRatepercentage is set as: " + hddWriteRatepercentage);
	    }
	    catch (Exception e)
	    {
	    	// Some error with handling files
	    	log.debug("Error with handling files...");
	    }
    	   	
//    	MainboardType mainboard = server.getMainboard().get(0);
//    	if(mainboard != null) {
//    		
//    		//1. Set memory usage
//    		mainboard.setMemoryUsage(memUsage);
//    		
//    		//2. Set Hard disk read/write rate
//    		for(HardDiskType hdd : mainboard.getHardDisk()) {
//    			hdd.setReadRate(new IoRateType(hddReadRatepercentage * hdd.getMaxReadRate().getValue()));
//    			hdd.setWriteRate(new IoRateType(hddWriteRatepercentage * hdd.getMaxWriteRate().getValue()));
//    		}
//    	}
				
		//3. Set actual RPM caused by the job
    	if(tempServer  instanceof RackableServerType) {
    		RackableServerType myRackableServer = (RackableServerType) tempServer ;
    		//log.debug("PSU Load: " +myRackableServer.getPSU().get(0).getLoad().getValue());
    		for(FanType fan : myRackableServer.getFan()) {
    			fan.setActualRPM(new RPMType((int)(fanRPMpercentage * fan.getMaxRPM().getValue())));
    		}
    	}
    	tempServer.setMeasuredPower(null);				
		idlePower = powerCalculator.computePowerServer(tempServer).getActualConsumption();
		
		return idlePower;
    	    	
    }
    
	/**
	 * Get the estimated wait time of the cluster
	 * 
	 */
    
    public long getEstimatedWaitTime(DatacenterType datacenter, JobType myJob)
    {
    	long estimatedWaitTime = 0;
    	DatacenterType tempDC = (DatacenterType) datacenter.clone();
    	long runningJobsRemainingTime = 0;
    	long queuedJobsTime = 0;
    	long totalCores = 0;
    	int queueSize = 0;
    	
		boolean resourcesFound = false;   
		//Check if there are free resources for the job
		resourcesFound = checkDataCenter(tempDC, myJob);
		queueSize = getQueueSize(tempDC);
		
		//If resources are found
		if(resourcesFound == true) {
			//If the queue size is zero, the job can be started immediately
			if(queueSize == 0) {
				estimatedWaitTime = 0;
			}
			//If there are jobs in the queue
			else {
				runningJobsRemainingTime = getRunningJobsRemainingTime(tempDC);
				queuedJobsTime = getQueuedJobsTime(tempDC);
				totalCores = getClusterTotalCores(tempDC);

				estimatedWaitTime = (runningJobsRemainingTime  + queuedJobsTime) / totalCores;		
			}
		}
		//If resources are not found
		else {
			runningJobsRemainingTime = getRunningJobsRemainingTime(tempDC);
			queuedJobsTime = getQueuedJobsTime(tempDC);
			totalCores = getClusterTotalCores(tempDC);

			estimatedWaitTime = (runningJobsRemainingTime  + queuedJobsTime) / totalCores;
		}

    	return estimatedWaitTime;
    }
    
	/**
	 * Get the remaining wall time of the running jobs
	 * 
	 */
   
    public long getRunningJobsRemainingTime(DatacenterType datacenter)
    {
    	
    	List<JobType> tempRunningList = new LinkedList<JobType>(); // temp List of running jobs
    	long remainingTime = 0;
    	long jobRemainingTime = 0;
    	
    	//Andre:
    	// Iteration over the Racks
    	Iterator<RackType> rackIter = datacenter.getRack().iterator();
		while(rackIter.hasNext())
		{
			RackType rackType = rackIter.next();
			
			// Iteration over the "Enclosure" objects
			Iterator<EnclosureType> enclosureIter = rackType.getEnclosure().iterator();
			while(enclosureIter.hasNext())
			{
				EnclosureType enclosureType = enclosureIter.next();
				
				// Iteration over the "BladeServer" objects
				Iterator<BladeServerType> bladeIter = enclosureType.getBladeServer().iterator();
				while(bladeIter.hasNext())
				{
					ServerType bladeServer = bladeIter.next();								
					// The node is the RMS
		    		if(bladeServer.getName().toString().matches("HPC_RESOURCE_MANAGEMENT"))
		    		{
		    			/// Form temporary data structures for the queue and running jobs
		    			Iterator<QueueType> queueIterator = bladeServer.getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
		    			while(queueIterator.hasNext()){
		    				QueueType queueType = queueIterator.next();
		    				for(JobType job: queueType.getJobs()) {
		    	    			if(job.getStatus().toString().matches("RUNNING")) {
		    	    				tempRunningList.add(job);
		    	    			}
		    	    		}
		    			}
		    			break;
		    		}
				}
			}
			
			// Iteration over the "RackableServer" objects
			Iterator<RackableServerType> serverIter = rackType.getRackableServer().iterator();
			while(serverIter.hasNext())
			{
				ServerType server = serverIter.next();
	        	// The node is the RMS
	    		if(server.getName().toString().matches("HPC_RESOURCE_MANAGEMENT"))
	    		{
	    			/// Form temporary data structures for the queue and running jobs
	    			Iterator<QueueType> queueIterator = server.getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
	    			while(queueIterator.hasNext()){
	    				QueueType queueType = queueIterator.next();
	    				for(JobType job: queueType.getJobs()) {
	    	    			if(job.getStatus().toString().matches("RUNNING")) {
	    	    				tempRunningList.add(job);
	    	    			}
	    	    		}
	    			}
	    			break;
	    		}
			}
		}       
    	//End: Andre
    	
    	//Form temporary list for the running jobs
//    	for(ServerType server : datacenter.getRack().get(0).getRackableServer()) {    		
//    		if(server.getName().toString().matches("HPC_RESOURCE_MANAGEMENT")) {
//    			/// Form temporary data structures for the queue and running jobs
//    			Iterator<QueueType> queueIterator = server.getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
//    			while(queueIterator.hasNext()){
//    				QueueType queueType = queueIterator.next();
//    				for(JobType job: queueType.getJobs()) {
//    	    			if(job.getStatus().toString().matches("RUNNING")) {
//    	    				tempRunningList.add(job);
//    	    			}
//    	    		}
//    			}
//    		}
//    	}
    	
    	//Get the remaining wall time of the running jobs
    	for(int i = 0; i <  tempRunningList.size(); i++)
    	{
    		JobType tempJob = tempRunningList.get(i);
    		jobRemainingTime = ((tempJob.getTimeOfStart().getValue() + tempJob.getWallTime().getValue()) - getEpochTime()) * tempJob.getNeededCoresPerNode().getValue() * tempJob.getNumberOfNodes().getValue();
    		remainingTime += jobRemainingTime;
    	}
    	
    	log.debug("Remaining Time for running jobs: " +remainingTime);
    	tempRunningList.clear();
    	return remainingTime;
    }
    
	/**
	 * Get the total wall time of the queued jobs
	 * 
	 */
    
    public long getQueuedJobsTime(DatacenterType datacenter)
    {
    	List<JobType> tempJobList = new LinkedList<JobType>(); // temp List of jobs in the queue
    	long queuedTime = 0;
    	long jobTime = 0;
    	   	
    	//Andre:
    	// Iteration over the Racks
    	Iterator<RackType> rackIter = datacenter.getRack().iterator();
		while(rackIter.hasNext())
		{
			RackType rackType = rackIter.next();
			
			// Iteration over the "Enclosure" objects
			Iterator<EnclosureType> enclosureIter = rackType.getEnclosure().iterator();
			while(enclosureIter.hasNext())
			{
				EnclosureType enclosureType = enclosureIter.next();
				
				// Iteration over the "BladeServer" objects
				Iterator<BladeServerType> bladeIter = enclosureType.getBladeServer().iterator();
				while(bladeIter.hasNext())
				{
					ServerType bladeServer = bladeIter.next();								
					// The node is the RMS
		    		if(bladeServer.getName().toString().matches("HPC_RESOURCE_MANAGEMENT"))
		    		{
		    			/// Form temporary data structures for the queue and running jobs
		    			Iterator<QueueType> queueIterator = bladeServer.getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
		    			while(queueIterator.hasNext()){
		    				QueueType queueType = queueIterator.next();
		    				for(JobType job: queueType.getJobs()) {
		    	    			if(job.getStatus().toString().matches("QUEUED")) {
		    	    				tempJobList.add(job);
		    	    			}
		    	    		}
		    			}
		    			break;
		    		}
				}
			}
			
			// Iteration over the "RackableServer" objects
			Iterator<RackableServerType> serverIter = rackType.getRackableServer().iterator();
			while(serverIter.hasNext())
			{
				ServerType server = serverIter.next();
	        	// The node is the RMS
	    		if(server.getName().toString().matches("HPC_RESOURCE_MANAGEMENT"))
	    		{
	    			/// Form temporary data structures for the queue and running jobs
	    			Iterator<QueueType> queueIterator = server.getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
	    			while(queueIterator.hasNext()){
	    				QueueType queueType = queueIterator.next();
	    				for(JobType job: queueType.getJobs()) {
	    	    			if(job.getStatus().toString().matches("QUEUED")) {
	    	    				tempJobList.add(job);
	    	    			}
	    	    		}
	    			}
	    			break;
	    		}
			}
		}       
    	//End: Andre
    	
    	
    	//Form temporary list for the queued jobs
//    	for(ServerType server : datacenter.getRack().get(0).getRackableServer()) {    		
//    		if(server.getName().toString().matches("HPC_RESOURCE_MANAGEMENT")) {
//    			Iterator<QueueType> queueIterator = server.getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
//    			while(queueIterator.hasNext()){
//    				QueueType queueType = queueIterator.next();
//    				for(JobType job: queueType.getJobs()) {
//    	    			if(job.getStatus().toString().matches("QUEUED")) {
//    	    				tempJobList.add(job);
//    	    			}
//    	    		}
//    			}
//    		}
//    	}
    	
    	//Get the wall time of the queued jobs
    	for(int i = 0; i <  tempJobList.size(); i++) {
    		JobType tempJob = tempJobList.get(i);
    		jobTime = tempJob.getWallTime().getValue() * tempJob.getNeededCoresPerNode().getValue() * tempJob.getNumberOfNodes().getValue();
    		queuedTime += jobTime;
    	}
    	
    	log.debug("Total amount of wall time for queued jobs: " +queuedTime);
     	tempJobList.clear();
    	return queuedTime;
    }
    
	/**
	 * Get the total number of cores in the cluster
	 * 
	 */
    
    public int getClusterTotalCores(DatacenterType datacenter)
    {
    	int totalCores = 0;	
    	
    	//Andre
    	// Iteration over the Racks
    	Iterator<RackType> rackIter = datacenter.getRack().iterator();
		while(rackIter.hasNext())
		{
			RackType rackType = rackIter.next();
			
			// Iteration over the "Enclosure" objects
			Iterator<EnclosureType> enclosureIter = rackType.getEnclosure().iterator();
			while(enclosureIter.hasNext())
			{
				EnclosureType enclosureType = enclosureIter.next();
				
				// Iteration over the "BladeServer" objects
				Iterator<BladeServerType> bladeIter = enclosureType.getBladeServer().iterator();
				while(bladeIter.hasNext())
				{
					ServerType server = bladeIter.next();								
					// The node is the RMS
		    		if(server.getName().toString().matches("HPC_COMPUTE_NODE"))
		    		{
		    			for(CPUType cpu: server.getMainboard().get(0).getCPU()) {
			    			totalCores += cpu.getCore().size();		
			    		}
		    		}
				}
			}
			
			// Iteration over the "RackableServer" objects
			Iterator<RackableServerType> serverIter = rackType.getRackableServer().iterator();
			while(serverIter.hasNext())
			{
				ServerType server = serverIter.next();
	        	// The node is the RMS
	    		if(server.getName().toString().matches("HPC_COMPUTE_NODE"))
	    		{
	    			for(CPUType cpu: server.getMainboard().get(0).getCPU()) {
		    			totalCores += cpu.getCore().size();		
		    		}
	    		}
			}
		}  
		//End: Andre
    	
    	
//    	for(ServerType server : datacenter.getRack().get(0).getRackableServer()) {   		
//    		if(server.getName().toString().matches("HPC_COMPUTE_NODE")) {
//	    		for(CPUType cpu: server.getMainboard().get(0).getCPU()) {
//	    			totalCores += cpu.getCore().size();		
//	    		}
//    		}
//    	}
    	
    	log.debug("Cluster total cores: " +totalCores);
   	
    	return totalCores;
    }
    
	/**
	 * Get the total number of compute nodes in the cluster
	 * 
	 */
    
    public int getClusterNumServers(DatacenterType datacenter)
    {
    	DatacenterType tempDC = (DatacenterType) datacenter.clone();
    	int numServers = 0;	
    	
    	//Andre
    	// Iteration over the Racks
    	Iterator<RackType> rackIter = datacenter.getRack().iterator();
		while(rackIter.hasNext())
		{
			RackType rackType = rackIter.next();
			
			// Iteration over the "Enclosure" objects
			Iterator<EnclosureType> enclosureIter = rackType.getEnclosure().iterator();
			while(enclosureIter.hasNext())
			{
				EnclosureType enclosureType = enclosureIter.next();
				
				// Iteration over the "BladeServer" objects
				Iterator<BladeServerType> bladeIter = enclosureType.getBladeServer().iterator();
				while(bladeIter.hasNext())
				{
					ServerType server = bladeIter.next();								
					// The node is the RMS
		    		if(server.getName().toString().matches("HPC_COMPUTE_NODE"))
		    		{
		    			numServers++;
		    		}
				}
			}
			
			// Iteration over the "RackableServer" objects
			Iterator<RackableServerType> serverIter = rackType.getRackableServer().iterator();
			while(serverIter.hasNext())
			{
				ServerType server = serverIter.next();
	        	// The node is the RMS
	    		if(server.getName().toString().matches("HPC_COMPUTE_NODE"))
	    		{
	    			numServers++;
	    		}
			}
		}  
		//End: Andre
    	
//    	for(ServerType server : tempDC.getRack().get(0).getRackableServer()) { 		
//    		if(server.getName().toString().matches("HPC_COMPUTE_NODE")){
//    			numServers++;
//    		}
//    	}
    	
    	log.debug("Cluster total compute nodes: " +numServers);
   	
    	return numServers;
    }
    
	/**
	 * Get the queue size in the cluster
	 * 
	 */
    public int getQueueSize(DatacenterType datacenter)
    {
    	DatacenterType tempDC = (DatacenterType) datacenter.clone();
    	int queueSize = 0;
    	
    	for(ServerType server : tempDC.getRack().get(0).getRackableServer()) {    		
    		if(server.getName().toString().matches("HPC_RESOURCE_MANAGEMENT")) {
    			Iterator<QueueType> queueIterator = server.getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
    			while(queueIterator.hasNext()){
    				QueueType queueType = queueIterator.next();
    				for(JobType job: queueType.getJobs()) {
    	    			if(job.getStatus().toString().matches("QUEUED")) {
    	    				queueSize++;
    	    			}
    	    		}
    			}
    		}
    	}
 	
    	return queueSize;
    }
    
	/**
	 * Gets the smallest application rank from all the DCs
	 * 
	 */
    
    public int getSmallestRank(String benchmarkName, FIT4GreenType model)
    {
    	int smallestRank = 0;
    	int candidateRank = -1;
    	
    	//If the user has specified a benchmark
    	if(!benchmarkName.isEmpty())
    	{
			for(DatacenterType datacenter : model.getSite().get(0).getDatacenter())
			{
				for(ApplicationBenchmarkType benchmark : datacenter.getApplicationBenchmark())
				{
					if(benchmark.getBenchmarkID().matches(benchmarkName))
					{
						if(benchmark.getRank().getValue() < candidateRank || candidateRank == -1)
						{
							smallestRank = benchmark.getRank().getValue();
							candidateRank = benchmark.getRank().getValue();
						}
						
					}
				}
			}
    	}
    	//Else: no benchmark has been specified, get the average smallest value from all benchmarks of DC
    	else
    	{
    		int sum = 0;
    		int n = 0;
    		int dcSmallest = 0;
    		
    		// get the smallest rank value from all clusters
			for(DatacenterType datacenter : model.getSite().get(0).getDatacenter())
			{	    		
				for(ApplicationBenchmarkType benchmark : datacenter.getApplicationBenchmark())
				{
					if(benchmark.getRank().getValue() < candidateRank || candidateRank == -1)
					{
						dcSmallest = benchmark.getRank().getValue();
						candidateRank = dcSmallest;
					}				
				}
				log.debug("Smallest rank of the data centre: "+dcSmallest);
				sum += dcSmallest;
				n++;
				candidateRank = -1;				
			} 
			//Get the average value from the smallest rank values
			smallestRank = sum/n;
    	}
    	
    	return smallestRank;
    }
    
	/**
	 * Gets the application rank of a  DC
	 * 
	 */
    
    public int getDCRank(String benchmarkName, DatacenterType datacentre)
    {
    	int dcRank = 0;
    	
    	//If the user has specified as benchmark
    	if(!benchmarkName.isEmpty())
    	{
			for(ApplicationBenchmarkType benchmark : datacentre.getApplicationBenchmark())
			{
				if(benchmark.getBenchmarkID().matches(benchmarkName))
				{
					dcRank = benchmark.getRank().getValue();			
				}
			}
    	}
    	//Else: no benchmark has been specified, get the average value from all benchmarks
    	else
    	{
    		int sum = 0;
    		int n = 0;
			for(ApplicationBenchmarkType benchmark : datacentre.getApplicationBenchmark())
			{
				sum += benchmark.getRank().getValue();
				n++;
			}
			dcRank = sum/n; 		
    	}
    	
    	return dcRank;
    	
    }
    
	/**
	 * Checks if a data centre is suitable for a job
	 * 
	 * @param Datacenter ID, List of suitable clusters
	 * @return true if suitable, false otherwise
	 */
	
	public boolean isSuitableCluster(String dcID, String[] suitableClusters)
	{
		boolean isSuitable = false;
		
        for(int i=0;i<suitableClusters.length;i++)
        {
        	if(suitableClusters[i].matches(dcID))
        	{
        		isSuitable = true;
        		break;
        	}
        }
		
		return isSuitable;
	}
	
	/**
	 * Checks if a data centre has free resources for a job
	 * 
	 * @param DatacenterType datacenter, JobType myJob
	 * @return true if resources found, false otherwise
	 */
	
	public boolean checkDataCenter(DatacenterType datacenter, JobType myJob)
	{
		
		double serverMemory;	
		int serverCores;
		int coresInUse;
			
		// Go through the meta-model and forms temporary data structures for
		// the job queue, jobs that are currently running, and servers
		
		Iterator<RackableServerType> myItr = datacenter.getRack().get(0).getRackableServer().iterator();

        // Iteration over the "rackableServer" items
        while (myItr.hasNext())
        {       	
        	ServerType serverObject = myItr.next();
           			
        	// The node is the RMS
    		if(((ServerType)serverObject).getName().toString().matches("HPC_RESOURCE_MANAGEMENT"))
    		{
    			/// Form temporary data structures for the queue and running jobs
    			Iterator<QueueType> queueIterator = ((ServerType)serverObject).getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
    			while(queueIterator.hasNext()){
    				QueueType queueType = queueIterator.next();
    				Iterator<JobType> itr = queueType.getJobs().iterator();
    				while(itr.hasNext())
    				{
    					// Retrieve the job from the meta-model and place it into a list
    					JobType jobObject = itr.next();  								
    					enqueue(jobObject);
    				}

    			}
    		}	
    		// Else:  the node is a computing node
    		else
    		{
    			// Create a temporary server structure
    			ServerType server = serverObject;   			
				serverMemory = 0;				
				serverCores = 0;
				coresInUse = 0;
				
				// Iteration over the "Cores" objects
				Iterator<CPUType> cpuIter = serverObject.getMainboard().get(0).getCPU().iterator();
				while(cpuIter.hasNext())
				{
					CPUType cpuObject = cpuIter.next();
					Iterator<CoreType> coreIter = cpuObject.getCore().iterator();
					while(coreIter.hasNext())
					{
						CoreType coreObject = coreIter.next();
						serverCores++;
					}
				}
								
				coresInUse = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue();
    			
				// Iteration over the RAMStick objects
    			Iterator<RAMStickType> iter = serverObject.getMainboard().get(0).getRAMStick().iterator();
				while(iter.hasNext())
				{
					RAMStickType tempObject =  iter.next();
					serverMemory = serverMemory + tempObject.getSize().getValue();
					
				}
				
				try {
					
	    			Iterator<Object> jobIter = serverObject.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
	    			while(jobIter.hasNext())
	    			{
	    				JobType tempJob =  (JobType) jobIter.next();
	    				serverMemory = serverMemory - tempJob.getNeededMemory().getValue();					
	    			}
				}
				catch (NullPointerException e)
				{
					
				}
				  									
				serverMemoryList.add(serverMemory);
				serverCoresList.add(serverCores);
				serverCoresInUseList.add(coresInUse);
				reservedList.add(false);
				srvList.add(server); 						
    		}       

        }
        
        int[] serverNumber = new int[myJob.getNumberOfNodes().getValue()];
		searchServers(myJob, myJob.getNumberOfNodes().getValue(), myJob.getNeededMemory().getValue(), myJob.getNeededCoresPerNode().getValue(), serverNumber, srvList);
		boolean serversFound = true;
		serversFound = checkServerNumber(serverNumber, serversFound, myJob.getNumberOfNodes().getValue(), srvList);
		
		// Clear all temporary data structures
		jobList.clear();
		runningList.clear();
		srvList.clear();
		jobIdList.clear();
		reservedList.clear();
		serverMemoryList.clear();
		serverCoresList.clear();
		serverCoresInUseList.clear();
		
		if(serversFound == true)
		{
			return true;
		}
		else
		{
			return false;
		}	
		
	}
    
	/**
	 * Simulates default scheduling behavior and wakes up nodes if needed
	 * 
	 */
    
    public void fit4greenActions()
    {
		if(jobList.isEmpty())
		{
			// Queue empty, no servers need to be woken up
			log.debug("Queue empty.");
		}
		
		else
		{			
			// 1. Find resources for the first job in the queue
			findResources(); 
		   	   	
	    	// 2. Simulate backfill first fit and check if some servers need to be woken up
	    	
			// Do backfilling if the list is not empty and there is more than one element in the queue
			if(!jobList.isEmpty() && jobList.size() > 1)
			{
				log.debug("Executing backfill first fit..");					
				int index = 1; // Index starts from 1, since the 1st element in the queue is 0
				
				do
				{
					
					JobType temp_job = jobList.get(index); // Pop a job from the queue
					boolean resourcesFound = true;
					int nodes;
					nodes = temp_job.getNumberOfNodes().getValue();
					int[] serverNumber = new int[nodes];
					
					// Search for appropriate servers
					searchServers(temp_job, nodes, temp_job.getNeededMemory().getValue(), temp_job.getNeededCoresPerNode().getValue(), serverNumber, srvList);
					
					// Check if the servers were found
					resourcesFound = checkServerNumber(serverNumber, resourcesFound, nodes, srvList);
					
					if (resourcesFound == false)
					{
						// No servers found, continue the search
						log.debug("No servers were found for the job index: " +index);
					}
					else
					{
						log.debug("Servers were found for the job index: " +index);
						
						// Get the estimated completion time of the new job
						long compTime = getEpochTime() + temp_job.getWallTime().getValue();
												
						// Get the estimated start time of the 1st job in the queue
						long startTime;
						startTime = getEstimatedStartTime(srvList);
						log.debug("Estimated start time of the 1st job: " +startTime);
						log.debug("Completion time of the new job: " + compTime);
						
						// Check if the job can be executed before the 1st job
						if (startTime > compTime)
						{
							log.debug("The job can be executed before the 1st element in the queue.");
							temp_job.getTimeOfStart().setValue(getEpochTime());
																													
							// Generate actions that the server needs to do
							for(int i = 0; i < nodes; i++)
							{
								ServerType server = srvList.get(serverNumber[i]);
								if (server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("DOWN") || server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY") || server.getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED"))
								{
									log.debug("wake up server: " + serverNumber[i]);
									createActionRequest("POWER_ON", server.getFrameworkID(), "");
								}
								if(server.getNativeOperatingSystem().getNode().get(0).getJobRef() != null){
									server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(temp_job);
								}
								else {
									IDREFS jobRef = new IDREFS();						
									server.getNativeOperatingSystem().getNode().get(0).setJobRef(jobRef);
									server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().add(temp_job);
								}
								
								server.getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("RUNNING"));
								//Set new value for used cores
								NrOfCoresType usedCores = new NrOfCoresType ();
								int usedCoresValue = server.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue() + temp_job.getNeededCoresPerNode().getValue();
								usedCores.setValue(usedCoresValue);
								
								server.getNativeOperatingSystem().getNode().get(0).setCoresInUse(usedCores);
								
								double tempMemory = server.getNativeOperatingSystem().getNode().get(0).getAvailableMemory();
								server.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(tempMemory - temp_job.getNeededMemory().getValue());
								//serverCoresInUseList.set(serverNumber[i], (serverCoresInUseList.get(serverNumber[i]) + temp_job.getNeededCoresPerNode().getValue()));
								//serverMemoryList.set(serverNumber[i], (serverMemoryList.get(serverNumber[i]) - temp_job.getNeededMemory().getValue()));
																								
							}
	
							jobList.remove(index); // remove the job from the temporary queue
							
							// Include the job into the running jobs list
							temp_job.setStatus(JobStatusType.valueOf("RUNNING"));
							enqueue(temp_job);
							
							// Decrement the index, to continue to the next element in the queue
							index--;
						}
						else
						{
							log.debug("The job cannot be executed before the 1st element.");
						}
						
					}
					
					// increment the index
					index++;
					
					// Continue backfill first fit until the list becomes empty, 
					// end of the queue is reached, or there are no resources available
				} while(!jobList.isEmpty() && index != jobList.size() && isResourcesAvailable() == true);
			}
			log.debug("Backfill first fit simulation finished.");
		}
		// 3. check the queue if there are certain jobs that require more nodes than there are powered ON
		// This is to ensure that jobs get out of the deferred state
		if(jobList.isEmpty() == false)
		{
			int largestJobNodes = 0;
			int poweredOnNodes = 0;
			largestJobNodes = getLargestJobNodes();
			poweredOnNodes = getPoweredOnNodes();
			log.debug("Largest job requires: "+largestJobNodes);
			log.debug("poweredOnNodes: "+poweredOnNodes);
			
			if(largestJobNodes > poweredOnNodes)
			{
				log.debug("There is a job requiring more nodes than are powered ON.");
				// Power ON more nodes
				for(int i = 0; i < srvList.size(); i++)
				{
					if(srvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("STANDBY") || srvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("DOWN") || srvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("HYBERNATED"))
					{
						log.debug("wake up server: " + i);
						srvList.get(i).getNativeOperatingSystem().getNode().get(0).setStatus(NodeStatusType.valueOf("BUSY"));
						createActionRequest("POWER_ON", srvList.get(i).getFrameworkID(), "");
						poweredOnNodes++;
						if(poweredOnNodes == largestJobNodes)
							break;
					}
				}
			}
		}		
    }
    
	/**
	 * Gets the amount of nodes required by the largest job in the queue
	 * 
	 */
    
    public int getLargestJobNodes()
    {
    	int candidateNodes = -1;
    	int index = 0;
    	
    	//Go through the whole queue
		do 
		{
			JobType temp_job = jobList.get(index);
			int nodes;
			nodes = temp_job.getNumberOfNodes().getValue();
			if(nodes > candidateNodes || candidateNodes == -1)
			{
				candidateNodes = nodes;
			}
			index++;
		} while (index != jobList.size());
    	
    	return candidateNodes;
    }
    
	/**
	 * Gets the amount of nodes that are ON
	 * 
	 */
    
    public int getPoweredOnNodes()
    {
    	int poweredOnNodes = 0;
	
		for(int i = 0; i < srvList.size(); i++)
		{
			if(srvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("IDLE") || srvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("RUNNING") || srvList.get(i).getNativeOperatingSystem().getNode().get(0).getStatus().toString().matches("RESERVED"))
			{
				poweredOnNodes++;
			}
		}
    	
    	return poweredOnNodes;
    }
    
    
    public int getServerIndex(ServerType server)
    {
    	int serverIndex = 0;
    	String serverID = server.getFrameworkID();
    	String tempServerID = "";
		for(int i = 0; i < srvList.size(); i++)
		{
			tempServerID = srvList.get(i).getFrameworkID();
			if(serverID.matches(tempServerID))
			{
				serverIndex = i;
				break;
			}
			
		}
		return serverIndex;
    	
    }
    
    public double getServerMemory(ServerType server)
    {
		double serverMemory = 0;	
		
		// Create a temporary server structure
		ServerType tempServer = server;   						
		
		// Iteration over the RAMStick objects
		Iterator<RAMStickType> iter = tempServer.getMainboard().get(0).getRAMStick().iterator();
		while(iter.hasNext())
		{
			RAMStickType tempObject =  iter.next();
			serverMemory = serverMemory + tempObject.getSize().getValue();
			
		}
		
		return serverMemory;
    }
    
	/**
	 * Creates temporary data structures
	 * 
	 */
    
    public void createTemporaryDataStructures(FIT4GreenType model)
    {
    	
		double serverMemory = 0;	
//		int serverCores;
//		int coresInUse;
		
		// Form a  temporary data structure clusterList
		// clusterList includes a Cluster object, that holds a PUE value,
		// a server list, name and softwareLicense list								
		for( SiteType site : model.getSite())
		{
			Cluster tempCluster = new Cluster();
			
			for(DatacenterType datacenter : site.getDatacenter())        	        	
    		//if(datacenter.getFrameworkCapabilities().get(0).getStatus().toString().matches("RUNNING"))
    		{
            	//log.debug("OptimizerEngineHPC: Performing Global Optimization on Data Centre: " +datacenter.getFrameworkCapabilities().get(0).getId());		

            	// Andre
            	// Iteration over the Racks
            	Iterator<RackType> rackIter = datacenter.getRack().iterator();
				while(rackIter.hasNext())
				{
					RackType rackType = rackIter.next();
					
					// Iteration over the "Enclosure" objects
					Iterator<EnclosureType> enclosureIter = rackType.getEnclosure().iterator();
					while(enclosureIter.hasNext())
					{
						EnclosureType enclosureType = enclosureIter.next();
						
						// Iteration over the "BladeServer" objects
						Iterator<BladeServerType> bladeIter = enclosureType.getBladeServer().iterator();
						while(bladeIter.hasNext())
						{
							ServerType bladeServer = bladeIter.next();								
							// The node is a compute node
				    		if(bladeServer.getName().toString().matches("HPC_COMPUTE_NODE"))
				    		{
				    			serverMemory = 0;
								// Iteration over the RAMStick objects
				    			Iterator<RAMStickType> iter = bladeServer.getMainboard().get(0).getRAMStick().iterator();
								while(iter.hasNext())
								{
									RAMStickType tempObject =  iter.next();
									serverMemory = serverMemory + tempObject.getSize().getValue();
									bladeServer.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(serverMemory);
									
								}
								  			
			
								try {
									
					    			Iterator<Object> jobIter = bladeServer.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
					    			while(jobIter.hasNext())
					    			{
					    				JobType tempJob =  (JobType) jobIter.next();
					    				serverMemory = serverMemory - tempJob.getNeededMemory().getValue();		  					
					    			}
								}
								catch (NullPointerException e)
								{
									//log.debug("No job ref associated.");
								}
								
								NodeStatusType status = bladeServer.getNativeOperatingSystem().getNode().get(0).getStatus();								
								if(status.toString().matches("RESERVED") || status.toString().matches("POWERING_ON") || status.toString().matches("POWERING_OFF") || status.toString().matches("OFF"))
								{
									//Do Nothing
								}
								else
								{
									tempCluster.clusterSrvList.add(bladeServer);
								}
				    		}
						}
					}
					
					// Iteration over the "RackableServer" objects
					Iterator<RackableServerType> serverIter = rackType.getRackableServer().iterator();
					while(serverIter.hasNext())
					{
						ServerType server = serverIter.next();
						// The node is a compute node
			    		if(server.getName().toString().matches("HPC_COMPUTE_NODE"))
			    		{
			    			serverMemory = 0;
							// Iteration over the RAMStick objects
			    			Iterator<RAMStickType> iter = server.getMainboard().get(0).getRAMStick().iterator();
							while(iter.hasNext())
							{
								RAMStickType tempObject =  iter.next();
								serverMemory = serverMemory + tempObject.getSize().getValue();
								server.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(serverMemory);
								
							}
							  			
		
							try {
								
				    			Iterator<Object> jobIter = server.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
				    			while(jobIter.hasNext())
				    			{
				    				JobType tempJob =  (JobType) jobIter.next();
				    				serverMemory = serverMemory - tempJob.getNeededMemory().getValue();		  					
				    			}
							}
							catch (NullPointerException e)
							{
								
							}
							
							NodeStatusType status = server.getNativeOperatingSystem().getNode().get(0).getStatus();								
							if(status.toString().matches("RESERVED") || status.toString().matches("POWERING_ON") || status.toString().matches("POWERING_OFF") || status.toString().matches("OFF"))
							{
								//Do Nothing
							}
							else
							{
								tempCluster.clusterSrvList.add(server);
							}
							
			    		}
					}
				}       
    		}
			// End: Andre
			
//			for(DatacenterType datacenter : site.getDatacenter())
//			{
//				for(RackType tempRack : datacenter.getRack())
//				{
//					for(ServerType tempServer : tempRack.getRackableServer())
//					{
//						if(tempServer.getName().toString().matches("HPC_COMPUTE_NODE"))
//						{							
//							tempCluster.clusterSrvList.add(tempServer);
//						
//						}
//					}
//				}
//			}
			
			tempCluster.PUE = site.getPUE().getValue();
			tempCluster.name = site.getName();
			if(!(site.getSoftwareLicense().isEmpty()))
			{
				for(SoftwareLicenseType license : site.getSoftwareLicense())
				{
					tempCluster.licenseList.add(license);
				}
			}
			
			clusterList.add(tempCluster);
				
		}
		
		
		// Sort the clusterList with the smallest PUE values coming first
		Collections.sort(clusterList, new ClusterComparator());


		boolean enqueueJob = true;
   	  	
		for(SiteType site : model.getSite())
		{						
			for(DatacenterType datacenter : site.getDatacenter())
			{
				for(RackType tempRack : datacenter.getRack())
				{
					//Andre: Iterating over all ServerType
					//read all potential tempServers
					List <ServerType> tempServers = new ArrayList<ServerType>();

					// Iteration over the "Enclosure" objects
					Iterator<EnclosureType> enclosureIter = tempRack.getEnclosure().iterator();
					while(enclosureIter.hasNext())
					{
						EnclosureType enclosureType = enclosureIter.next();						
						tempServers.addAll(enclosureType.getBladeServer());
					}

					tempServers.addAll(tempRack.getRackableServer());
					//End Andre
					
					for(ServerType tempServer : tempServers)
					//for(ServerType tempServer : tempRack.getRackableServer())
					{
						if(tempServer.getName().toString().matches("HPC_RESOURCE_MANAGEMENT"))
						{
							/// Form temporary data structures for the queue and running jobs
							Iterator<QueueType> queueIterator = ((ServerType)tempServer).getNativeOperatingSystem().getClusterManagement().get(0).getQueue().iterator();
							while(queueIterator.hasNext()){
								QueueType queueType = queueIterator.next();
								Iterator<JobType> itr = queueType.getJobs().iterator();
								while(itr.hasNext()){
									// Retrieve the job from the meta-model and place it into a list
									JobType jobObject = itr.next();			    				
									JobType myJob = (JobType) jobObject.clone();

									//TODO: calculate priority for the job.
									int priority = getPriorityForJob(myJob);
									JobPriorityType jobPriority = new JobPriorityType();

									jobPriority.setValue(priority);
									myJob.setPriority(jobPriority);
									//TODO: check if the job satisfies the queue limits
									//enqueueJob = checkQueueLimits(myJob, queueType);
									if(enqueueJob == true)
									{
										enqueue(jobObject);
										log.debug("Job satisfies queue limits.");
									}
								}
							}
						}
//						else
//						{
//			    			// Create a temporary server structure
//			    			ServerType myServer = tempServer;   			
//							serverMemory = 0;				
//							serverCores = 0;
//							coresInUse = 0;
//							
//							// Iteration over the "CPU" objects
//							Iterator<CPUType> cpuIter = myServer.getMainboard().get(0).getCPU().iterator();
//							while(cpuIter.hasNext())
//							{
//								CPUType cpuObject = cpuIter.next();
//								
//								// Iteration over the "Core" objects
//								Iterator<CoreType> coreIter = cpuObject.getCore().iterator();
//								while(coreIter.hasNext())
//								{
//									CoreType coreObject = coreIter.next();
//									serverCores++;
//								}
//							}
//							
//							coresInUse = myServer.getNativeOperatingSystem().getNode().get(0).getCoresInUse().getValue();
//			    			
//							// Iteration over the RAMStick objects
//			    			Iterator<RAMStickType> iter = myServer.getMainboard().get(0).getRAMStick().iterator();
//							while(iter.hasNext())
//							{
//								RAMStickType tempObject =  iter.next();
//								serverMemory = serverMemory + tempObject.getSize().getValue();
//								tempServer.getNativeOperatingSystem().getNode().get(0).setAvailableMemory(serverMemory);
//								
//							}
//							  			
//		
//							try {
//								
//				    			Iterator<Object> jobIter = myServer.getNativeOperatingSystem().getNode().get(0).getJobRef().getValue().iterator();
//				    			while(jobIter.hasNext())
//				    			{
//				    				JobType tempJob =  (JobType) jobIter.next();
//				    				serverMemory = serverMemory - tempJob.getNeededMemory().getValue();		  					
//				    			}
//							}
//							catch (NullPointerException e)
//							{
//								log.debug("No job ref associated.");
//							}
//									
//							serverMemoryList.add(serverMemory);
//							serverCoresList.add(serverCores);
//							serverCoresInUseList.add(coresInUse);
//							reservedList.add(false);
//							srvList.add(myServer); 
//							
//						}
					}
				}
			}
		}
    	
    }
    
	/**
	 * Checks if enough licenses are available for a job
	 * 
	 */
    
    public boolean isLicensesAvailable(JobType temp_job)
    {
    	boolean available = false;
    	//String siteName = temp_job.getSiteName();
    	String requestedSoftware = temp_job.getRequestedSoftware();
    	
    	//log.debug("requestedSoftware: "+requestedSoftware);
    	//log.debug("siteName: "+siteName);
    	
    	for(int clusterIndex = 0; clusterIndex < clusterList.size(); clusterIndex++)
    	{  		
    		if(!(clusterList.get(clusterIndex).getLicenseList().isEmpty()))
    		{	
				for(int licenseIndex = 0; licenseIndex < clusterList.get(clusterIndex).getLicenseList().size(); licenseIndex++)
				{
					SoftwareLicenseType license = clusterList.get(clusterIndex).getLicenseList().get(licenseIndex);
    				if(license.getName().matches(requestedSoftware))
    				{
    					//log.debug("license matches: "+license.getName());
    					//log.debug("NumberOfAvailable: "+license.getNumberOfAvailable());
    					if(license.getNumberOfAvailable() > 0)
    					{
    						available = true;
    						break;
    					}
    				} 				
				}
    		}
    	}
    	log.debug("License available: "+available);
    	
    	return available;
    }
    
	/**
	 * Decreases number of licenses
	 * 
	 */
	
    public void decreaseLicenses(JobType temp_job)
    {
    	//String siteName = temp_job.getSiteName();
    	String requestedSoftware = temp_job.getRequestedSoftware();
    	for(int clusterIndex = 0; clusterIndex < clusterList.size(); clusterIndex++)
    	{  		
    		if(!(clusterList.get(clusterIndex).getLicenseList().isEmpty()))
    		{	
				for(int licenseIndex = 0; licenseIndex < clusterList.get(clusterIndex).getLicenseList().size(); licenseIndex++)
				{
					SoftwareLicenseType license = clusterList.get(clusterIndex).getLicenseList().get(licenseIndex);
    				if(license.getName().matches(requestedSoftware))
    				{
    					int numberOfLicenses = license.getNumberOfAvailable();
    					license.setNumberOfAvailable((numberOfLicenses-1));
    					break;
    				} 				
				}
    		}
    	}
    }
    
	/**
	 * Calculates a priority value for a job
	 * 
	 */
    
    public int getPriorityForJob(JobType temp_job)
    {
    	int priority = 1;
    	
    	priority = FSWEIGHT * getFSPriority(temp_job) + CREDWEIGHT * getCREDPriority(temp_job)
    		+ SERVWEIGHT * getSERVPriority(temp_job);
    	log.info("Calculated priority of job " + temp_job.getId() + " is: " + priority );	
    	  	
    	return priority;
    }
    
    private int getCREDPriority(JobType temp_job){
    	return 0;
    }
    
    private int getSERVPriority(JobType temp_job){
    	return 0;
    }
    
    private int getFSPriority(JobType temp_job){
    	double fs_priority = 1; 
    	Map<String,Double> old_fs_data_map = fsPolicy.read_old_fs_data();
		Map<String,Double> new_fs_data_map = fsPolicy.read_current_fs_table();
		double total_usage = old_fs_data_map.get("total_users_usage") +  new_fs_data_map.get("total_users_usage");
		String jobOwner = temp_job.getJobOwner();
		String queue = temp_job.getRmsQueueName();
		double new_user_usage = 0.0;
		if(new_fs_data_map.get("user:" + jobOwner)!=null){
			new_user_usage = new_fs_data_map.get("user:" + jobOwner);
		}
		double old_user_usage = 0.0;
		if(old_fs_data_map.get("user:" + jobOwner)!=null){
			old_user_usage = old_fs_data_map.get("user:" + jobOwner);
		}
		double users_usage = (new_user_usage + old_user_usage) / total_usage;
		log.debug("Current usage factor of user " + jobOwner + " is: " + users_usage );	
		
		double new_queue_usage = 0.0;
		if(new_fs_data_map.get("queue:" + queue)!=null){
			new_queue_usage = new_fs_data_map.get("queue:" + queue);
		}
		double old_queue_usage = 0.0;
		if(old_fs_data_map.get("queue:" + queue)!=null){
			old_queue_usage = old_fs_data_map.get("queue:" + queue);
		}
		double queue_usage = (new_queue_usage + old_queue_usage) / total_usage;
		log.debug("Current usage factor of queue " + queue + " is: " + queue_usage );
		
		//FS priority calculation					
		double fs_user_weight = fsPolicy.getFs_user_weight();
		double fs_user_target = fsPolicy.getFs_user_target();
		double fs_queue_weight = fsPolicy.getFs_class_weight();
		double fs_queue_target = fsPolicy.getFs_class_target();
		fs_priority = ((fs_user_weight * (fs_user_target - (users_usage*100))) + fs_queue_weight * (fs_queue_target - (queue_usage*100)));
		log.debug("fs priority is " + fs_priority );
		
		return new Double(fs_priority).intValue();
    }
    
    public boolean checkQueueLimits(JobType myJob, QueueType myQueue)
    {
    	boolean enqueueJob = false;
		int queueNodesLimit = 0;
		int queueCoresLimit = 0;
		long queueWalltimeLimit = 0;
		int jobNodes = 0;
		int jobCores = 0;
		long jobWalltime = 0;
		
		// Queue limits
		queueNodesLimit = myQueue.getNumberOfNodesLimit().getValue();
		queueCoresLimit = myQueue.getNumberOfCoresLimit().getValue();
		queueWalltimeLimit = myQueue.getWallTimeLimit().getValue();
		// Job parameters
		jobNodes = myJob.getNumberOfNodes().getValue();
		jobCores = myJob.getNeededCoresPerNode().getValue();
		jobWalltime = myJob.getWallTime().getValue();
		
		if(jobNodes <= queueNodesLimit && jobCores <= queueCoresLimit && jobWalltime <= queueWalltimeLimit)
		{
			enqueueJob = true;
		}
		
    	
    	return enqueueJob;
    }
	
	public void setModelCopy(FIT4GreenType model)
	{
		this.model = model;
	}
	public FIT4GreenType getModelCopy()
	{
		return this.model;
	}

}
