/**
* ============================== Header ============================== 
* file:          OptimizerTest.java
* project:       FIT4Green/Optimizer
* created:       10 déc. 2010 by cdupont
* last modified: $LastChangedDate: 2012-05-01 01:59:19 +0300 (ti, 01 touko 2012) $ by $LastChangedBy: f4g.cnit $
* revision:      $LastChangedRevision: 1406 $
* 
* short description:
*   Optimizer mother class for tests
* ============================= /Header ==============================
*/
package org.f4g.test;


import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;

import javax.measure.quantities.Duration;
import javax.measure.quantities.Energy;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.f4g.com.util.PowerData;
import org.f4g.controller.IController;
import org.f4g.optimizer.ICostEstimator;
import org.f4g.optimizer.CloudTraditional.OptimizerEngineCloudTraditional;
import org.f4g.optimizer.utils.Utils;
import org.f4g.power.IPowerCalculator;
import org.f4g.schema.metamodel.CPUType;
import org.f4g.schema.metamodel.CoreType;
import org.f4g.schema.metamodel.DatacenterType;
import org.f4g.schema.metamodel.FIT4GreenType;
import org.f4g.schema.metamodel.FanType;
import org.f4g.schema.metamodel.HardDiskType;
import org.f4g.schema.metamodel.MainboardType;
import org.f4g.schema.metamodel.NASType;
import org.f4g.schema.metamodel.NetworkNodeType;
import org.f4g.schema.metamodel.OperatingSystemTypeType;
import org.f4g.schema.metamodel.RAIDType;
import org.f4g.schema.metamodel.RackType;
import org.f4g.schema.metamodel.ServerType;
import org.f4g.schema.metamodel.SiteType;
import org.f4g.schema.metamodel.SolidStateDiskType;
import org.f4g.schema.metamodel.VirtualMachineType;
import org.f4g.schema.actions.AbstractBaseActionType;
import org.f4g.schema.actions.ActionRequestType;
import org.f4g.schema.actions.LiveMigrateVMActionType;
import org.f4g.schema.actions.MoveVMActionType;
import org.f4g.schema.actions.PowerOffActionType;
import org.f4g.schema.actions.PowerOnActionType;
import org.f4g.schema.allocation.AllocationRequestType;
import org.f4g.schema.allocation.CloudVmAllocationType;
import org.f4g.schema.allocation.ObjectFactory;
import org.f4g.schema.allocation.TraditionalVmAllocationType;
import org.f4g.schema.constraints.optimizerconstraints.BoundedClustersType;
import org.f4g.schema.constraints.optimizerconstraints.BoundedPoliciesType;
import org.f4g.schema.constraints.optimizerconstraints.BoundedSLAsType;
import org.f4g.schema.constraints.optimizerconstraints.ClusterType;
import org.f4g.schema.constraints.optimizerconstraints.FederationType;
import org.f4g.schema.constraints.optimizerconstraints.NodeControllerType;
import org.f4g.schema.constraints.optimizerconstraints.PolicyType;
import org.f4g.schema.constraints.optimizerconstraints.SLAType;
import org.f4g.schema.constraints.optimizerconstraints.ClusterType.Cluster;
import org.jscience.economics.money.Money;
import org.jscience.physics.measures.Measure;


import junit.framework.TestCase;
import static javax.measure.units.SI.*;


/**
 * {To be completed; use html notation, if necessary}
 * @author  cdupont
 */
public class OptimizerTest extends TestCase {

	public Logger log;  
	
	//this actionRequest is filled by the MockController after being called. 
	protected ActionRequestType actionRequest = null;
	protected final Semaphore actionRequestAvailable = new Semaphore(1);
	
	/**
	 * @uml.property  name="optimizer"
	 * @uml.associationEnd  
	 */
	OptimizerEngineCloudTraditional optimizer = null;

	protected XMLGregorianCalendar begin;

	protected XMLGregorianCalendar end;
	
	//protected abstract OptimizerEngine getOptimizer();
	
	/**
	 * Mocked controller to be passed to the optimizer
	 *
	 * @author cdupont
	 */
	protected class MockController implements IController{

