package Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import Common.Constants;
import Common.DatagramChannelUtils;
import Common.UdpMessage;

public class ServerConnection implements Runnable {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private InetSocketAddress LocalSocketAddress;
	private InetSocketAddress RemoteSocketAddress;

	private int LocalSequenceNumber;
	private int RemoteSequenceNumber;

	public ServerConnection(UdpMessage UdpMessage) {
		this.RemoteSocketAddress = UdpMessage.GetSocketAddress();
		this.RemoteSequenceNumber = UdpMessage.GetSequenceNumber() + 1;
	}

	@Override
	public void run() {

		try {
			DatagramChannel Channel = DatagramChannel.open();
			Channel.configureBlocking(false);
			// Port 0 will select any available one.
			Channel.bind(new InetSocketAddress(0));
			LocalSocketAddress = (InetSocketAddress) Channel.getLocalAddress();
			// System.out.println("Sending address: " + OutChannel.getLocalAddress());

			Optional<UdpMessage> SynAckMsg = UdpMessage.ConstructSynAckNew(RemoteSequenceNumber,
					RemoteSocketAddress.getAddress(), RemoteSocketAddress.getPort());

			if (SynAckMsg.isEmpty()) {
				return;
			}

			LocalSequenceNumber = SynAckMsg.get().GetSequenceNumber();

			Optional<UdpMessage> AckMsg = Optional.empty();
			for (int i = 0; i < Constants.RETRANSMISSION_ATTEMPTS; i++) {
				LOGGER.log(Level.INFO, "Sending: " + SynAckMsg.get() + ".");

				DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, SynAckMsg.get());

				LOGGER.log(Level.INFO, "Waiting for ACK on " + LocalSocketAddress.toString() + "...");

				AckMsg = DatagramChannelUtils.Receive(Channel, Constants.DEFAULT_TIMEOUT);

				if (AckMsg.isPresent()) {
					break;
				}
			}

			if (AckMsg.isEmpty()) {
				LOGGER.log(Level.WARNING, "Connection attemp timeout: SYNACK never received.");
				return;
			}

			// Here, we could have the ACK or Data.
			if (AckMsg.get().GetSequenceNumber() == RemoteSequenceNumber) {
				// It was the ACK, just start RDT.
				LOGGER.log(Level.INFO, "Received: " + AckMsg.get() + ". Waiting for RDT.");
			} else {
				// It was Data, so we can assume the ACK was sent.
				LOGGER.log(Level.INFO,
						"Received data. Assume ACK was sent. : " + AckMsg.get() + ". Sending packet to RDT.");
				RemoteSequenceNumber++;
				// Then send the packet to RDT.
			}

		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
