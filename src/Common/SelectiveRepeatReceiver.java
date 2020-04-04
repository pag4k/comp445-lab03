package Common;

import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SelectiveRepeatReceiver {

	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	public static Optional<HttpMessage> Run(DatagramChannel Channel, int StartSequenceNumber) {
		ArrayList<Byte> Bytes = new ArrayList<Byte>();
		// Since most message will be short, start assuming only on packet.
		ByteUtils.ExtendToSize(Bytes, UdpMessage.PAYLOAD_MAX_SIZE);
		int MessageLength = -1;

		HashMap<Integer, byte[]> BufferedPackets = new HashMap<Integer, byte[]>();

		int BasePacketNumber = 0;
		int BaseSequenceNumber = StartSequenceNumber;

		for (;;) {
			// First try to receive a packet.
			Optional<UdpMessage> DataMessage = DatagramChannelUtils.ReceiveOnce(Channel);
			if (DataMessage.isPresent() && DataMessage.get().IsData()) {
				final int SequenceNumber = DataMessage.get().GetSequenceNumber();
				if (IsWithinWindow(BaseSequenceNumber, SequenceNumber)) {
					Optional<UdpMessage> AckMessage = UdpMessage.ConstructAckNew(SequenceNumber, 0,
							DataMessage.get().GetAddress(), DataMessage.get().GetPortNumber());
					if (AckMessage.isPresent()) {
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, AckMessage.get());

						if (SequenceNumber == BaseSequenceNumber) {
							ByteUtils.AppendTo(Bytes, DataMessage.get().GetPayload().get());
							BasePacketNumber++;
							BaseSequenceNumber = (BaseSequenceNumber + 1) % (UdpMessage.NUMBER_MAX + 1);
							while (BufferedPackets.containsKey(BaseSequenceNumber)) {
								ByteUtils.AppendTo(Bytes, BufferedPackets.get(BaseSequenceNumber));
								BufferedPackets.remove(BaseSequenceNumber);
								BasePacketNumber++;
								BaseSequenceNumber = (BaseSequenceNumber + 1) % (UdpMessage.NUMBER_MAX + 1);
							}
						} else {
							if (!BufferedPackets.containsKey(SequenceNumber)) {
								// Clone works since byte is a primitive.
								BufferedPackets.put(SequenceNumber, DataMessage.get().GetPayload().get().clone());
							}
						}

					} else {
						LOGGER.log(Level.WARNING, "Could not generate ACK packet.");
					}

				} else if (IsWithinWindow(BaseSequenceNumber - Constants.WINDOW_SIZE, SequenceNumber)) {
					LOGGER.log(Level.INFO,
							"Received '" + SequenceNumber + "' which is in previous window. Resending ACK...");
					Optional<UdpMessage> AckMessage = UdpMessage.ConstructAckNew(SequenceNumber, 0,
							DataMessage.get().GetAddress(), DataMessage.get().GetPortNumber());
					if (AckMessage.isPresent()) {
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, AckMessage.get());
					} else {
						LOGGER.log(Level.WARNING, "Could not generate ACK packet.");
					}
				} else {
					LOGGER.log(Level.INFO, "Received '" + SequenceNumber
							+ "' which is neither current window not in previous window. Ignoring...");
				}
			}

			// Second check if message is completed.
			// If there is no buffered packets, it might be done.
			// Check if it is a well formed HTML message.
			if (BufferedPackets.isEmpty()) {
				Optional<HttpMessage> Message = HttpMessage
						.TryToParse(ByteUtils.ToPrimitives((Byte[]) Bytes.toArray()));
				if (Message.isPresent()) {
					return Message;
				}
			}

			// TODO: Need a failure exit condition?
		}

		// return Optional.empty();

	}

	private static Boolean IsWithinWindow(int BaseSequenceNumber, int SequenceNumber) {
		BaseSequenceNumber = BaseSequenceNumber % UdpMessage.NUMBER_MAX + 1;
		if (BaseSequenceNumber <= SequenceNumber) {
			return SequenceNumber < BaseSequenceNumber + Constants.WINDOW_SIZE;
		} else {
			return UdpMessage.NUMBER_MAX + 1 + SequenceNumber < BaseSequenceNumber + Constants.WINDOW_SIZE;
		}
	}

}