		@Override
		public boolean executeActionList(ActionRequestType myActionRequest) {
			actionRequest = myActionRequest;
			actionRequestAvailable.release();
			
			for (JAXBElement<? extends AbstractBaseActionType> action : myActionRequest.getActionList().getAction()){
				if (action.getValue() instanceof PowerOnActionType) {
					PowerOnActionType on = (PowerOnActionType)action.getValue();
					log.debug("executeActionList: power ON on :" + on.getNodeName());
				}
				if (action.getValue() instanceof PowerOffActionType) {
					PowerOffActionType off = (PowerOffActionType)action.getValue();
					log.debug("executeActionList: power OFF on :" + off.getNodeName());
				}
				if (action.getValue() instanceof MoveVMActionType) {
					MoveVMActionType move = (MoveVMActionType)action.getValue();
					log.debug("executeActionList: move VM " + move.getVirtualMachine() + " from " + move.getSourceNodeController() + " to " + move.getDestNodeController());
				}
				if (action.getValue() instanceof LiveMigrateVMActionType) {
					LiveMigrateVMActionType move = (LiveMigrateVMActionType)action.getValue();
					log.debug("executeActionList: live migrate VM " + move.getVirtualMachine() + " from " + move.getSourceNodeController() + " to " + move.getDestNodeController());
				}
			}
			
			log.debug("executeActionList: ComputedPowerBefore = " + myActionRequest.getComputedPowerBefore());
			log.debug("executeActionList: ComputedPowerAfter = " + myActionRequest.getComputedPowerAfter());
			
			return true;
		}

		@Override
		public boolean dispose() {
			return false;
		}

		/* (non-Javadoc)
		 * @see org.f4g.controller.IController#setActionsApproved(boolean)
		 */
		@Override
		public void setActionsApproved(boolean actionsApproved) {
			// TODO Auto-generated method stub
			
		}
		
		/* (non-Javadoc)
		 * @see org.f4g.controller.IController#setActionsApproved(boolean)
		 */
		@Override
		public void setApprovalSent(boolean actionsApproved) {
			// TODO Auto-generated method stub
			
		}	
				
	}
	
	/**
	 * Mocked power calculator to be passed to the optimizer
	 *
	 * @author cdupont
	 */
	protected class MockPowerCalculator implements IPowerCalculator{

		PowerCalculatorTraverser traverser = new PowerCalculatorTraverser();
		
		@Override public PowerData computePowerFIT4Green(FIT4GreenType model) {
			return traverser.calculatePower(model);
		}
		
		@Override public boolean dispose() {
			return true;
		}


		@Override public PowerData computePowerCPU(CPUType cpu,
				OperatingSystemTypeType operatingSystem) {
			return traverser.calculatePower(cpu);
		}


		@Override public PowerData computePowerDatacenter(DatacenterType datacenter) {
			return traverser.calculatePower(datacenter);
		}

		@Override public PowerData computePowerFAN(FanType fan) {
			return traverser.calculatePower(fan);
		}

		
		@Override public PowerData computePowerHardDisk(HardDiskType hardDisk) {
			return traverser.calculatePower(hardDisk);
		}

		@Override public PowerData computePowerMainboard(MainboardType mainboard,
				OperatingSystemTypeType operatingSystem) {
			return traverser.calculatePower(mainboard);
		}

		@Override public PowerData computePowerMainboardRAMs(MainboardType mainboard) {
			return traverser.calculatePower(mainboard);
		}

		@Override public PowerData computePowerRAID(RAIDType raid) {
			return traverser.calculatePower(raid);
		}

		@Override public PowerData computePowerRack(RackType rack) {
			return traverser.calculatePower(rack);
		}

		@Override public PowerData computePowerServer(ServerType server) {
			return traverser.calculatePower(server);
		}

		@Override public PowerData computePowerSite(SiteType site) {
			return traverser.calculatePower(site);
		}

		@Override public PowerData computePowerSolidStateDisk(SolidStateDiskType ssdisk) {
			return traverser.calculatePower(ssdisk);
		}

		@Override
		public PowerData computePowerCore(CoreType myCore, CPUType cpu,
				OperatingSystemTypeType operatingSystem) {
			return traverser.calculatePower(myCore);
		}

		@Override
		public PowerData computePowerSAN(RackType obj) {
			return traverser.calculatePower(obj);
		}

		@Override
		public PowerData computePowerIdleSAN(RackType obj) {
			return traverser.calculatePower(obj);
		}

		@Override
		public boolean getSimulationFlag() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void setSimulationFlag(boolean f) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public PowerData computePowerIdleNAS(NASType obj) {
			return traverser.calculatePower(obj);
		}

		@Override
		public PowerData computePowerNAS(NASType obj) {
			return traverser.calculatePower(obj);
		}
	}
	
	/**
	 * Mocked cost estimator to be passed to the optimizer
	 *
	 * @author cdupont
	 */
	protected class MockNetworkCost implements ICostEstimator {


		@Override
		public boolean dispose() {
			return false;
		}

		@Override
		public Measure<Duration> moveDownTimeCost(NetworkNodeType fromServer,
				NetworkNodeType toServer, VirtualMachineType VM,
				FIT4GreenType model) {
			return null;
		}

		@Override
		public Measure<Energy> moveEnergyCost(NetworkNodeType fromServer,
				NetworkNodeType toServer, VirtualMachineType VM,
				FIT4GreenType model) {
			
			return Measure.valueOf(100, JOULE);
		}


		@Override
		public Measure<Money> moveFinancialCost(NetworkNodeType fromServer,
				NetworkNodeType toServer, VirtualMachineType VM,
				FIT4GreenType model) {
			return null;
		}
		
	}
	
