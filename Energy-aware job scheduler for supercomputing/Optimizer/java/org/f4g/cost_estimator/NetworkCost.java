/**
 * org.f4g.cost_estimator/NetworkCost.java
 *
 * Ricardo Lent
 */

package org.f4g.cost_estimator;
import java.util.*;

import static javax.measure.units.NonSI.*;
import static javax.measure.units.SI.*;
import org.jscience.physics.measures.Measure;
import org.jscience.economics.money.*;
import org.jscience.economics.money.Currency;
import javax.measure.quantities.*;

import org.f4g.schema.metamodel.FIT4GreenType;
import org.f4g.schema.metamodel.ServerStatusType;
import org.f4g.schema.metamodel.ServerType;
import org.f4g.schema.metamodel.NetworkNodeType;
import org.f4g.schema.metamodel.NetworkPortType;
import org.f4g.schema.metamodel.VirtualMachineType;
import org.f4g.schema.metamodel.SiteType;
import org.f4g.optimizer.ICostEstimator;
import org.f4g.power.IPowerCalculator;
import org.f4g.power.PoweredNetworkNode;
import org.f4g.optimizer.OptimizationObjective;
import org.f4g.optimizer.utils.Utils;
import org.f4g.optimizer.CloudTraditional.SLAReader;
import org.f4g.schema.constraints.optimizerconstraints.VMTypeType;
import org.f4g.util.Util;

/*
 Things to improve:
 
 - pass desired proto
 - 
 
 
 
 
 */



/**
 * class NetworkCost: estimates network energy costs for both inter and intra site
 * 
 */

public class NetworkCost implements ICostEstimator {
    
    public class Protocol extends Object {
        public static final int SSH = 0;
        public static final int HTTP = 1;
        public static final int UDT = 2;
    }
    
    private int defaultNetworkProto = NetworkCost.Protocol.SSH;
    private int defaultPacketSize = 512;
    private OptimizationObjective optiObjective = OptimizationObjective.Power;
    private VMTypeType currentVMType;
   
    
    public NetworkCost(OptimizationObjective optObj, VMTypeType vmType) {
        super();
        optiObjective = optObj;
        currentVMType = vmType;
    }

