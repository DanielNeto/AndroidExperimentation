package com.example.androiddistributed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ambientdynamix.api.application.IContextInfo;
import org.ambientdynamix.contextplugins.addplugin.IAddPluginInfo;
import org.ambientdynamix.contextplugins.oneplugin.IOnePluginInfo;
import org.ambientdynamix.contextplugins.twoplugin.ITwoPluginInfo;

import android.os.Bundle;
import android.util.Log;

public class Job {
	
	private String contextType;
	private Scheduler scheduler;
	private String jobState;
	
	private List<String> dependencies;
	private Map<String, Boolean> allowedDependencies;
	private Map<String, Boolean> wakedDependencies;
	
	// null constructor
	public Job()
	{
		//
	}
	
	// constructor
	public Job(String contextType, Scheduler scheduler)
	{
		this.contextType = contextType;
		this.scheduler = scheduler;
		setState("not_ready");		
		dependencies = new ArrayList<String>();
		allowedDependencies = new HashMap<String, Boolean>();
		wakedDependencies = new HashMap<String, Boolean>();
	}
	
	public void setState(String state)
	{
		this.jobState = state;
		scheduler.sendThreadMessage("job_state_changed:"+state);
	}
	
	public void getMsg(IContextInfo nativeInfo)
	{			
		if(nativeInfo instanceof IAddPluginInfo)
	    {
			IAddPluginInfo info = (IAddPluginInfo) nativeInfo;
			String pluginState = info.getState();
			
			if( jobState.equals("not_ready") )
			{	
				if(pluginState.equals("ready"))
				{
					// get job dependencies	
					setDependencies(info.getDependencies());
						
					// TODO - check if job's dependencies is Ok with sensor permissions 
					for(String dependency : dependencies)
					{
						setAllowedDependency(dependency, true);
					}
							
					// call dependencies plugins
					for( String dependency : dependencies )
					{
						scheduler.commitDependency(dependency);	
					}
			
					setState("pending_initialization");
				}
				else if( pluginState.equals("stopped") )
				{				
					// get job dependencies	
			//		setDependencies(info.getDependencies());	
			//		startJob();
					
					scheduler.startPlugin(info.getContextType());
				}
			}
			else if( jobState.equals("running") )
			{		
				if( pluginState.equals("finished") )
				{
					Bundle results = info.getData();		
					
					Set<String> keys = results.keySet();
					Log.i("WTF", Integer.toString(keys.size()) );
					
					//	scheduler.storeResult(number);
				}
			}
			else if( jobState.equals("stopped") )
			{
				if( pluginState.equals("stopped") )
				{
					Bundle results = info.getData();		
					
					Set<String> keys = results.keySet();
					Log.i("WTF", Integer.toString(keys.size()) );
						
					//	scheduler.storeResult(number);
				}
			}
	    }
		else if( (nativeInfo instanceof IOnePluginInfo) )
	    {			
			IOnePluginInfo info = (IOnePluginInfo) nativeInfo;
						
			if( jobState.equals("pending_initialization") )
			{					
				setWakedDependency(nativeInfo.getContextType(), true);
				
				if( isDependenciesWaked() )
				{
					setState("initialized");
						
					scheduler.doJobPlugin(this.getContextType());
					for(String dependency : dependencies)
					{
						scheduler.doJobPlugin(dependency);
					}
				
					setState("running");
				}
			}
			else if( jobState.equals("running") )
			{
					double batteryLevel = info.getBatteryLevel();	
					Bundle data = new Bundle();
					data.putString("command", info.getContextType());
					data.putDouble("data", batteryLevel);
					scheduler.sendData(this.getContextType(), data);
			}
	    }
		else if(nativeInfo instanceof ITwoPluginInfo)
		{
			ITwoPluginInfo info = (ITwoPluginInfo) nativeInfo;
			String twoState = info.getState();
			Log.i("twostate", twoState);
			
			if( jobState.equals("pending_initialization") )
			{					
				setWakedDependency(nativeInfo.getContextType(), true);
				
				if( isDependenciesWaked() )
				{
					setState("initialized");
						
					scheduler.doJobPlugin(this.getContextType());
					for(String dependency : dependencies)
					{
						scheduler.doJobPlugin(dependency);
					}
				
					setState("running");
				}
			}
			else if( jobState.equals("running") )
			{
				long timeStamp = info.getTime();		
				
				Bundle data = new Bundle();
				data.putString("command", info.getContextType());
				data.putLong("data", timeStamp);
				
				scheduler.sendData(this.getContextType(), data);
			}	
		}
	}

	
	public String getState()
	{
		return this.jobState;
	}
		
	public String getContextType()
	{
		return contextType;
	}
		
	private void setDependencies(List<String> contextTypes)
	{		
		String message = "!";
		
		for(String dependency : contextTypes)
		{
			dependencies.add(dependency);
			message = message + dependency + "!";
		}
		
		scheduler.sendThreadMessage("jobDependencies:"+message);
	}
	
	public void setWakedDependency(String contextType, boolean waked)
	{
		wakedDependencies.put(contextType, waked);
	}
	
	private void setAllowedDependency(String contextType, boolean allowed)
	{
		allowedDependencies.put(contextType, allowed);
	}
	
	private boolean isDependenciesAllowed()
	{
		boolean allowed = false;
		
		for(String dependency : dependencies)
		{
			if( !allowedDependencies.get(dependency) )
			{
				allowed = false;
			}
		}
		
		return allowed;
	}
	
	public boolean isDependenciesWaked()
	{
		boolean waked = true;
		
		for(String dependency : dependencies)
		{
			if( !wakedDependencies.get(dependency) )
			{
				waked = false;
			}
		}
		
		return waked;
	}
	
	public void stopJob()
	{
		try
		{
			scheduler.stopPlugin(this.getContextType());
			
			for(String dependency : this.dependencies)
			{
				scheduler.stopPlugin(dependency);
			}
			
			setState("stopped");
		}
		catch(Exception e)
		{
			Log.e("WTF", e.toString());
		}
	}
	
	public void startJob()
	{
		for(String dependency : dependencies)
		{
			scheduler.pingPlugin(dependency);
		}
	}
	
}