package Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import Common.Constants;
import Common.DatagramChannelUtils;
import Common.HttpMessage;
import Common.HttpRequest;
import Common.HttpResponse;
import Common.SelectiveRepeatReceiver;
import Common.SelectiveRepeatSender;
import Common.UdpMessage;

public class ServerConnection implements Runnable {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private InetSocketAddress LocalSocketAddress;
	final private InetSocketAddress RemoteSocketAddress;

	private int LocalSequenceNumber;
	final private int RemoteSequenceNumber;
	final private Path RootPath;

	public ServerConnection(UdpMessage UdpMessage, Path RootPath) {
		this.RemoteSocketAddress = UdpMessage.GetSocketAddress();
		this.RemoteSequenceNumber = UdpMessage.GetSequenceNumber();
		this.RootPath = RootPath;
	}

	public Boolean IsSameAddree(InetSocketAddress Address) {
		return this.RemoteSocketAddress.getAddress().equals(Address.getAddress())
				&& this.RemoteSocketAddress.getPort() == Address.getPort();
	}

	@Override
	public void run() {
		try (DatagramChannel Channel = DatagramChannel.open();) {
			// Channel configuration.
			Channel.configureBlocking(false);
			// Port 0 will select any available one.
			Channel.bind(new InetSocketAddress(0));
			// Get local port.
			LocalSocketAddress = (InetSocketAddress) Channel.getLocalAddress();

			// Get SYNACK.
			final Optional<UdpMessage> SynAckMsg = UdpMessage.ConstructSynAckNew(LocalSequenceNumber,
					RemoteSocketAddress.getAddress(), RemoteSocketAddress.getPort());
			if (SynAckMsg.isEmpty()) {
				return;
			}

			// Get Server SEQ from SYNACK.
			LocalSequenceNumber = SynAckMsg.get().GetSequenceNumber();

			// Send SYNACK and try to receive ACK.
			Optional<UdpMessage> AckMsg = Optional.empty();
			for (int i = 0; i < Constants.RETRANSMISSION_ATTEMPTS; i++) {
				LOGGER.log(Level.INFO, "Sending: " + SynAckMsg.get() + ".");
				DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, SynAckMsg.get());

				LOGGER.log(Level.INFO, "Waiting for ACK on " + LocalSocketAddress.toString() + "...");
				AckMsg = DatagramChannelUtils.Receive(Channel, Constants.DEFAULT_TIMEOUT);

				// If received, break.
				if (AckMsg.isPresent()) {
					if (AckMsg.get().IsAck()) {
						LOGGER.log(Level.INFO, "Received: " + AckMsg.get());
						break;
					} else if (AckMsg.get().IsData()) {
						LOGGER.log(Level.INFO, "Received data (assume ACK was sent): " + AckMsg.get());
						break;
					} else {
						AckMsg = Optional.empty();
					}
				}
			}
			if (AckMsg.isEmpty()) {
				LOGGER.log(Level.WARNING, "Connection attemp timeout: ACK never received.");
				return;
			}

			// Initialize RDT-Receiver to receive Request.
			final Optional<HttpMessage> ReceivedMessage = SelectiveRepeatReceiver.Run(Channel, RemoteSequenceNumber);
			HttpRequest Request = null;
			if (ReceivedMessage.isPresent()) {
				try {
					LOGGER.log(Level.INFO, "Done receiving request.");
					Request = (HttpRequest) ReceivedMessage.get();
				} catch (ClassCastException e) {
					LOGGER.log(Level.WARNING, "Received message is not a HTTP message, but not a reply.");
					return;
				}
			} else {
				LOGGER.log(Level.WARNING, "Received message is not a valid HTTP message.");
				return;
			}

			// Get Response from HTTP Protocol.
			final HttpResponse Response = HttpProtocol.GetResponse(RootPath, Request, false);

			if (Response.GetError().isEmpty()) {
				LOGGER.log(Level.INFO, "Sending response.");
				SelectiveRepeatSender.Run(Channel, RemoteSocketAddress.getAddress(), RemoteSocketAddress.getPort(),
						LocalSequenceNumber, Response, false);
			} else {
				LOGGER.log(Level.WARNING, Response.GetError().get());
				return;
			}
			LOGGER.log(Level.INFO, "Done sending response.");

			// End connection.
			LOGGER.log(Level.INFO, "Wait for conneciton to close...");
			Boolean bSuccesBoolean = false;
			Optional<UdpMessage> Fin1Msg = Optional.empty();
			for (int i = 0; i < Constants.RETRANSMISSION_ATTEMPTS; i++) {
				if (Fin1Msg.isEmpty()) {
					// Receive FIN.
					LOGGER.log(Level.INFO, "Waiting for FIN on " + LocalSocketAddress.toString() + "...");
					Fin1Msg = DatagramChannelUtils.Receive(Channel, Constants.DEFAULT_TIMEOUT);
					if (Fin1Msg.isEmpty()) {
						continue;
					} else {
						if (!Fin1Msg.get().IsFin()) {
							Fin1Msg = Optional.empty();
							continue;
						}
					}
				}
				LOGGER.log(Level.INFO, "Received: " + Fin1Msg.get() + ".");

				// Send ACK.
				final Optional<UdpMessage> Ack1Msg = UdpMessage.ConstructAckNew(0, 0, RemoteSocketAddress.getAddress(),
						RemoteSocketAddress.getPort());
				if (Ack1Msg.isEmpty()) {
					Fin1Msg = Optional.empty();
					continue;
				}
				LOGGER.log(Level.INFO, "Sending: " + Ack1Msg.get() + ".");
				DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, Ack1Msg.get());

				// Send FIN.
				final Optional<UdpMessage> Fin2Msg = UdpMessage.ConstructFinNew(RemoteSocketAddress.getAddress(),
						RemoteSocketAddress.getPort());
				if (Fin2Msg.isEmpty()) {
					Fin1Msg = Optional.empty();
					continue;
				}
				LOGGER.log(Level.INFO, "Sending: " + Fin2Msg.get() + ".");
				DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, Fin2Msg.get());

				// Receive ACK.
				LOGGER.log(Level.INFO, "Waiting for ACK on " + LocalSocketAddress.toString() + "...");
				final Optional<UdpMessage> Ack2Msg = DatagramChannelUtils.Receive(Channel, Constants.DEFAULT_TIMEOUT);
				if (Ack2Msg.isEmpty()) {
					// If receive nothing, retransmit ACK and FIN.
					continue;
				} else {
					if (Ack2Msg.get().IsFin()) {
						Fin1Msg = Ack2Msg;
						continue;
					} else {
						Fin1Msg = Optional.empty();
					}
				}
				LOGGER.log(Level.INFO, "Received: " + Ack2Msg.get() + ".");

				// We are done.
				bSuccesBoolean = true;
				break;
			}

			if (bSuccesBoolean) {
				LOGGER.log(Level.INFO, "Successfully closed connection with: " + RemoteSocketAddress.toString() + ".");
			} else {
				LOGGER.log(Level.WARNING, "Failed to closed connection with: " + RemoteSocketAddress.toString() + ".");
			}
		} catch (SocketException e1) {
			LOGGER.log(Level.WARNING, "ERROR: SocketException: " + e1.toString());
		} catch (IOException e2) {
			LOGGER.log(Level.WARNING, "ERROR: IOException: " + e2.toString());
		}
	}
}
