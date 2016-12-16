/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.Tuple;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;


/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class  BinaryV1Router extends ActiveRouter {
	
	public Map<String, Integer> repCounter;
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public BinaryV1Router(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected BinaryV1Router(BinaryV1Router r) {
		super(r);
		this.repCounter = new HashMap<String, Integer>();
		//TODO: copy epidemic settings here (if any)
	}
	/*
	 * Count the numer of sent messages
	 * */
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);
		BinaryV1Router othRouter = (BinaryV1Router) from.getRouter();
		if(othRouter.repCounter.get(id) == null) {
			othRouter.repCounter.put(id, 0);
		}
		int curCount = othRouter.repCounter.get(id) + 1;
		othRouter.repCounter.put(id, curCount);
		if(curCount >= 2) {
			othRouter.deleteMessage(id, true);
		}
		return m;
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
		tryOtherMessages();
	}
	/*
	 * then remove current message from current node
	 * */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();

		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			BinaryV1Router othRouter = (BinaryV1Router)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId()) || othRouter.repCounter.containsKey(m.getId())) {
					continue; // skip messages that the other one has or other forward to other node
				}
				messages.add(new Tuple<Message, Connection>(m,con));
				
			}
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	@Override
	public BinaryV1Router replicate() {
		return new BinaryV1Router(this);
	}

}
