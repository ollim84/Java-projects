/**
* ============================== Header ============================== 
* file:          FairsharePolicy.java
* project:       FIT4Green/CommunicatorFzj
* created:       04.05.2012 by agiesler
* 
* $LastChangedDate:$ 
* $LastChangedBy:$
* $LastChangedRevision:$
* 
* short description:
*   {To be completed}
* ============================= /Header ==============================
*/
package org.f4g.optimizer.HPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * {To be completed; use html notation, if necessary}
 * 
 *
 * @author agiesler
 */
public class FairshareTableMgmt {
	
static Logger log = Logger.getLogger(FairshareTableMgmt.class.getName());
	
	//public static final String COM_PROPERTIES_DIR = "fairshare_tables/";
	public static final String COM_PROPERTIES_SUFFIX = ".properties";
	
	int depth = 7;
	int time_frame = 3;
	int current_depth = 1;
	String interval;
	String fsType;
	double decay;
	
	int fs_user_weight;
	int fs_class_weight;
	double fs_user_target = 50;
	double fs_class_target = 50;
	
	boolean rollover_executed = false;
	
	String name = "fs_table_";
	String fs_table_dir_path;
	
	File currentLogfile;
	
	SimpleDateFormat df = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss.S" );
	
	private Map<String, FairshareTable> fileFsTableMap = new HashMap<String, FairshareTable>();

	public FairshareTableMgmt(int depth, String fsType, String interval, double decay,
			String fs_table_dir_path) {
		super();
		this.fsType = fsType;
		this.depth = depth;
		this.interval = interval;
		this.decay = decay;
		this.fs_table_dir_path = fs_table_dir_path;
		
		String[] interval_parts = interval.split(":");
		int hours  = Integer.parseInt(interval_parts[0]);
		int minutes = Integer.parseInt(interval_parts[1]);
		time_frame = hours * 60 + minutes;
	}



	public boolean init(boolean rollout){
		File fs_table_dir = new File(fs_table_dir_path);		
		if(!fs_table_dir.exists()){
			if(fs_table_dir.mkdir()){
				log.info("Created fairshare tables directory at " + 
						fs_table_dir.getAbsolutePath());
			}
			else{
				log.error("Could not create fairshare tables directory at " + 
						fs_table_dir.getAbsolutePath());
				return false;
			}
		}
		else{
			if(rollout){
				rollout_fs_data();
				return true;
			}
			for (int i=1;i<depth+1;i++ )
			{
				String filename = fs_table_dir_path + name + i + COM_PROPERTIES_SUFFIX;
				File f = new File( filename );
				if (f.exists())
				{
					Properties prop = new Properties();
					try
					{
						FileInputStream fis = null;
						try {
							fis = new FileInputStream(f);
						} catch (FileNotFoundException e1) {
							log.error("Could not create fairshare table directory at " + 
									filename);
							e1.printStackTrace();
							return false;
						}		
						log.debug("Reading properties from " + f.getPath());
						prop.load(fis);
						FairshareTable fairsharetb = new FairshareTable();					
						fairsharetb.setFairshare_values(prop);	
						fileFsTableMap.put(filename, fairsharetb);
					}
					catch (IOException ioe){
						log.error("Couldn't read properties from relative path 'config/ComFzj.properties'.");						
					}
				}
				else{
					break;
				}
			}
		}
		return true;
	}
	
