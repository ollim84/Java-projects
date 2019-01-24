/**
* ============================== Header ============================== 
* file:          Cluster.java
* project:       FIT4Green/Optimizer
* created:       May 8, 2012 by omolli
* last modified: $LastChangedDate: 2010-11-26 12:33:26 +0200 (pe, 26 marras 2010) $ by $LastChangedBy: corentin.dupont@create-net.org $
* revision:      $LastChangedRevision: 150 $
* 
* short description:
*   {To be completed}
* ============================= /Header ==============================
*/
package org.f4g.optimizer.HPC;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.f4g.schema.metamodel.ServerType;
import org.f4g.schema.metamodel.SoftwareLicenseType;

/**
 * {To be completed; use html notation, if necessary}
 * 
 *
 * @author omolli
 */
public class Cluster implements Comparator<Cluster>{

		List<ServerType> clusterSrvList = new LinkedList<ServerType> ();
		double PUE;
		String name;
		List<SoftwareLicenseType> licenseList = new LinkedList<SoftwareLicenseType> ();
double getPUE(){
	return PUE;
}
List<ServerType> getSrvList(){
	return clusterSrvList;
}
String getName(){
	return name;
}

List<SoftwareLicenseType> getLicenseList(){
	return licenseList;
}
/**
* Compare a given Cluster with this object.
* If Cluster PUE of this object is 
* greater than the received object,
* then this object is greater than the other.
*/
public int compare(Cluster p1, Cluster p2) {
    if (p1.getPUE() < p2.getPUE()) return -1;
    if (p1.getPUE() > p2.getPUE()) return 1;
    return 0;
}  

}

