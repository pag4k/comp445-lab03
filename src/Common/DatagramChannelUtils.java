package Common;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatagramChannelUtils {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	public static Boolean Send(DatagramChannel Channel, SocketAddress TargetSocket, UdpMessage Message) {
		final byte[] RawMessage = Message.GenerateRaw();
		ByteBuffer Buffer = ByteBuffer.allocate(RawMessage.length);
		Buffer.put(RawMessage);
		Buffer.clear();
		try {
			Channel.send(Buffer, TargetSocket);
		} catch (IOException e) {
			return false;
		}

		return true;
	}

	public static Optional<UdpMessage> ReceiveOnce(DatagramChannel Channel) {
		ByteBuffer Buffer = ByteBuffer.allocate(UdpMessage.UDP_MESSAGE_MAX_SIZE);
		try {
			SocketAddress SocketAddress = Channel.receive(Buffer);
			if (SocketAddress != null) {
				Buffer.clear();
				Optional<UdpMessage> Message = UdpMessage.ConstructFromBytes(Buffer.array());
				if (Message.isPresent()) {
					return Message;
				} else {
					LOGGER.log(Level.WARNING, "Malformed message: " + Buffer.toString() + ".");
				}
			}
		} catch (IOException e) {
			return Optional.empty();
		}
		return Optional.empty();
	}

	public static Optional<UdpMessage> Receive(DatagramChannel Channel, int Timeout) {
		final long StartTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - StartTime < Timeout) {
			ByteBuffer Buffer = ByteBuffer.allocate(UdpMessage.UDP_MESSAGE_MAX_SIZE);

			try {
				SocketAddress SocketAddress = Channel.receive(Buffer);
				if (SocketAddress != null) {
					Buffer.clear();
					Optional<UdpMessage> Message = UdpMessage.ConstructFromBytes(Buffer.array());
					if (Message.isPresent()) {
						return Message;
					} else {
						LOGGER.log(Level.WARNING, "Malformed message: " + Buffer.toString() + ".");
					}
				}
			} catch (IOException e) {
				return Optional.empty();
			}
		}
		return Optional.empty();

	}

	public static Optional<UdpMessage> ReceiveBlocking(DatagramChannel Channel) {
		while (true) {
			ByteBuffer Buffer = ByteBuffer.allocate(UdpMessage.UDP_MESSAGE_MAX_SIZE);

			try {
				SocketAddress SocketAddress = Channel.receive(Buffer);
				if (SocketAddress != null) {
					Buffer.clear();
					Optional<UdpMessage> Message = UdpMessage.ConstructFromBytes(Buffer.array());
					if (Message.isPresent()) {
						return Message;
					} else {
						LOGGER.log(Level.WARNING, "Malformed message: " + Buffer.toString() + ".");
						return Optional.empty();
					}
				}
			} catch (IOException e) {
				return Optional.empty();
			}
		}
	}
}
