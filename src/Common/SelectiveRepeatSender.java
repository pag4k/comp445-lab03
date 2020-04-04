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
			int StartSequenceNumber, HttpMessage Message) {
		// FIXME: This assumes that the message can always be transformed as bytes.
		final byte[] Bytes = Message.GetAsBytes();
		final int MessageLength = Bytes.length;
		final int PacketCount = (int) Math.ceil(MessageLength * 1.0 / UdpMessage.PAYLOAD_MAX_SIZE);

		int RTT = Constants.INITIAL_RTT;

		// FIXME: Not the best data structure.
		HashMap<Integer, Long> StartTimes = new HashMap<Integer, Long>();

		int BasePacketNumber = 0;
		int BaseSequenceNumber = StartSequenceNumber;

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
					if (AcknowledgeNumber == BaseSequenceNumber) {
						BasePacketNumber++;
						BaseSequenceNumber = (BaseSequenceNumber + 1) % (UdpMessage.NUMBER_MAX + 1);
					}
					// Increment window until a sent packet is found.
					while (!StartTimes.containsKey(BaseSequenceNumber)) {
						BasePacketNumber++;
						BaseSequenceNumber = (BaseSequenceNumber + 1) % (UdpMessage.NUMBER_MAX + 1);
					}
				} else {
					LOGGER.log(Level.WARNING, "Timer for packer number " + AcknowledgeNumber + " was not started.");
				}
			}

			// Second, check timer and resend.
			for (int SequenceNumber : StartTimes.keySet()) {
				if (System.currentTimeMillis() + RTT > StartTimes.get(SequenceNumber)) {
					StartTimes.put(SequenceNumber, System.currentTimeMillis());
					final int PacketNumber = SelectiveRepeatUtils.GetPacketNumber(StartSequenceNumber, BasePacketNumber,
							BaseSequenceNumber, SequenceNumber);
					Optional<UdpMessage> DataMessage = GetPacket(Address, PortNumber, Bytes, StartSequenceNumber,
							PacketNumber);
					if (DataMessage.isPresent()) {
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, DataMessage.get());
					} else {
						LOGGER.log(Level.WARNING, "Could not generate Data packet.");
					}
				}
			}

			// Third, check if we can send another packet.
			if (StartTimes.keySet().size() < Constants.WINDOW_SIZE) {
				final int SequenceNumber = BaseSequenceNumber + StartTimes.keySet().size();
				final int PacketNumber = SelectiveRepeatUtils.GetPacketNumber(StartSequenceNumber, BasePacketNumber,
						BaseSequenceNumber, SequenceNumber);
				if (PacketNumber < PacketCount) {
					StartTimes.put(SequenceNumber % (UdpMessage.NUMBER_MAX + 1), System.currentTimeMillis());
					Optional<UdpMessage> DataMessage = GetPacket(Address, PortNumber, Bytes, StartSequenceNumber,
							PacketNumber);
					if (DataMessage.isPresent()) {
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, DataMessage.get());
					} else {
						LOGGER.log(Level.WARNING, "Could not generate package.");
					}
				}
			}

			// Fourth, check if done.
			if (BasePacketNumber == PacketCount) {
				break;
			}
		}

		return Optional.empty();
	}

	private static Optional<UdpMessage> GetPacket(InetAddress Address, int PortNumber, byte[] Bytes,
			int StartSequenceNumber, int PacketNumber) {
		final int SequenceNumber = SelectiveRepeatUtils.GetSequenceNumber(StartSequenceNumber, PacketNumber);

		final int StartByteIndex = PacketNumber * UdpMessage.PAYLOAD_MAX_SIZE;
		final int NextByteIndex = StartByteIndex + UdpMessage.PAYLOAD_MAX_SIZE;
		final int EndByteIndex = (NextByteIndex <= Bytes.length) ? NextByteIndex : Bytes.length;

		return UdpMessage.New(EUdpPacketType.Data, SequenceNumber, -1, Address, PortNumber,
				Arrays.copyOfRange(Bytes, StartByteIndex, EndByteIndex));
	}

}
