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
import Common.UdpMessage;

public class ClientConnection implements Runnable {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private InetSocketAddress LocalSocketAddress;
	private InetSocketAddress RemoteSocketAddress;

	private int LocalSequenceNumber;
	private int RemoteSequenceNumber;

	public ClientConnection(InetSocketAddress SocketAddress) {
		this.RemoteSocketAddress = SocketAddress;
	}

	@Override
	public void run() {
//		UdpMessage Msg = new UdpMessage(EUdpPacketType.Data, (long) 1,
//		InetAddress.getByAddress(new byte[] { (byte) 127, (byte) 0, (byte) 0, (byte) 1 }), (int) 8007,
//		new byte[] { (byte) 75, (byte) 105, (byte) 32, (byte) 83 });

		Optional<UdpMessage> SynMsg = UdpMessage.ConstructSynNew(RemoteSocketAddress.getAddress(),
				RemoteSocketAddress.getPort());

		if (SynMsg.isEmpty()) {
			return;
		}

		// SynMsg.get().PrintAsUnsignedBytes();

		LocalSequenceNumber = SynMsg.get().GetSequenceNumber();

		try (DatagramChannel Channel = DatagramChannel.open();) {
			// Channel configuration.
			Channel.configureBlocking(false);
			// Port 0 will select any available one.
			Channel.bind(new InetSocketAddress(0));
			LocalSocketAddress = (InetSocketAddress) Channel.getLocalAddress();

			Optional<UdpMessage> SynAckMsg = Optional.empty();
			for (int i = 0; i < Constants.RETRANSMISSION_ATTEMPTS; i++) {
				LOGGER.log(Level.INFO, "Sending: " + SynMsg.get() + ".");

				DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, SynMsg.get());

				LOGGER.log(Level.INFO, "Waiting for SYNACK on " + LocalSocketAddress.toString() + "...");

				SynAckMsg = DatagramChannelUtils.Receive(Channel, Constants.DEFAULT_TIMEOUT);

				if (SynAckMsg.isPresent()) {
					break;
				}
			}

			if (SynAckMsg.isEmpty()) {
				LOGGER.log(Level.WARNING, "Connection attemp timeout: SYNACK never received.");
				return;
			}

			// Update address to avoid main server port.
			RemoteSocketAddress = SynAckMsg.get().GetSocketAddress();

			LocalSequenceNumber++;

			RemoteSequenceNumber = SynAckMsg.get().GetSequenceNumber() + 1;

			LOGGER.log(Level.INFO, "Received: " + SynAckMsg.get() + ".");

			Optional<UdpMessage> AckMsg = UdpMessage.ConstructAckNew(LocalSequenceNumber, RemoteSequenceNumber,
					RemoteSocketAddress.getAddress(), RemoteSocketAddress.getPort());

			if (SynMsg.isEmpty()) {
				return;
			}

			LOGGER.log(Level.INFO, "Sending: " + AckMsg.get() + ".");

			DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, AckMsg.get());

			LOGGER.log(Level.INFO, "Sending data to " + RemoteSocketAddress.toString() + ".");

		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
