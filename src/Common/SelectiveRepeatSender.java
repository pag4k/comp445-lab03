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

	public static void Run(DatagramChannel Channel, InetAddress Address, int PortNumber, int StartSequenceNumber,
			HttpMessage HttpMessage, Boolean bClient) {
		// Transform message in bytes.
		// FIXME: This assumes that the message can always be transformed as bytes.
		final byte[] Bytes = HttpMessage.GetAsBytes();
		final int MessageLength = Bytes.length;
		final int PacketCount = (int) Math.ceil(MessageLength * 1.0 / UdpMessage.PAYLOAD_MAX_SIZE);
		LOGGER.log(Level.INFO, "Started RDT. MessageLength : " + MessageLength + " PacketCount: " + PacketCount + ".");

		// Keep track of when packets were sent.
		// Assume Time = -1 implies it was ACKed.
		HashMap<Integer, Long> StartTimes = new HashMap<Integer, Long>();

		// Initialize base numbers.
		int BasePacketNumber = 0;
		int BaseSequenceNumber = StartSequenceNumber;

		for (;;) {
			// First try to receive a packet.
			Optional<UdpMessage> Message = DatagramChannelUtils.ReceiveOnce(Channel);

			// Second, check exceptional cases: if we got Data or FIN.
			if (Message.isPresent()) {
				if (Message.get().IsData()) {
					if (bClient) {
						// If client, it means server started to send, so abort.
						LOGGER.log(Level.INFO, "Received Data. Assume it is from next message. Aborting...");
						break;
					} else {
						// If server, it means its leftover from previous message. Send ACK.
						LOGGER.log(Level.INFO, "Received Data. Assume it came from previous message. Sending Ack...");
						final int SequenceNumber = Message.get().GetSequenceNumber();
						Optional<UdpMessage> AckMessage = UdpMessage.ConstructAckNew(0, SequenceNumber,
								Message.get().GetAddress(), Message.get().GetPortNumber());
						if (AckMessage.isPresent()) {
							DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, AckMessage.get());
						}
						continue;
					}
				} else if (Message.get().IsFin()) {
					LOGGER.log(Level.INFO, "Received FIN. Aborting...");
					break;
				}
			}

			// Third, process ACK.
			if (Message.isPresent() && Message.get().IsAck()) {
				LOGGER.log(Level.INFO, "Received : " + Message.get().toString());
				final int AcknowledgeNumber = Message.get().GetAcknowledgmentNumber();
				if (StartTimes.containsKey(AcknowledgeNumber)) {
					// If SEQ is base, more window.
					if (AcknowledgeNumber == BaseSequenceNumber) {
						StartTimes.remove(AcknowledgeNumber);
						BasePacketNumber++;
						BaseSequenceNumber = (BaseSequenceNumber + 1) % (UdpMessage.NUMBER_MAX + 1);
						// Increment window until a sent packet is found.
						while (StartTimes.containsKey(BaseSequenceNumber) && StartTimes.get(BaseSequenceNumber) < 0) {
							StartTimes.remove(BaseSequenceNumber);
							BasePacketNumber++;
							BaseSequenceNumber = (BaseSequenceNumber + 1) % (UdpMessage.NUMBER_MAX + 1);
						}
					} else {
						// Otherwise, leave timer there, but set to received.
						StartTimes.put(AcknowledgeNumber, (long) -1);
					}
				} else {
					LOGGER.log(Level.WARNING, "Timer for packer number " + AcknowledgeNumber + " was not started.");
				}
			}

			// Fourth, check timer and retransmit.
			for (int SequenceNumber : StartTimes.keySet()) {
				// Check if timed out.
				if (StartTimes.get(SequenceNumber) > 0
						&& System.currentTimeMillis() > StartTimes.get(SequenceNumber) + Constants.DEFAULT_TIMEOUT) {
					// Restart timer.
					StartTimes.put(SequenceNumber, System.currentTimeMillis());
					// Retransmit data.
					final int PacketNumber = GetPacketNumber(StartSequenceNumber, BasePacketNumber, BaseSequenceNumber,
							SequenceNumber);
					final Optional<UdpMessage> DataMessage = GetPacket(Address, PortNumber, Bytes, StartSequenceNumber,
							PacketNumber);
					if (DataMessage.isPresent()) {
						LOGGER.log(Level.INFO, "Sending : " + DataMessage.get().toString());
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, DataMessage.get());
					} else {
						LOGGER.log(Level.WARNING, "Could not generate Data packet.");
					}
				}
			}

			// Fifth, check if we can send another packet.
			if (StartTimes.keySet().size() < Constants.WINDOW_SIZE) {
				final int SequenceNumber = BaseSequenceNumber + StartTimes.keySet().size();
				final int PacketNumber = GetPacketNumber(StartSequenceNumber, BasePacketNumber, BaseSequenceNumber,
						SequenceNumber);
				// Check if we have more packets to send.
				if (PacketNumber < PacketCount) {
					// Start timer.
					StartTimes.put(SequenceNumber % (UdpMessage.NUMBER_MAX + 1), System.currentTimeMillis());
					// Send data,
					final Optional<UdpMessage> DataMessage = GetPacket(Address, PortNumber, Bytes, StartSequenceNumber,
							PacketNumber);
					if (DataMessage.isPresent()) {
						LOGGER.log(Level.INFO, "Sending : " + DataMessage.get().toString());
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, DataMessage.get());
					} else {
						LOGGER.log(Level.WARNING, "Could not generate package.");
					}
				}
			}

			// Sixth, check if done.
			if (BasePacketNumber == PacketCount) {
				break;
			}
		}
	}

	private static Optional<UdpMessage> GetPacket(InetAddress Address, int PortNumber, byte[] Bytes,
			int StartSequenceNumber, int PacketNumber) {

		final int SequenceNumber = GetSequenceNumber(StartSequenceNumber, PacketNumber);
		final int StartByteIndex = PacketNumber * UdpMessage.PAYLOAD_MAX_SIZE;
		final int NextByteIndex = StartByteIndex + UdpMessage.PAYLOAD_MAX_SIZE;
		final int EndByteIndex = (NextByteIndex <= Bytes.length) ? NextByteIndex : Bytes.length;

		return UdpMessage.New(EUdpPacketType.Data, SequenceNumber, 0, Address, PortNumber,
				Arrays.copyOfRange(Bytes, StartByteIndex, EndByteIndex));
	}

	public static int GetSequenceNumber(int StartSequenceNumber, int PacketNumber) {
		return (PacketNumber + StartSequenceNumber) % (UdpMessage.NUMBER_MAX + 1);
	}

	public static int GetPacketNumber(int StartSequenceNumber, int BasePacketNumber, int BaseSequenceNumber,
			int SequenceNumber) {
		// Integer division, round down.
		int Factor = (BasePacketNumber + StartSequenceNumber) / (UdpMessage.NUMBER_MAX + 1);
		if (BaseSequenceNumber > SequenceNumber) {
			Factor++;
		}
		return SequenceNumber - StartSequenceNumber + Factor * (UdpMessage.NUMBER_MAX + 1);

	}
}
