/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class BinaryRouter extends ActiveRouter {
	/*
	 * count number of reps in buffer of current node
	 * */
	public Map<String, Integer> repCounter;
	public Map<String, Double> repPaths; 
	private Map<String, Double> Flags;
	private List<Double> interContactTimes;
	private Double lamda; 
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public BinaryRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected BinaryRouter(BinaryRouter r) {
		super(r);
		this.repCounter = new HashMap<String, Integer>();
		this.repPaths = new HashMap<String, Double>();
		this.Flags = new HashMap<String, Double>();
		this.interContactTimes = new ArrayList<Double>();
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

		tryOtherMessages();
	}
	
	/*
	 * which message should be drop when buffer is full
	 * */
//	@Override
//	protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
//		Collection<Message> messages = this.getMessageCollection();
//		List<Message> validMessages = new ArrayList<Message>();
//
//		for (Message m : messages) {
//			if (excludeMsgBeingSent && isSending(m.getId())) {
//				continue; // skip the message(s) that router is sending
//			}
//			validMessages.add(m);
//		}
//		
//		Comparator<Message> binaryComparator = new Comparator<Message>() {
//			public int compare(Message m1, Message m2) {
//				BinaryRouter router = (BinaryRouter) getHost().getRouter();
//				Double repPath1 = router.repPaths.get(m1.getId());
//				Double repPath2 = router.repPaths.get(m2.getId());
//				if ( repPath1 == null) repPath1 = 0.0;
//				if ( repPath2 == null) repPath2 = 0.0;
//				if( repPath1 - repPath2 >= 0) return -1;
//				return 1;
//			}
//		};
//		Collections.sort(validMessages, binaryComparator);
//		return validMessages.get(validMessages.size()-1); // return last message
//	}
	
	/*
	 * calculate meeting time and LAMDA value
	 * */
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		String key = con.getOtherNode(getHost()).toString();
		if (con.isUp()) {
			
			if (this.Flags.containsKey(key)) {
				Double interContactTime = SimClock.getTime() - this.Flags.get(key);
				this.interContactTimes.add(interContactTime);
				this.Flags.remove(key);
			}
			// calculate LAMDA
			if (this.interContactTimes.size() > 0) {
				Double sum = 0.0;
				for(Double i: this.interContactTimes) {
					sum += i;
				}
				this.lamda = this.interContactTimes.size() / sum;
			}
			
		} else {
			if(this.Flags.containsKey(key)) {
				System.out.println(this.Flags);
			}
			this.Flags.put(key, SimClock.getTime());
		}
	}
	
	/*
	 * Count the numer of sent messages
	 * */
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);
		BinaryRouter othRouter = (BinaryRouter) from.getRouter();
		if(othRouter.repCounter.get(id) == null) {
			othRouter.repCounter.put(id, 0);
		}
		int curCount = othRouter.repCounter.get(id) + 1;
		othRouter.repCounter.put(id, curCount);
		return m;
	}
	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
			BinaryRouter router = (BinaryRouter) getHost().getRouter();
			String msg1Id = tuple1.getKey().getId();
			String msg2Id = tuple2.getKey().getId();
			Double repPath1 = router.repPaths.get(msg1Id);
			Double repPath2 = router.repPaths.get(msg2Id);
			if (repPath1 == null) repPath1 = 0.0;
			if ( repPath2 == null) repPath2 = 0.0;
			if( repPath1 - repPath2 >= 0) return -1;
			return 1;
		}
	}
	/*
	 * then remove current message from current node
	 * */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();

		// calculate PR = 1/( 1 - e^(-LAMDA * R + n))
		for (Message m: getMessageCollection()) {	
			if(this.lamda != null) {
				int hopCounts = m.getHopCount();
				int ttl = m.getTtl() * 60;	
				Double repPath = 1/(1 - Math.pow(Math.E,-this.lamda * ttl + hopCounts));
				this.repPaths.put(m.getId(), repPath);
			}	
		}
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			BinaryRouter othRouter = (BinaryRouter)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has or other forward to other node
				}
				if(!this.repPaths.containsKey(m.getId())) {
					messages.add(new Tuple<Message, Connection>(m,con));
				} else {
					Double repPath = this.repPaths.get(m.getId());
					if(repPath > 0) {
						messages.add(new Tuple<Message, Connection>(m,con));
					}
				}
			}
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		//Collections.sort(messages, new TupleComparator());
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	@Override
	public BinaryRouter replicate() {
		return new BinaryRouter(this);
	}

}