	public boolean putFSData(String user, Long minutes) {		
		File current_fs_table;
		if(check_current_depth()>0){
			if(rollover_required()){
				try {
					rollOver();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			current_fs_table = new File( fs_table_dir_path + name + 1 + COM_PROPERTIES_SUFFIX );
			Properties prop = new Properties();
			String prop_path = current_fs_table.getAbsolutePath();
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(current_fs_table);
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}		
			log.debug("Reading properties from " + prop_path);
			try {
				prop.load(fis);
				String current_minutes = prop.getProperty(user);
				if(current_minutes==null){
					prop.setProperty(user, String.valueOf(minutes));
					log.debug("User " + user + " has consumed in current time frame [seconds]: " + minutes);
				}
				else{
					long current_min_val = Long.parseLong(current_minutes);
					current_min_val += minutes;
					prop.setProperty(user, String.valueOf(current_min_val));
					log.debug("User " + user + " has consumed in current time frame [seconds]: " + current_min_val);
				}
				FileOutputStream propOutFile =
					new FileOutputStream( current_fs_table );
				prop.store( propOutFile, null );
				

			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		else{
			File f = new File( fs_table_dir_path + name + 1 + COM_PROPERTIES_SUFFIX );			
			try {
				f.createNewFile();
				FileOutputStream propOutFile;
				propOutFile = new FileOutputStream( f );
				Properties prop = new Properties();
				Date dt = new Date(System.currentTimeMillis());
				prop.setProperty("timeWindowStart", df.format(dt));
				prop.setProperty(user, String.valueOf(minutes));
				prop.store(propOutFile, null);
			} 
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}


		return true;
	}
	
//	public Map<String,FairshareTable> read_fs_data(){
//		for (int i=1;i<depth+1;i++ )
//		{
//			if(rollover_executed && i>1){
//				rollover_executed = false;
//				i = depth +1;
//				break;
//			}
//			String filename = fs_table_dir_path + name + i + COM_PROPERTIES_SUFFIX;
//			File f = new File( filename );
//			if ( f.exists() )
//			{
//				Properties prop = new Properties();
//				try
//				{
//					FileInputStream fis = null;
//					try {
//						fis = new FileInputStream(f);
//					} catch (FileNotFoundException e1) {
//						e1.printStackTrace();
//					}		
//					log.info("Reading properties from " + f.getPath());
//					prop.load(fis);
//					FairshareTable fairsharetb = new FairshareTable();					
//					fairsharetb.setFairshare_values(prop);	
//					fileFsTableMap.put(filename, fairsharetb);
//				}
//				catch (IOException ioe){
//					log.error("Couldn't read properties from relative path 'config/ComFzj.properties'.");
//					
//				}
//			}
//		}
//		return fileFsTableMap;
//	}
	
	public Map<String,Double> read_current_fs_table(){
		Map<String,Double> fix_fs_values = new HashMap<String,Double>(); 
		double total_user_usage = 0.0;
		double total_queue_usage = 0.0;
		String filename = fs_table_dir_path + name + 1 + COM_PROPERTIES_SUFFIX;
		File f = new File( filename );
		if ( f.exists() )
		{
			Properties prop = new Properties();
			try
			{
				FileInputStream fis = null;
				try {
					fis = new FileInputStream(f);
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}		
				log.debug("Reading properties from " + f.getPath());
				prop.load(fis);					
				FairshareTable fairsharetb = new FairshareTable();					
				fairsharetb.setFairshare_values(prop);	
				fairsharetb.getFairshare_values().keySet().remove("timeWindowStart");
				for (Iterator<?> it = fairsharetb.getFairshare_values().keySet().iterator(); it.hasNext();) 
				{  
					String entry = (String) it.next();
					String usage = fairsharetb.getFairshare_values().getProperty(entry);
					Double value = Double.valueOf(usage);										
					fix_fs_values.put(entry, value);
					if(entry.split(":")[0].equals("user")){
						total_user_usage += value;	
					}
					else if(entry.split(":")[0].equals("queue")){
						total_queue_usage += value;	
					}			
				}
			}
			catch (IOException ioe){
				log.error("Couldn't read properties from path " + f.getAbsolutePath());
			}
		}
		fix_fs_values.put("total_users_usage", total_user_usage);
		fix_fs_values.put("total_queue_usage", total_queue_usage);
		return fix_fs_values;
	}
	
	public Map<String,Double> read_old_fs_data(){
		Map<String,Double> fix_fs_values = new HashMap<String,Double>(); 
		double total_user_usage = 0.0;
		double total_queue_usage = 0.0;
		for (int i=2;i<depth+1;i++ )
		{			
			String filename = fs_table_dir_path + name + i + COM_PROPERTIES_SUFFIX;
			File f = new File( filename );
			if ( f.exists() )
			{
				Properties prop = new Properties();
				try
				{
					FileInputStream fis = null;
					try {
						fis = new FileInputStream(f);
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}		
					log.debug("Reading properties from " + f.getPath());
					prop.load(fis);					
					FairshareTable fairsharetb = new FairshareTable();					
					fairsharetb.setFairshare_values(prop);	
					fairsharetb.getFairshare_values().keySet().remove("timeWindowStart");
					for (Iterator<?> it = fairsharetb.getFairshare_values().keySet().iterator(); it.hasNext();) 
					{  
						String entry = (String) it.next();
						String usage = fairsharetb.getFairshare_values().getProperty(entry);
						Double value = Double.valueOf(usage);
						value = value * Math.pow(decay,i-1);						
						if(fix_fs_values.get(entry)!=null){
							double existent_usage = fix_fs_values.get(entry);
							existent_usage += value;
							fix_fs_values.put(entry, existent_usage);							
						}
						else{
							fix_fs_values.put(entry, value);
						}
						if(entry.split(":")[0].equals("user")){
							total_user_usage += value;	
						}
						else if(entry.split(":")[0].equals("queue")){
							total_queue_usage += value;	
						}		
					}
				}
				catch (IOException ioe){
					log.error("Couldn't read properties from path " + f.getAbsolutePath());
				}
			}
		}
		fix_fs_values.put("total_users_usage", total_user_usage);
		fix_fs_values.put("total_queue_usage", total_queue_usage);
		return fix_fs_values;
	}
	
//	public void log_current_fairshareTables(){
//		read_fs_data();
//		if(fileFsTableMap!=null || !fileFsTableMap.isEmpty()){				
//			Iterator<Entry<String, FairshareTable> > fileFsTableSetItr = fileFsTableMap.entrySet().iterator(); 
//			while( fileFsTableSetItr.hasNext()) {
//				Entry<String, FairshareTable> fsTableEntry = fileFsTableSetItr.next();
//				String key = fsTableEntry.getKey();
//				FairshareTable fsTable = fsTableEntry.getValue();
//				Iterator<?> keyset = fsTable.getFairshare_values().keySet().iterator();
//				log.debug("Entries in file " + key);
//				log.debug("StartTime of Window " + fsTable.getFairshare_values().getProperty("timeWindowStart"));
//				while( keyset.hasNext()) {
//					String keyset_String = (String)keyset.next();
//					log.debug("User " + keyset_String + " : " + fsTable.getFairshare_values().getProperty(keyset_String));
//				}
//			}
//		}
//	}

	private int check_current_depth(){
		current_depth = 0;
		for (int i=1;i<depth+1;i++ )
		{
			String filename = fs_table_dir_path + name + i + COM_PROPERTIES_SUFFIX;
			File f = new File( filename );
			if ( f.exists() )
			{
				current_depth = i;
			}
			else
			{
				log.debug("Current depth is " + current_depth);
				return current_depth;
			}
		}
		return current_depth;
	}
	
	private boolean rollover_required(){
		String prop_path = fs_table_dir_path + name + 1 + COM_PROPERTIES_SUFFIX;
		File f = new File(prop_path);
		Properties prop = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(f);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}		
		log.debug("Reading properties from " + prop_path);
		try {
			prop.load(fis);
			String timeWindowStart = prop.getProperty("timeWindowStart");
			if(timeWindowStart==null){
				Date dt = new Date(System.currentTimeMillis());
				prop.setProperty("timeWindowStart", df.format(dt));
			}
			else{
				try {
					Date dt = df.parse(timeWindowStart);
					long current_windowTimeStart = dt.getTime();// Long.parseLong(timeWindowStart);
					Calendar myCal1 = Calendar.getInstance();   
					Calendar myCal2 = new GregorianCalendar();
					myCal2.setTimeInMillis(current_windowTimeStart);
					long time = myCal1.getTime().getTime() - myCal2.getTime().getTime();  
					long minutes = Math.round( (double)time / (60.*1000.) );     
					log.debug( "Current running minutes of time window: " + minutes );
					if(minutes>time_frame){
						log.debug("Rollover fs_target tables");
						return true;
					}		
				} catch (ParseException e) {
					e.printStackTrace();
				}
						
			}				
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;

	}
	
	private boolean rollout_fs_data(){
		File directory = new File(fs_table_dir_path);
		File[] files = directory.listFiles();
		for (File file : files)
		{
		   if (!file.delete())
		   {
				log.info("Could not rollout file properties from " + file.getAbsolutePath());
				return false;
		   }
		}
		return true;
	}

	private void rollOver() throws IOException{		
		if(current_depth==depth){
			File oldfsTablefile = new File(fs_table_dir_path + name + depth + COM_PROPERTIES_SUFFIX);
	        if(!oldfsTablefile.exists())
	            return;
	        oldfsTablefile.delete();	        
		}
		for(int i=current_depth;i>0;i--){
			File src_file = new File(fs_table_dir_path + name + i + COM_PROPERTIES_SUFFIX);
			if(src_file.exists()){
				int new_fs_table = i+1;
				File dest_file = new File(fs_table_dir_path + name + (new_fs_table) + COM_PROPERTIES_SUFFIX);
				log.debug("Creating fs table " + name + (new_fs_table));
				copyFile( src_file.getAbsolutePath(), dest_file.getAbsolutePath() );
				fileFsTableMap.put(name + (new_fs_table), fileFsTableMap.get(name+i));
				src_file.delete();
				log.debug("Deleted fs table " + name + i);
				if(i==1){
					try {
						src_file.createNewFile();
						FileOutputStream propOutFile;
						propOutFile = new FileOutputStream( src_file );
						Properties prop = new Properties();
						Date dt = new Date(System.currentTimeMillis());
						prop.setProperty("timeWindowStart", df.format(dt));
						prop.store(propOutFile, null);
					} 
					catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}			
			}
		}
		rollover_executed = true;
    }
	
	static void copy( InputStream fis, OutputStream fos )
	  {
	    try
	    {
	      byte[] buffer = new byte[ 0xFFFF ];
	      int neg = -1;
	      for ( int len; (len = fis.read(buffer)) != neg; )
	        fos.write( buffer, 0, len );
	    }
	    catch( IOException e ) {
	      System.err.println( e );
	    }
	    finally {
	      if ( fis != null )
	        try { fis.close(); } catch ( IOException e ) { e.printStackTrace(); }
	      if ( fos != null )
	        try { fos.close(); } catch ( IOException e ) { e.printStackTrace(); }
	    }
	  }
	  static void copyFile( String src, String dest )
	  {
	    try
	    {
	      copy( new FileInputStream( src ), new FileOutputStream( dest ) );
	    }
	    catch( IOException e ) {
	      e.printStackTrace();
	    }
	  }

   public void setWeights(int user, int group, int account, int queue_class){
	   setFs_user_weight(user);
	   setFs_class_weight(queue_class);	   
   }
   
   public void setTargets(double user, double group, double account, double queue_class){
	   setFs_user_target(user);
	   setFs_class_target(queue_class);	   
   }

	/**
	 * @return the fs_user_weight
	 */
	public int getFs_user_weight() {
		return fs_user_weight;
	}



	/**
	 * @param fs_user_weight the fs_user_weight to set
	 */
	public void setFs_user_weight(int fs_user_weight) {
		this.fs_user_weight = fs_user_weight;
	}



	/**
	 * @return the fs_class_weight
	 */
	public int getFs_class_weight() {
		return fs_class_weight;
	}



	/**
	 * @param fs_class_weight the fs_class_weight to set
	 */
	public void setFs_class_weight(int fs_class_weight) {
		this.fs_class_weight = fs_class_weight;
	}



	/**
	 * @return the fs_user_target
	 */
	public double getFs_user_target() {
		return fs_user_target;
	}



	/**
	 * @param fs_user_target the fs_user_target to set
	 */
	public void setFs_user_target(double fs_user_target) {
		this.fs_user_target = fs_user_target;
	}



	/**
	 * @return the fs_class_target
	 */
	public double getFs_class_target() {
		return fs_class_target;
	}



	/**
	 * @param fs_class_target the fs_class_target to set
	 */
	public void setFs_class_target(double fs_class_target) {
		this.fs_class_target = fs_class_target;
	}
	  
	  
}
