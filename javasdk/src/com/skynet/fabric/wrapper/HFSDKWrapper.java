package com.skynet.fabric.wrapper;

import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.sdk.BlockEvent.TransactionEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

public class HFSDKWrapper {

	// hold the properties
	private Properties	properties			= new Properties();

	private HFClient	client;

	private Pattern		ordererNamePattern	= Pattern.compile("^orderer\\.([^.]+)\\.url$");

	private Pattern		peerNamePattern		= Pattern.compile("^peer\\.([^.]+)\\.url$");

	private Pattern		eventHubNamePattern	= Pattern.compile("^eventHub\\.([^.]+)\\.url$");



	// create the default HFSDKWrapper
	public HFSDKWrapper() {

	}



	// create the HFSDKWrapper from properties
	public HFSDKWrapper(Properties properties) {
		this.properties = properties;
	}



	// create the HFSDKWrapper from properties file
	public HFSDKWrapper(File properties) {
		this.properties = loadProperties(properties);
	}



	public Properties loadProperties(File file) {
		Properties properties = new Properties();
		try (InputStream input = new FileInputStream(file)) {
			properties.load(input);
			input.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return properties;
	}



	public Map<String, Object> invokeChaincode(Map<String, Object> context, String channelName, String chainID,
			String funName, String[] parameters) throws Exception {

		if (context == null) {
			context = new HashMap<String, Object>();
		}

		Channel channel = getChannel(channelName);

		TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();

		ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chainID).build();
		transactionProposalRequest.setChaincodeID(chaincodeID);

		// always be "invoke"
		transactionProposalRequest.setFcn(funName);
		transactionProposalRequest.setArgs(parameters);
		transactionProposalRequest.setProposalWaitTime(parseProsalWaitTime(this.properties));

		// save the transient map
		Map<String, byte[]> transientMap = new HashMap<String, byte[]>();
		transientMap.put("context", objectToByteArray(context));
		transientMap.put("channelName", objectToByteArray(channelName));
		transientMap.put("chainID", objectToByteArray(chainID));
		transientMap.put("funName", objectToByteArray(funName));
		transientMap.put("parameters", objectToByteArray(parameters));
		transactionProposalRequest.setTransientMap(transientMap);

		// send transaction to peers
		Collection<ProposalResponse> invokePropResp = channel.sendTransactionProposal(transactionProposalRequest);
		Collection<ProposalResponse> successful = new LinkedList<ProposalResponse>();
		Collection<ProposalResponse> failed = new LinkedList<ProposalResponse>();
		for (ProposalResponse response : invokePropResp) {
			if (response.getStatus() == Status.SUCCESS) {
				successful.add(response);
			} else {
				failed.add(response);
			}
		}

		if (failed.size() > 0) {
			ProposalResponse firstFailed = failed.iterator().next();
			throw new ProposalException(format("Status: %s Message: %s  Verifed: %s",
					firstFailed.getStatus().getStatus(), firstFailed.getMessage(), firstFailed.isVerified()));
		}

		////////////////////////////
		// Send transaction to orderer
		CompletableFuture<TransactionEvent> event = channel.sendTransaction(successful);
		return new ResponseMap(successful.iterator().next().getTransactionID(), getTimeout(properties), event);
	}



	public Map<String, Object> queryChaincode(Map<String, Object> context, String channelName, String chainID,
			String funName, String[] parameters) throws Exception {

		if (context == null) {
			context = new HashMap<String, Object>();
		}

		Channel channel = getChannel(channelName);

		QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
		queryByChaincodeRequest.setArgs(parameters);
		queryByChaincodeRequest.setFcn(funName);
		ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chainID).build();
		queryByChaincodeRequest.setChaincodeID(chaincodeID);

		Collection<ProposalResponse> queryProposals;

		try {
			queryProposals = channel.queryByChaincode(queryByChaincodeRequest);
		} catch (Exception e) {
			throw new CompletionException(e);
		}