    //TODO: remove this (broken) constructor
    public NetworkCost() {
        super();
    }
	public boolean dispose() {
        return true;
    }
    
    
	/**
	 * Calculates the energy used for moving a VM from one server to another
     *
     * Assumes:
     *
     *     - end hosts have a single network port
     *
     *     - 
     *
	 * 
	 * @param the origin server, the destination server, the VM to move, the complete model
	 * @return the energy
	 */
	public Measure<Energy> moveEnergyCost(NetworkNodeType srcServer, NetworkNodeType dstServer, VirtualMachineType VM, FIT4GreenType model) 
    {  
        ArrayList<NetworkNodeType> route = calculateRoute(srcServer, dstServer, model);   
        double throughput = estimateThroughput(route);   
        double nbytes = 0.;
        
        VMTypeType.VMType SLA_VM = null;
		if(VM.getActualStorageUsage() == null || VM.getActualMemoryUsage() == null) {
			SLA_VM = Util.findVMByName(VM.getCloudVmType(), currentVMType);	
            nbytes = SLA_VM.getExpectedLoad().getVRamUsage().getValue() + SLA_VM.getCapacity().getVHardDisk().getValue();       // check units 
        }
        else {
            nbytes = VM.getActualStorageUsage().getValue() + VM.getActualMemoryUsage().getValue();
        }

        Measure<Duration> xfer_time = estimateXferTime(throughput, nbytes);   
        Measure<Power> totalPower = calculatePowerNetwork(route, throughput, model).plus( calculatePowerEndHosts(srcServer, dstServer, throughput, model) );

        return Measure.valueOf(totalPower.doubleValue(WATT) * xfer_time.doubleValue(SECOND), JOULE);        
    }
    
    
    public Measure<Duration> 
    moveDownTimeCost(NetworkNodeType srcServer, NetworkNodeType dstServer, VirtualMachineType VM, FIT4GreenType model)
    {
        ArrayList<NetworkNodeType> route = calculateRoute(srcServer, dstServer, model);
        double throughput = estimateThroughput(route);
        double nbytes = 0.;
        
        VMTypeType.VMType SLA_VM = null;
		if(VM.getActualStorageUsage() == null || VM.getActualMemoryUsage() == null) {
//		if(VM.getCloudVmType() != null) {
			SLA_VM = Util.findVMByName(VM.getCloudVmType(), currentVMType);	
            nbytes = SLA_VM.getExpectedLoad().getVRamUsage().getValue() + SLA_VM.getExpectedLoad().getVDiskLoad().getValue();
		}
        else {
            nbytes = VM.getActualStorageUsage().getValue() + VM.getActualMemoryUsage().getValue();
        }
     
        return estimateXferTime(throughput, nbytes);
    }
    
    
    public Measure<Money> moveFinancialCost(NetworkNodeType srcServer, NetworkNodeType dstServer, VirtualMachineType VM, FIT4GreenType model) {
        
        Measure<Money> money = Measure.valueOf(0.0, Currency.EUR);
        return money;
    }
    
    
    // ========================================================================================================
    
    
    protected Measure<Duration> 
    estimateXferTime(double throughput, double nbytes) 
    {
        double xfer_time = 0.0;
        
        if( throughput > 0.0 )
                xfer_time = (nbytes * estimateProtocolOverhead()) / throughput;
                
        return Measure.valueOf(xfer_time, SECOND);    
    }
    
    
    protected Measure<Power> 
    calculatePowerEndHosts(NetworkNodeType srcServer, NetworkNodeType dstServer, double throughput, FIT4GreenType model)
    {
        Measure<Power> total = Measure.valueOf(0.0, WATT);
        List<NetworkPortType> sportlst = srcServer.getNetworkPort();
        List<NetworkPortType> dportlst = dstServer.getNetworkPort();
        
        if( sportlst.size() > 0 && dportlst.size() > 0 ) {
            
            NetworkPortType sport = sportlst.get(0);
            NetworkPortType dport = dportlst.get(0);            
            
            SiteType ssite = null, dsite = null;
            
            if(model != null) {
                ssite = Utils.getNetworkNodeSite(srcServer, model);
                dsite = Utils.getNetworkNodeSite(dstServer, model);
            }
            
            // the power consumption at the end hosts for 
            
            
            double s_idle   = srcServer.getPowerIdle().getValue();
            double s_max    = srcServer.getPowerMax().getValue();
            double s_ppsmax = srcServer.getProcessingBandwidth().getValue();
            double d_idle   = dstServer.getPowerIdle().getValue();
            double d_max    = dstServer.getPowerMax().getValue();
            double d_ppsmax = dstServer.getProcessingBandwidth().getValue();
            
            double spower = PoweredNetworkNode.trafficToPower(s_idle, s_max, throughput, s_ppsmax).doubleValue(WATT) - s_idle;
            double dpower = PoweredNetworkNode.trafficToPower(d_idle, d_max, throughput, d_ppsmax).doubleValue(WATT) - d_idle;
            
            if( ssite != null ) {
                double f;
            	if(optiObjective == OptimizationObjective.Power) 
            		f = ssite.getPUE().getValue();
            	else 
            		f = ssite.getCUE().getValue();
                spower = spower * f;
            }
            
            if( dsite != null ) {
                double f;
            	if(optiObjective == OptimizationObjective.Power) 
            		f = dsite.getPUE().getValue();
            	else 
            		f = dsite.getCUE().getValue();
                dpower = dpower * f;
            }
            
            total = Measure.valueOf(spower + dpower, WATT);
        }
        
        return total;
    }
    
    
    
