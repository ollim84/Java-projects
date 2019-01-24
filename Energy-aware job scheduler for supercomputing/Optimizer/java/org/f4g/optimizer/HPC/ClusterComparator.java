/**
* ============================== Header ============================== 
* file:          ClusterComparator.java
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

/**
 * {To be completed; use html notation, if necessary}
 * 
 *
 * @author omolli
 */
public class ClusterComparator  implements Comparator<Cluster>{
    public int compare(Cluster p1, Cluster p2) {
        if (p1.getPUE() < p2.getPUE()) return -1;
        if (p1.getPUE() > p2.getPUE()) return 1;
        return 0;
    } 
}
