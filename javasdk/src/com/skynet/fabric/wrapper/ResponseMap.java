package com.skynet.fabric.wrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo;

public class ResponseMap extends HashMap<String, Object>  implements Map<String, Object> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private CompletableFuture<TransactionEvent> event;
	
	private boolean getResult = false;
	private long timeout = 200;

	public ResponseMap(String tid, String timeout, CompletableFuture<TransactionEvent> pEvent) {
		super();
		this.put("transactionId", tid);
		try {
		this.timeout = Long.parseLong(timeout);
		}catch (Exception e) {
		}
		event = pEvent;
	}

	@Override
	public Object get(Object pKey) {
		saveKeys();
		return super.get(pKey);
	}

	private void saveKeys() {
		if(getResult){
			return;
		}
		//run result
		try {
			TransactionEvent transactionEvent = event.get(timeout, TimeUnit.MICROSECONDS);
			
			TransactionActionInfo transactionActionInfo = transactionEvent.getTransactionActionInfo(0);
		
			this.put("status", transactionActionInfo.getResponseStatus());
			this.put("response", transactionActionInfo.getProposalResponsePayload());
			this.put("blockId", transactionEvent.getBlockEvent().getBlockNumber());

		} catch (Exception e) {
			this.put("exception", e);
		} 
		getResult = true;
	}

	
}
