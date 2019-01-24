
package org.f4g.entropy.plan.constraint;

import static choco.cp.solver.CPSolver.*;

import java.util.ArrayList;
import java.util.List;

import choco.Choco;
import choco.kernel.solver.variables.integer.IntDomainVar;
import choco.kernel.solver.variables.set.SetVar;
import entropy.configuration.*;
import entropy.plan.choco.Chocos;
import entropy.plan.choco.ReconfigurationProblem;
import entropy.plan.choco.actionModel.ManageableNodeActionModel;
import entropy.vjob.PlacementConstraint;
import entropy.vjob.builder.protobuf.PBVJob;
import entropy.vjob.builder.xml.XmlVJobSerializer;

/**
 * 
 *
 */
public class SpareCPUs implements PlacementConstraint {

    private ManagedElementSet<Node> nodes;

    //the global spare CPU in number of CPUs
    public int minSpareCPU;


    private static final ManagedElementSet<VirtualMachine> empty = new SimpleManagedElementSet<VirtualMachine>();

    /**
     * Make a new constraint.
     *
     * @param nodes the nodes to put offline if they don't host any running VM.
     */
    public SpareCPUs(ManagedElementSet<Node> nodes, int myMinSpareCPU) {
        this.nodes = nodes;
        minSpareCPU = myMinSpareCPU;
        
    }
    

    @Override
    public void inject(ReconfigurationProblem core) {
    	
    	IntDomainVar[] spareCPU = new IntDomainVar[nodes.size()];
	    
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			SetVar s = core.getSetModel(n);
			IntDomainVar nbVMs = s.getCard();
			
			if (core.getFutureOfflines().contains(n)) {
				spareCPU[i] = core.createIntegerConstant("", 0);
			} else if (core.getFutureOnlines().contains(n)) {
				spareCPU[i] = core.createBoundIntVar("spareCPU" + i, 0, Choco.MAX_UPPER_BOUND);
				core.post(core.eq(spareCPU[i], minus(n.getNbOfCPUs(), nbVMs)));
			} else {
				spareCPU[i] = core.createBoundIntVar("spareCPU" + i, 0,	Choco.MAX_UPPER_BOUND);
				ManageableNodeActionModel a = (ManageableNodeActionModel) core.getAssociatedAction(n);

				IntDomainVar tmpSpare = core.createBoundIntVar("spareCPU", 0, Choco.MAX_UPPER_BOUND);
				core.post(core.eq(tmpSpare, minus(n.getNbOfCPUs(), nbVMs)));
				core.post(core.eq(spareCPU[i], Chocos.mult(core, a.getState(), tmpSpare)));
			}

		}
		core.post(core.leq(minSpareCPU, sum(spareCPU)));
		
    }

    @Override
    public boolean isSatisfied(Configuration cfg) {
    	int spareCPU = 0;
        for (Node n : nodes) {
        	int CPUOccupied = 0;
        	for(VirtualMachine vm : cfg.getRunnings(n)){
        		CPUOccupied += vm.getCPUConsumption();
        	}
        	spareCPU += n.getCPUCapacity() - CPUOccupied;
            
        }
        if (spareCPU < minSpareCPU)
            return false;
        else
        	return true;
    }

    @Override
    public ManagedElementSet<VirtualMachine> getAllVirtualMachines() {
        return empty;
    }

    @Override
    public ManagedElementSet<Node> getNodes() {
        return nodes;
    }

    @Override
    public ManagedElementSet<VirtualMachine> getMisPlaced(Configuration cfg) {
        //All the VMs on the nodes sadly
        ManagedElementSet<VirtualMachine> all = new SimpleManagedElementSet<VirtualMachine>();
        for (Node n : nodes) {
            all.addAll(cfg.getRunnings(n));
        }
        return all;
    }

    @Override
    public String toXML() {
        StringBuilder b = new StringBuilder();
        b.append("<constraint id=\"SpareCPUs\">");
        b.append("<params>");
        b.append("<param>").append(XmlVJobSerializer.getNodeset(nodes)).append("</param>");
        b.append("</params>");
        b.append("</constraint>");
        return b.toString();
    }

    @Override
    public PBVJob.vjob.Constraint toProtobuf() {
        
        return null;
    }

    @Override
    public Type getType() {
        return Type.relative;
    }

    @Override
    public String toString() {
        return new StringBuilder("SpareCPUs(").append(nodes).append(", ").append(minSpareCPU).append("%)").toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SpareCPUs q = (SpareCPUs) o;

        return nodes.equals(q.nodes);
    }

    @Override
    public int hashCode() {
        return "noIdleOnline".hashCode() * 31 + nodes.hashCode();
    }
}