    protected Measure<Power> 
    calculatePowerNetwork(ArrayList<NetworkNodeType> route, double throughput, FIT4GreenType model)
    {
        Measure<Power> total = Measure.valueOf(0.0, WATT);
        
        if( route.size() > 2 ) {
            // add network cost
            for(int i=1; i<(route.size()-1); i++) {
                NetworkNodeType node = route.get(i);
                double a = node.getPowerIdle().getValue(), b = node.getPowerMax().getValue(), c = node.getProcessingBandwidth().getValue();                
                Measure<Power> node_pwr = PoweredNetworkNode.trafficToPower( a, b, throughput, c ).minus(PoweredNetworkNode.trafficToPower( a, b, 0, c ));
                
                
                SiteType site = Utils.getNetworkNodeSite(node, model);
                if( site != null ) {
                    double f;
                    if(optiObjective == OptimizationObjective.Power) 
                        f = site.getPUE().getValue();
                    else 
                        f = site.getCUE().getValue();
                    node_pwr = node_pwr.times(f);
                }
                total = total.plus( node_pwr );
           }
        }
        
        return total;        
    }
    
    
    // ========================================================================================================
    
    
    protected double 
    estimateThroughput(ArrayList<NetworkNodeType> route)            
    {
        double throughput = 0.;
        if( route.size() <= 0 ) return 0.;
        
        // System.out.println( ">>>>" + route.get(0).getNetworkPort().size() );
        
        throughput = route.get(0).getNetworkPort().get(0).getLineCapacity().getValue() / 8.0;
        
        for(NetworkNodeType node : route) {
            double prate = node.getNetworkPort().get(0).getLineCapacity().getValue();
            if( prate < throughput ) throughput = prate;                                        // PENDING: Replace with residual bandwidth 
        }
        
        if( defaultNetworkProto != NetworkCost.Protocol.UDT )
            throughput *= 0.75;     // approx for TCP xfers
        
        return throughput;
    }
    
    
    protected double 
    estimateProtocolOverhead() {
        
        double data_pktlen = defaultPacketSize+14+20+20;	// MSS + ETH header + IP header + TCP header    -- in bytes
        // double ack_pktlen = 64;		// in bytes
        
        if( defaultNetworkProto == NetworkCost.Protocol.SSH )
            data_pktlen += 6;                   // from  measurements
        else
            if( defaultNetworkProto == NetworkCost.Protocol.HTTP )
                data_pktlen += 10;              // needs to be validated
            else                
                data_pktlen -= 12;              // replace TCP header with UDP header
        
        return ((double) data_pktlen)/defaultPacketSize;
    }                
    
    
    // ========================================================================================================
    
    protected ArrayList<NetworkNodeType>
    calculateRoute(NetworkNodeType srcServer, NetworkNodeType dstServer, FIT4GreenType model)
    {  
        boolean found = false;
        LinkedList<NetworkNodeType> q  = new LinkedList();
        Map<NetworkNodeType, NetworkNodeType> predecessor = new HashMap<NetworkNodeType, NetworkNodeType>();
        //System.out.println("S: " + srcServer.getFrameworkID() + " " + dstServer.getFrameworkID() );
        // traverse graph
        q.addLast( srcServer );
        predecessor.put(srcServer, srcServer);

       	if(model!=null){
        while( q.size() > 0 ) {          
            NetworkNodeType node = q.removeFirst();        
            System.out.println( "-> " + node.getFrameworkID() );          
            if( node.getFrameworkID() == dstServer.getFrameworkID() ) {
                System.out.println( "found" );
                found = true;
                break;
            }
            else {
               List<NetworkPortType> portlist = node.getNetworkPort();
                if( portlist == null ) {
                    //System.out.println( "no route" );
                    return new ArrayList();     // no route
                }
                for(NetworkPortType port : portlist) {                
                    NetworkNodeType neighbor = new NetworkNodeType();                  
                    if( port.getNetworkPortRef() != null ) {
                        try {
                            ServerType srv = (ServerType) Utils.findServerByName(model, (String) port.getNetworkPortRef());
                            neighbor = (NetworkNodeType) (srv.getMainboard().get(0).getEthernetNIC().get(0));
                            }
                        catch (NoSuchElementException e) {
                        	neighbor = (NetworkNodeType) Utils.findNetworkNodeByName(model, (String) port.getNetworkPortRef());
                        }
                        System.out.println( "neighbor: " + neighbor.getFrameworkID() );
                    }
                    else {
                        System.out.println( "no route" );
                        return new ArrayList();     // no route
                    }
                    if( ! predecessor.containsKey(neighbor) ) {
                        q.addLast( neighbor );
                        predecessor.put(neighbor, node);
                    }
                }
                
            }
        }
    }       
        
        // prepare return structure
        
        LinkedList<NetworkNodeType> route = new LinkedList();
        if( found ) {
            NetworkNodeType node = dstServer;
            while( ! node.equals( srcServer ) ) {
                route.addFirst(node);
                node = predecessor.get( node );
            }
            route.addFirst(srcServer);          
        }   
        else {  // unknown interconnection network
            route.addLast( srcServer );
            route.addLast( dstServer );
        }
        
        return new ArrayList(route);
    }
    
    
    
    
    
}


