/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import routing.maxprop.MeetingProbabilitySet;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader.Array;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class EpicRouter extends ActiveRouter{

	public static HashMap<String, Integer> counter = new HashMap<String, Integer>();
	public static HashMap<String, Double> marker = new HashMap<String, Double>();
	public static HashMap<String, Double> mettings = new HashMap<String, Double>();
	public static HashMap<String, ArrayList<Double>> lamdas = new HashMap<String, ArrayList<Double>>(); 
	public static HashMap<String, ArrayList<Double>> reps = new HashMap<String, ArrayList<Double>>();
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EpicRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EpicRouter(EpicRouter r) {
		super(r);
		
		//TODO: copy epidemic settings here (if any)
	}
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		//DTNHost otherHost = con.getOtherNode(getHost());
		String key = getHost().toString();
		if (con.isUp()) {
			if(marker.get(key) != null) {
				double mettingTime = SimClock.getTime() - marker.get(key);
				marker.remove(key);
				if(mettings.get(key) == null) {
					mettings.put(key, 1/ mettingTime);
					counter.put(key, 1);
					// save array of lamdas
					ArrayList<Double> ls = new ArrayList<Double>();
					ls.add(1/mettingTime);
					lamdas.put(key, ls);
				} else {
					Double m = mettings.get(key);
					Integer c = counter.get(key);
					m = (c + 1)/( c/m + mettingTime);
					mettings.put(key, m);
					counter.put(key, c + 1);
					// Array of lamdas
					ArrayList<Double> ls = lamdas.get(key);
					ls.add(m);
					lamdas.put(key, ls);
				}
			}
			
			if(getHost().getAddress() == 0) {
				predictReplicas();
			}
		} else {
			if(marker.get(key) == null) {
				marker.put(key, SimClock.getTime());
			}
		}
	}
	
	public void predictReplicas() {
		List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
		Double lamda = mettings.get(getHost().toString());
		if(lamda == null) return;
		for(Message m: messages) {
			int hopCounts = m.getHopCount();
			int ttl = m.getTtl() * 60;
			String id = m.getId();
			if(ttl < 0) ttl = 0;
			Double replicas = 1/(1 - Math.pow(Math.E,-lamda * ttl + hopCounts));
			Double maxRep = (ttl * lamda);
			if(replicas > 10) replicas = 10.0;
			if(replicas < 0) replicas = 0.0;
			// save to reps
			if(reps.get(id) == null) {
				ArrayList<Double> mRep = new ArrayList<Double>();
				mRep.add(replicas);
				reps.put(id, mRep);
			} else {
				ArrayList<Double> mRep = reps.get(id);
				mRep.add(replicas);
				reps.put(id, mRep);
			}
		}
	}
	public void exportReps() {
		try {
			PrintWriter writer = new PrintWriter("reps.csv", "UTF-8");
			int maxRows = 0;
			for(Map.Entry<String, ArrayList<Double>> entry:reps.entrySet()) {
		    	if(entry.getValue().size() > maxRows) 
		    		maxRows = entry.getValue().size();
		    	writer.print(entry.getKey() + ",");
		    }
			writer.println();
			for(int i = 1; i <= maxRows; i++) {
				for(Map.Entry<String, ArrayList<Double>> entry:reps.entrySet()) {
					
					if(entry.getValue().size() < i) {
						writer.print(",");
					} else {
						Double rep = entry.getValue().get(i-1);
						writer.print(rep + ",");
					}
			    }
				writer.println();
			}
			writer.close();
		} catch(IOException e) {
			System.out.println(e);
		}
	}
	public void exportLamdas() {
		try {
			PrintWriter writer = new PrintWriter("lamdas.csv", "UTF-8");
			int maxRows = 0;
			for(Map.Entry<String, ArrayList<Double>> entry:lamdas.entrySet()) {
		    	if(entry.getValue().size() > maxRows) 
		    		maxRows = entry.getValue().size();
		    	writer.print(entry.getKey() + ",");
		    }
			writer.println();
			for(int i = 1; i <= maxRows; i++) {
				for(Map.Entry<String, ArrayList<Double>> entry:lamdas.entrySet()) {
					
					if(entry.getValue().size() < i) {
						writer.print(",");
					} else {
						Double lamda = entry.getValue().get(i-1);
						writer.print(lamda + ",");
					}
			    }
				writer.println();
			}
			writer.close();
		} catch(IOException e) {
			System.out.println(e);
		}
	}
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		// then try any/all message to any/all connection
		this.tryAllMessagesToAllConnections();
		
		if(SimClock.getIntTime() == 43200) {
			exportLamdas();
			exportReps();
		}
	}


	@Override
	public EpicRouter replicate() {
		return new EpicRouter(this);
	}

}
