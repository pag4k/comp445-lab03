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
import Common.EUdpPacketType;
import Common.UdpMessage;

public class ServerConnection implements Runnable {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private InetSocketAddress LocalSocketAddress;
	private InetSocketAddress RemoteSocketAddress;

	public ServerConnection(InetSocketAddress SocketAddress) {
		this.RemoteSocketAddress = SocketAddress;
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

			Optional<UdpMessage> SynAckMsg = UdpMessage.New(EUdpPacketType.SynAck, (long) 1,
					RemoteSocketAddress.getAddress(), RemoteSocketAddress.getPort(), new byte[] {});

			if (SynAckMsg.isEmpty()) {
				return;
			}

			LOGGER.log(Level.INFO, "Sending SYNACK to " + SynAckMsg.get().GetSocketAddress().toString() + ".");

			DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, SynAckMsg.get());

			LOGGER.log(Level.INFO, "Waiting for ACK on " + LocalSocketAddress.toString() + "...");

			Optional<UdpMessage> AckMsg = DatagramChannelUtils.ReceiveBlocking(Channel);

			if (AckMsg.isEmpty()) {
				return;
			}

			LOGGER.log(Level.INFO,
					"ACK received from " + AckMsg.get().GetSocketAddress().toString() + ". Waiting for RDT.");

		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