	/**
	 * Construction of the optimizer
	 *
	 * @author cdupont
	 */
	protected void setUp() throws Exception {
		super.setUp();

		begin = DatatypeFactory.newInstance().newXMLGregorianCalendarDate(2010, 1, 1, 0);
		end = DatatypeFactory.newInstance().newXMLGregorianCalendarDate(2020, 1, 1, 0);
		
		Properties log4jProperties = new Properties();
		if(System.getProperty("log4j.configuration") != null){
			PropertyConfigurator.configure(System.getProperty("log4j.configuration"));				
		} else {
			InputStream isLog4j = this.getClass().getClassLoader().getResourceAsStream("config/log4j.properties");
			log4jProperties.load(isLog4j);
			PropertyConfigurator.configure(log4jProperties);
			System.out.println("logger f4g:" + log4jProperties.getProperty("log4j.logger.org.f4g"));
		}
			
		log = Logger.getLogger(this.getClass().getName()); 
		
		actionRequestAvailable.acquire();	    
	}



	/**
	 * Destruction
	 * 
	 * @author cdupont
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		
	}

	
	/**
	 * helper function
	 */
	protected AllocationRequestType createAllocationRequestCloud(String VMType) {
		
		AllocationRequestType request = new AllocationRequestType();
		
		CloudVmAllocationType alloc = new CloudVmAllocationType();
		alloc.setVmType(VMType);
		alloc.setImageId("");
		alloc.getClusterId().add("c1");
		
		
		//Simulates a CloudVmAllocationType operation
		JAXBElement<CloudVmAllocationType>  operationType = (new ObjectFactory()).createCloudVmAllocation(alloc);
	
		request.setRequest(operationType);
		
		return request;
	}

	/**
	 * helper function
	 */
	protected AllocationRequestType createAllocationRequestTrad() {
		
		AllocationRequestType request = new AllocationRequestType();
		
		TraditionalVmAllocationType alloc = new TraditionalVmAllocationType();
		//cloudAlloc.
		alloc.getClusterId().add("c1");
		alloc.setNumberOfCPUs(1);
		alloc.setCPUUsage(100.0);
		alloc.setDiskIORate(0.0);
		alloc.setMemoryUsage(0.0);
		alloc.setNetworkUsage(0.0);
		alloc.setStorageUsage(0.0);
		
		
		
		//Simulates a CloudVmAllocationType operation
		JAXBElement<TraditionalVmAllocationType>  operationType = (new ObjectFactory()).createTraditionalVmAllocation(alloc);
	
		request.setRequest(operationType);
		
		return request;
	}
	
	protected SLAType createDefaultSLA(){
		SLAType slas = new SLAType();
		SLAType.SLA sla = new SLAType.SLA();
		
		slas.getSLA().add(sla);
		return slas;
	}
	
	protected ClusterType createDefaultCluster(int NumberOfNodes, SLAType.SLA sla, PolicyType policy) {
	
		List<String> nodeName = new ArrayList<String>();
		for(int i=0; i<NumberOfNodes; i++){
			nodeName.add("id" + i*100000 );	
		}		
		List<Cluster> cluster = new ArrayList<ClusterType.Cluster>();

		BoundedSLAsType bSlas = new BoundedSLAsType();
		bSlas.getSLA().add(new BoundedSLAsType.SLA(sla));	
		
		BoundedPoliciesType bPolicies = new BoundedPoliciesType();
		bPolicies.getPolicy().add(new BoundedPoliciesType.Policy(policy.getPolicy().get(0)));	
		
		cluster.add(new Cluster("c1", new NodeControllerType(nodeName) , bSlas, bPolicies, "id"));
		return new ClusterType(cluster);
	}
	

	protected FederationType makeSimpleFed(PolicyType policies, FIT4GreenType f4g) {
				
		FederationType fed = new FederationType();
		BoundedPoliciesType.Policy bpol = new BoundedPoliciesType.Policy(policies.getPolicy().get(0));
		BoundedPoliciesType bpols = new BoundedPoliciesType();
		bpols.getPolicy().add(bpol);		
		fed.setBoundedPolicies(bpols);
		
		if(f4g != null) {
			//add all servers in one cluster
	
			BoundedClustersType bcls = new BoundedClustersType();
			ClusterType.Cluster c = new ClusterType.Cluster();
			c.setNodeController(new NodeControllerType());
			c.setName("c1");
			for(ServerType s : Utils.getAllServers(f4g)) {
			    c.getNodeController().getNodeName().add(s.getFrameworkID());
			}
			BoundedClustersType.Cluster bcl = new BoundedClustersType.Cluster();
			bcl.setIdref(c);
			bcls.getCluster().add(bcl);
			fed.setBoundedCluster(bcls);
		}	
		
		return fed;

	}
	
}
