/**
 * ============================== Header ============================== 
 * file:          SLAReader.java
 * project:       FIT4Green/Optimizer
 * created:       15 déc. 2010 by cdupont
 * last modified: $LastChangedDate: 2012-04-27 14:52:52 +0200 (Fri, 27 Apr 2012) $ by $LastChangedBy: f4g.cnit $
 * revision:      $LastChangedRevision: 1383 $
 * 
 * short description:
 *   Reads and validate the SLA instance file.
 * ============================= /Header ==============================
 */
package org.f4g.optimizer.HPC;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.ValidationEventLocator;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.log4j.Logger;
import org.f4g.schema.constraints.optimizerconstraints.FIT4GreenHPCOptimizerConstraint;
import org.f4g.schema.constraints.optimizerconstraints.HPCPolicyType;
import org.f4g.schema.constraints.optimizerconstraints.LoadType;
import org.f4g.schema.constraints.optimizerconstraints.PeriodType;
import org.f4g.schema.constraints.optimizerconstraints.RepeatsType;

/**
 * {To be completed; use html notation, if necessary}
 * 
 * 
 * @author cdupont
 */
public class SLAReader {

	public static Logger log = Logger.getLogger(SLAReader.class.getName());
	public FIT4GreenHPCOptimizerConstraint SLA; 
	
	/**
	 * get SLA from file input: the SLA path
	 * 
	 */
	public SLAReader(String SLAPathName) {

		SLA = readSLA(SLAPathName);

		if (SLA == null)
			log.error("No SLA instance file found or wrong instance");
	}

	/**
	 * get Policies from a SLA file
	 */
	public HPCPolicyType getPolicies() {
		if (SLA != null)
			return SLA.getListOfPolicies();
		else {
			log.error("No SLA instance file found or wrong instance");
			return null;
		}
	}
	
	/**
	 * loads a SLA file
	 * 
	 * @author cdupont
	 */
	public FIT4GreenHPCOptimizerConstraint readSLA(String SLAPathName) {

		InputStream isSLA = this.getClass().getClassLoader()
				.getResourceAsStream(SLAPathName);

		log.debug("SLAPathName: " + SLAPathName + ", isModel: " + isSLA);

		// JAXBElement<FIT4GreenOptimizerConstraint> poElement = null;
		try {
			// create an Unmarshaller
			Unmarshaller u = JAXBContext.newInstance(
					"org.f4g.schema.constraints.optimizerconstraints")
					.createUnmarshaller();

			// ****** VALIDATION ******
			// TODO: remove hard path
			URL url = this.getClass().getClassLoader()
					.getResource("schema/SLASupercomputingConstraints.xsd");

			log.debug("URL: " + url);

			SchemaFactory sf = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
			try {
				Schema schema = sf.newSchema(url);
				u.setSchema(schema);
				u.setEventHandler(new ValidationEventHandler() {
					// allow unmarshalling to continue even if there are errors
					public boolean handleEvent(ValidationEvent ve) {
						// ignore warnings
						if (ve.getSeverity() != ValidationEvent.WARNING) {
							ValidationEventLocator vel = ve.getLocator();
							log.warn("Line:Col[" + vel.getLineNumber() + ":"
									+ vel.getColumnNumber() + "]:"
									+ ve.getMessage() + "\nURL: "
									+ vel.getURL() + " Name:"
									+ vel.getObject().getClass().getName()
									+ " Node:" + vel.getNode().getNodeName());
						}
						return true;
					}
				});
			} catch (org.xml.sax.SAXException se) {
				log.error("Unable to validate due to following error: ", se);
			}
			// *********************************

			// unmarshal an XML document into a tree of Java content
			// objects composed of classes from the "org.f4g.schema" package.

			// poElement = (JAXBElement<FIT4GreenOptimizerConstraint>)
			// u.unmarshal(isSLA);
			return (FIT4GreenHPCOptimizerConstraint) u.unmarshal(isSLA);

			// return (FIT4GreenOptimizerConstraint) poElement.getValue();

		} catch (JAXBException je) {
			log.error(je);
			return null;
		}

	}

	
	/**
	 * Scan PeriodVMThreshold policies to find out low and high VM slots thresholds for specific date
	 * 
	 * @param currentDate date to search for
	 * @param policy policy associated to the cluster at hand
	 * @return low and high VM slots thresholds for currentDate or null if no load found
	 * 
	 * @author VG
	 */
	public static LoadType getVMSlotsThreshold (Date currentDate, List<PeriodType> periods) {
		
		log.debug("Current date is: " + currentDate);
		
		LoadType load = new LoadType();
						
		if (periods.size() == 0) {
			log.warn("No periods found in policy");
		}
			
		for (int j=0; j<periods.size(); j++) {
			
			if ((periods.get(j).getStarts() != null) && (periods.get(j).getEnds() != null)) {
				
				Date startDate = periods.get(j).getStarts().toGregorianCalendar().getTime();
				Date endDate = periods.get(j).getEnds().toGregorianCalendar().getTime();
				
				if (startDate.after(endDate)) {
					log.error("Start date: " + startDate + ", after end date: " + endDate);
					return null;
				}
				
				if ((periods.get(j).getDuration() != null) && (periods.get(j).getRepeats() != null)) {
								
					Date currentStartDate = startDate;
								
					while (currentStartDate.before(endDate)) {
									
						// Update current end date by adding duration
						Date currentEndDate = new Date(currentStartDate.getTime());
						periods.get(j).getDuration().addTo(currentEndDate);
						
						log.debug("Current start date: " + currentStartDate);
									
						log.debug("Current end date: " + currentEndDate);
									
						// Check for load (start date included, end date excluded)
						if (currentDate.after(currentStartDate) && currentDate.before(currentEndDate)
						|| currentDate.equals(currentStartDate)	) {
							load = periods.get(j).getLoad();
							// highly unlikely to be null since this element is mandatory
							return load;
						}
					
						// Update current start date by checking REPEATS field
						GregorianCalendar calendar = new GregorianCalendar();
						calendar.setTimeInMillis(currentStartDate.getTime());
					
						String repeatString = periods.get(j).getRepeats().value();	
					
						if (repeatString.compareTo(RepeatsType.DAILY.toString()) == 0)  {
							calendar.add(GregorianCalendar.DAY_OF_MONTH,1);
							currentStartDate = calendar.getTime();
						} else if (repeatString.compareTo(RepeatsType.WEEKLY.toString()) == 0) {
							calendar.add(GregorianCalendar.DAY_OF_MONTH,7);
							currentStartDate = calendar.getTime();
						} else if (repeatString.compareTo(RepeatsType.MONTHLY.toString()) == 0) {
							calendar.add(GregorianCalendar.MONTH,1);
							currentStartDate = calendar.getTime();
						} else if (repeatString.compareTo(RepeatsType.YEARLY.toString()) == 0) {
							calendar.add(GregorianCalendar.YEAR,1);
							currentStartDate = calendar.getTime();
						} else {
							log.warn("Invalid REPEATS type; jump to end date");
							currentStartDate.setTime(endDate.getTime());
						}
					}
				} else {
					log.debug("Only start and end dates are considered; start date: " + startDate 
							+ ", end date: " + endDate);
				
					if (currentDate.after(startDate) && currentDate.before(endDate)) {
						load = periods.get(j).getLoad();
						// highly unlikely to be null since this element is mandatory
						return load;
					}
				}
			} else {
				log.debug("No start or end date: policy always applicable");
				load = periods.get(j).getLoad();
				// highly unlikely to be null since this element is mandatory
				return load;
			}
		}
		return null;
		
	}
	
}
