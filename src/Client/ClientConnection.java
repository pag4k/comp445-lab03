package Client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
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

public class ClientConnection {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private InetSocketAddress LocalSocketAddress;
	private InetSocketAddress RemoteSocketAddress;

	private int LocalSequenceNumber;
	private int RemoteSequenceNumber;

	public ClientConnection(InetSocketAddress SocketAddress) {
		this.RemoteSocketAddress = SocketAddress;
	}

	public Optional<HttpResponse> Send(HttpRequest Request) {
		// Get SYN.
		final Optional<UdpMessage> SynMsg = UdpMessage.ConstructSynNew(RemoteSocketAddress.getAddress(),
				RemoteSocketAddress.getPort());
		if (SynMsg.isEmpty()) {
			return Optional.empty();
		}

		// Get client SEQ from SYN.
		LocalSequenceNumber = SynMsg.get().GetSequenceNumber();

		Optional<HttpResponse> Response = null;
		try (DatagramChannel Channel = DatagramChannel.open();) {
			// Channel configuration.
			Channel.configureBlocking(false);
			// Port 0 will select any available one.
			Channel.bind(new InetSocketAddress(0));
			// Get local port.
			LocalSocketAddress = (InetSocketAddress) Channel.getLocalAddress();

			// Send SYN and try to receive SYNACK.
			Optional<UdpMessage> SynAckMsg = Optional.empty();
			for (int i = 0; i < Constants.RETRANSMISSION_ATTEMPTS; i++) {
				LOGGER.log(Level.INFO, "Sending: " + SynMsg.get());
				DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, SynMsg.get());

				LOGGER.log(Level.INFO, "Waiting for SYNACK on " + LocalSocketAddress.toString() + "...");
				SynAckMsg = DatagramChannelUtils.Receive(Channel, Constants.DEFAULT_TIMEOUT);

				// If received, break.
				if (SynAckMsg.isPresent()) {
					if (SynAckMsg.get().IsSynAck()) {
						break;
					} else {
						SynAckMsg = Optional.empty();
					}
				}
			}

			if (SynAckMsg.isEmpty()) {
				LOGGER.log(Level.WARNING, "Connection attemp timeout: SYNACK never received.");
				return Optional.empty();
			}

			LOGGER.log(Level.INFO, "Received: " + SynAckMsg.get() + ".");

			// Update address to avoid main server port.
			RemoteSocketAddress = SynAckMsg.get().GetSocketAddress();
			// Get server SEQ from SYNACK.
			RemoteSequenceNumber = SynAckMsg.get().GetSequenceNumber();

			// Send ACK.
			// Don't need to retransmit since received will know the connection is
			// established when it will receive Data.
			final Optional<UdpMessage> AckMsg = UdpMessage.ConstructAckNew(0, 0, RemoteSocketAddress.getAddress(),
					RemoteSocketAddress.getPort());
			if (AckMsg.isEmpty()) {
				return Optional.empty();
			}
			LOGGER.log(Level.INFO, "Sending: " + AckMsg.get() + ".");
			DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, AckMsg.get());

			// Initialize RDT-Sender to send Request..
			LOGGER.log(Level.INFO, "Starting RDT to " + RemoteSocketAddress.toString() + ".");
			SelectiveRepeatSender.Run(Channel, RemoteSocketAddress.getAddress(), RemoteSocketAddress.getPort(),
					LocalSequenceNumber, Request, true);

			// Initialize RDT-Receiver to receive Response.
			LOGGER.log(Level.INFO, "Done sending request. Waiting for response...");
			final Optional<HttpMessage> ReceivedMessage = SelectiveRepeatReceiver.Run(Channel, RemoteSequenceNumber);
			if (ReceivedMessage.isPresent()) {
				try {
					LOGGER.log(Level.INFO, "Done receiving response.");
					Response = Optional.of((HttpResponse) ReceivedMessage.get());
				} catch (ClassCastException e) {
					LOGGER.log(Level.WARNING, "Received message is not a HTTP message, but not a reply.");
					return Optional.empty();
				}
			} else {
				LOGGER.log(Level.WARNING, "Received message is not a valid HTTP message.");
				return Optional.empty();
			}

			// End connection.
			LOGGER.log(Level.INFO, "Ending connection...");
			Boolean bSuccesBoolean = false;
			Optional<UdpMessage> Ack1Msg = Optional.empty();
			for (int i = 0; i < Constants.RETRANSMISSION_ATTEMPTS; i++) {
				if (Ack1Msg.isEmpty()) {
					// Sending FIN.
					final Optional<UdpMessage> Fin1Msg = UdpMessage.ConstructFinNew(RemoteSocketAddress.getAddress(),
							RemoteSocketAddress.getPort());
					if (Fin1Msg.isEmpty()) {
						continue;
					}
					LOGGER.log(Level.INFO, "Sending: " + Fin1Msg.get() + ".");
					DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, Fin1Msg.get());

					// Receiving ACK.
					LOGGER.log(Level.INFO, "Waiting for ACK on " + LocalSocketAddress.toString() + "...");
					Ack1Msg = DatagramChannelUtils.Receive(Channel, Constants.DEFAULT_TIMEOUT);
					if (Ack1Msg.isEmpty()) {
						continue;
					} else {
						if (!Ack1Msg.get().IsAck()) {
							Ack1Msg = Optional.empty();
							continue;
						}
					}
				}
				LOGGER.log(Level.INFO, "Received: " + Ack1Msg.get() + ".");

				// Receiving FIN.
				LOGGER.log(Level.INFO, "Waiting for FIN on " + LocalSocketAddress.toString() + "...");
				final Optional<UdpMessage> Fin2Msg = DatagramChannelUtils.Receive(Channel, Constants.DEFAULT_TIMEOUT);
				if (Fin2Msg.isEmpty()) {
					continue;
				} else {
					if (Fin2Msg.get().IsAck()) {
						Ack1Msg = Fin2Msg;
						continue;
					}
				}
				LOGGER.log(Level.INFO, "Received: " + Fin2Msg.get() + ".");

				// Sending ACK.
				final Optional<UdpMessage> Ack2Msg = UdpMessage.ConstructAckNew(0, 0, RemoteSocketAddress.getAddress(),
						RemoteSocketAddress.getPort());
				if (Ack2Msg.isEmpty()) {
					Ack1Msg = Optional.empty();
					continue;
				}
				LOGGER.log(Level.INFO, "Sending: " + Ack2Msg.get() + ".");
				DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, Ack2Msg.get());

				// Check if another packet will come.
				final Optional<UdpMessage> TestMsg = DatagramChannelUtils.Receive(Channel, Constants.FIN_TIMEOUT);
				if (TestMsg.isEmpty()) {
					// If not, we are done.
					bSuccesBoolean = true;
					break;
				} else {
					// If so, try again.
					if (TestMsg.get().IsAck()) {
						Ack1Msg = TestMsg;
					} else {
						Ack1Msg = Optional.empty();
					}
				}
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

		return Response;
	}
}
