package Common;

import java.net.InetAddress;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SelectiveRepeatSender {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	public static Optional<HttpResponse> Run(DatagramChannel Channel, InetAddress Address, int PortNumber,
			int StartSequenceNumber, HttpRequest Request) {
		final byte[] Bytes = Request.GetAsBytes();
		final int RequestLength = Bytes.length;
		final int PacketCount = (int) Math.ceil(RequestLength * 1.0 / UdpMessage.PAYLOAD_MAX_SIZE);

		int RTT = Constants.INITIAL_RTT;

		// FIXME: Not the best data structure.
		HashMap<Integer, Long> StartTimes = new HashMap<Integer, Long>();

		int BaseNumber = StartSequenceNumber;

		for (;;) {
			// First try to receive a packet.
			Optional<UdpMessage> AckMessage = DatagramChannelUtils.ReceiveOnce(Channel);
			if (AckMessage.isPresent() && AckMessage.get().IsAck()) {
				final int AcknowledgeNumber = AckMessage.get().GetAcknowledgmentNumber();
				if (StartTimes.containsKey(AcknowledgeNumber)) {
					// This implies that the package was received.
					// Stop timer.
					StartTimes.remove(AcknowledgeNumber);
					//
					if (AcknowledgeNumber == BaseNumber % (UdpMessage.NUMBER_MAX + 1)) {
						BaseNumber = BaseNumber + 1;
					}
				} else {
					LOGGER.log(Level.WARNING, "Timer for packer number " + AcknowledgeNumber + " was not started.");
				}
			}

			// Second, check timer and resend.
			for (int Number : StartTimes.keySet()) {
				if (System.currentTimeMillis() + RTT > StartTimes.get(Number)) {
					StartTimes.put(Number, System.currentTimeMillis());
					final int factor = BaseNumber / (UdpMessage.NUMBER_MAX + 1);
					final int SequenceNumber = factor * (UdpMessage.NUMBER_MAX + 1) + Number;
					final int StartByteIndex = SequenceNumber * UdpMessage.PAYLOAD_MAX_SIZE;
					Optional<UdpMessage> DataMessage = UdpMessage.New(EUdpPacketType.Data,
							BaseNumber + Constants.WINDOW_SIZE, -1, Address, PortNumber,
							Arrays.copyOfRange(Bytes, StartByteIndex, StartByteIndex + UdpMessage.PAYLOAD_MAX_SIZE));
					if (DataMessage.isPresent()) {
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, DataMessage.get());
					} else {
						LOGGER.log(Level.WARNING, "Could not generate package.");
					}
				}
			}

			// Third, check if we can send another packet.
			if (StartTimes.keySet().size() < Constants.WINDOW_SIZE) {
				final int SequenceNumber = BaseNumber + StartTimes.keySet().size();
				StartTimes.put(SequenceNumber % (UdpMessage.NUMBER_MAX + 1), System.currentTimeMillis());
				final int StartByteIndex = (SequenceNumber) * UdpMessage.PAYLOAD_MAX_SIZE;
				Optional<UdpMessage> DataMessage = UdpMessage.New(EUdpPacketType.Data,
						BaseNumber + Constants.WINDOW_SIZE, -1, Address, PortNumber,
						Arrays.copyOfRange(Bytes, StartByteIndex, StartByteIndex + UdpMessage.PAYLOAD_MAX_SIZE));
				if (DataMessage.isPresent()) {
					DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, DataMessage.get());
				} else {
					LOGGER.log(Level.WARNING, "Could not generate package.");
				}
			}

			break;
		}

		return Optional.empty();
	}

}
