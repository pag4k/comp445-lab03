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
import Common.EUdpPacketType;
import Common.UdpMessage;

public class ClientConnection implements Runnable {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private InetSocketAddress LocalSocketAddress;
	private InetSocketAddress RemoteSocketAddress;

	public ClientConnection(InetSocketAddress SocketAddress) {
		this.RemoteSocketAddress = SocketAddress;
	}

	@Override
	public void run() {
//		UdpMessage Msg = new UdpMessage(EUdpPacketType.Data, (long) 1,
//		InetAddress.getByAddress(new byte[] { (byte) 127, (byte) 0, (byte) 0, (byte) 1 }), (int) 8007,
//		new byte[] { (byte) 75, (byte) 105, (byte) 32, (byte) 83 });

		Optional<UdpMessage> SynMsg = UdpMessage.New(EUdpPacketType.Syn, (long) 1, RemoteSocketAddress.getAddress(),
				RemoteSocketAddress.getPort(), new byte[] {});

		if (SynMsg.isEmpty()) {
			return;
		}

		try (DatagramChannel Channel = DatagramChannel.open();) {
			// Channel configuration.
			Channel.configureBlocking(false);
			// Port 0 will select any available one.
			Channel.bind(new InetSocketAddress(0));
			LocalSocketAddress = (InetSocketAddress) Channel.getLocalAddress();

			Optional<UdpMessage> SynAckMsg = Optional.empty();
			for (int i = 0; i < Constants.RETRANSMISSION_ATTEMPTS; i++) {
				LOGGER.log(Level.INFO, "Sending SYN to " + SynMsg.get().GetSocketAddress().toString() + ".");

				DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, SynMsg.get());

				LOGGER.log(Level.INFO, "Waiting for SYNACK on " + LocalSocketAddress.toString() + "...");

				SynAckMsg = DatagramChannelUtils.ReceiveBlocking(Channel);

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

			LOGGER.log(Level.INFO, "SYNACK received from " + SynAckMsg.get().GetSocketAddress().toString() + ".");

			Optional<UdpMessage> AckMsg = UdpMessage.New(EUdpPacketType.Ack, (long) 1, RemoteSocketAddress.getAddress(),
					RemoteSocketAddress.getPort(), new byte[] {});

			if (SynMsg.isEmpty()) {
				return;
			}

			LOGGER.log(Level.INFO, "Sending Ack to " + AckMsg.get().GetSocketAddress().toString() + ".");

			DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, AckMsg.get());

			LOGGER.log(Level.INFO, "Sending data  to " + RemoteSocketAddress.toString() + ".");

		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