		Map<String, Object> results = new HashMap<String, Object>();
		for (ProposalResponse proposalResponse : queryProposals) {
			int status = proposalResponse.getStatus().getStatus();
			String response = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
			results.put("status", status);
			results.put("response", response);
			return results;
		}
		return results;
	}



	public byte[] objectToByteArray(Object obj) {
		byte[] bytes = null;
		ByteArrayOutputStream byteArrayOutputStream = null;
		ObjectOutputStream objectOutputStream = null;
		try {
			byteArrayOutputStream = new ByteArrayOutputStream();
			objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
			objectOutputStream.writeObject(obj);
			objectOutputStream.flush();
			bytes = byteArrayOutputStream.toByteArray();

		} catch (IOException e) {
		} finally {
			if (objectOutputStream != null) {
				try {
					objectOutputStream.close();
				} catch (IOException e) {
				}
			}
			if (byteArrayOutputStream != null) {
				try {
					byteArrayOutputStream.close();
				} catch (IOException e) {
				}
			}

		}
		return bytes;
	}



	public Object byteArrayToObject(byte[] bytes) {
		Object obj = null;
		ByteArrayInputStream byteArrayInputStream = null;
		ObjectInputStream objectInputStream = null;
		try {
			byteArrayInputStream = new ByteArrayInputStream(bytes);
			objectInputStream = new ObjectInputStream(byteArrayInputStream);
			obj = objectInputStream.readObject();
		} catch (Exception e) {
		} finally {
			if (byteArrayInputStream != null) {
				try {
					byteArrayInputStream.close();
				} catch (IOException e) {
				}
			}
			if (objectInputStream != null) {
				try {
					objectInputStream.close();
				} catch (IOException e) {
				}
			}
		}
		return obj;
	}



	public long parseProsalWaitTime(Properties pProperties) {
		String prosalWaitTime = pProperties.getProperty("prosal.wait.time", "5000");
		return Long.parseLong(prosalWaitTime);
	}



	public Channel getChannel(String pChannelName) throws Exception {
		HFClient client = getCient();
		Channel channel = client.getChannel(pChannelName);
		if (channel == null) {
			channel = client.newChannel(pChannelName);
			List<Orderer> orderers = parseOrderers();
			for (Orderer orderer : orderers) {
				channel.addOrderer(orderer);
			}

			List<Peer> peers = parsePeers();
			for (Peer peer : peers) {
				channel.addPeer(peer);
			}
			List<EventHub> eventHubs = parseEventHubs();
			for (EventHub eventHub : eventHubs) {
				channel.addEventHub(eventHub);
			}
			channel.initialize();
		}
		return channel;
	}



	private List<EventHub> parseEventHubs() throws Exception {
		List<String> eventHubNames = findEventHubNames(this.properties);
		List<EventHub> eventHubs = new ArrayList<EventHub>();
		for (String eventHubName : eventHubNames) {
			eventHubs.add(parseEventHub(this.properties, eventHubName));
		}
		return eventHubs;
	}



	private EventHub parseEventHub(Properties pProperties, String pEventHubName) throws Exception {
		HFClient cient = getCient();
		return cient.newEventHub(pEventHubName, parseEventHubURL(pProperties, pEventHubName),
				parseEventHubProperties(pProperties, pEventHubName));
	}



	private String parseEventHubURL(Properties pProperties, String pEventHubName) {
		return pProperties.getProperty("eventHub." + pEventHubName + ".url");
	}



	private Properties parseEventHubProperties(Properties pProperties, String pEventHubName) {
		return null;
	}



	private List<String> findEventHubNames(Properties pProperties) {
		List<String> eventHubNames = new ArrayList<String>();

		Set<Object> propertyNames = pProperties.keySet();
		for (Object propertyName : propertyNames) {
			String property = (String) propertyName;
			Matcher m = eventHubNamePattern.matcher(property);
			if (m.matches()) {
				String eventHubName = m.group(1);
				eventHubNames.add(eventHubName);
			}
		}
		return eventHubNames;
	}



	public List<Peer> parsePeers() throws Exception {
		List<String> peerNames = findPeerNames(this.properties);
		List<Peer> peers = new ArrayList<Peer>();
		for (String peerName : peerNames) {
			peers.add(parsePeer(this.properties, peerName));
		}
		return peers;
	}



	public Peer parsePeer(Properties pProperties, String pPeerName) throws Exception {
		HFClient cient = getCient();
		return cient.newPeer(pPeerName, parsePeerURL(pProperties, pPeerName),
				parsePeerProperties(pProperties, pPeerName));
	}



	public Properties parsePeerProperties(Properties pProperties, String pPeerName) {
		return null;
	}



	public String parsePeerURL(Properties pProperties, String pPeerName) {
		return pProperties.getProperty("peer." + pPeerName + ".url");
	}



	public List<String> findPeerNames(Properties pProperties) {
		List<String> peerNames = new ArrayList<String>();

		Set<Object> propertyNames = pProperties.keySet();
		for (Object propertyName : propertyNames) {
			String property = (String) propertyName;
			Matcher m = peerNamePattern.matcher(property);
			if (m.matches()) {
				String peerName = m.group(1);
				peerNames.add(peerName);
			}
		}
		return peerNames;
	}



	public List<Orderer> parseOrderers() throws Exception {
		List<String> ordererNames = findOrdererNames(this.properties);
		List<Orderer> orderers = new ArrayList<Orderer>();
		for (String ordererName : ordererNames) {
			orderers.add(parseOrderer(this.properties, ordererName));
		}
		return orderers;
	}



	public Orderer parseOrderer(Properties pProperties, String pOrdererName) throws Exception {
		HFClient cient = getCient();
		return cient.newOrderer(pOrdererName, parseOrderURL(pProperties, pOrdererName),
				parseOrdererProperties(pProperties, pOrdererName));
	}



	// TODO
	public Properties parseOrdererProperties(Properties pProperties, String pOrdererName) {
		return null;
	}



	public String parseOrderURL(Properties pProperties, String pOrdererName) {
		return pProperties.getProperty("orderer." + pOrdererName + ".url");
	}



	public List<String> findOrdererNames(Properties pProperties) {
		List<String> ordererNames = new ArrayList<String>();

		Set<Object> propertyNames = pProperties.keySet();
		for (Object propertyName : propertyNames) {
			String property = (String) propertyName;
			Matcher m = ordererNamePattern.matcher(property);
			if (m.matches()) {
				String ordererName = m.group(1);
				ordererNames.add(ordererName);
			}
		}
		return ordererNames;
	}



	public synchronized HFClient getCient()
			throws CryptoException, InvalidArgumentException, IllegalAccessException, InstantiationException,
			ClassNotFoundException, NoSuchMethodException, InvocationTargetException, Exception {
		if (this.client == null) {
			HFClient client = HFClient.createNewInstance();
			client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
			client.setUserContext(parseUser(properties));
			this.client = client;
		}
		return this.client;
	}



	public User parseUser(final Properties pProperties) throws Exception {
		return new User() {

			@Override
			public Set<String> getRoles() {
				// we don't care it here
				return null;
			}



			@Override
			public String getName() {
				return parseUserName(pProperties);
			}



			@Override
			public String getMspId() {
				return parseMspId(pProperties);
			}



			@Override
			public Enrollment getEnrollment() {
				return new Enrollment() {

					@Override
					public PrivateKey getKey() {
						try {
							return getPrivateKeyFromBytes(
									IOUtils.toByteArray(new FileInputStream(parsePrivatekeyFilePath(pProperties))));
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}



					@Override
					public String getCert() {
						try {
							return new String(
									IOUtils.toByteArray(new FileInputStream(parseCertificateFilePath(pProperties))),
									"UTF-8");
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}

				};
			}



			@Override
			public String getAffiliation() {
				// we don't care it here
				return null;
			}



			@Override
			public String getAccount() {
				// we don't care it here
				return null;
			}
		};
	}



	public String parseMspId(Properties pProperties) {
		return pProperties.getProperty("user.mspid");
	}


	public String getTimeout(Properties pProperties) {
		return pProperties.getProperty("eventhub.timeout");
	}
	

	public String parseUserSecret(Properties pProperties) {
		return pProperties.getProperty("user.secret");
	}



	public String parseUserName(Properties pProperties) {
		return pProperties.getProperty("user.name");
	}



	public Properties parseCAProperties(Properties pProperties) {
		// not really implemented now
		return new Properties();
	}



	public String parsePrivatekeyFilePath(Properties pProperties) {
		return pProperties.getProperty("key.file");
	}



	public String parseCertificateFilePath(Properties pProperties) {
		return pProperties.getProperty("cert.file");
	}

	static {
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}



	public PrivateKey getPrivateKeyFromBytes(byte[] data)
			throws IOException, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
		final Reader pemReader = new StringReader(new String(data));

		final PEMKeyPair pemPair;
		try (PEMParser pemParser = new PEMParser(pemReader)) {
			pemPair = (PEMKeyPair) pemParser.readObject();
		}

		PrivateKey privateKey = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
				.getPrivateKey(pemPair.getPrivateKeyInfo());

		return privateKey;
	}
}
