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
		// Initialize ArrayList that will hold the data.
		// FIXME: Not very efficient.
		ArrayList<Byte> Bytes = new ArrayList<Byte>();

		// Map to hold buffered data.
		HashMap<Integer, byte[]> BufferedPackets = new HashMap<Integer, byte[]>();

		// Initialize base number.
		int BaseSequenceNumber = StartSequenceNumber;

		for (;;) {
			// First try to receive a packet.
			final Optional<UdpMessage> Message = DatagramChannelUtils.ReceiveOnce(Channel);

			// Second, check exceptional cases: if we got FIN.
//			if (Message.isPresent()) {
//				if (Message.get().IsFin()) {
//					LOGGER.log(Level.INFO, "Received FIN. Aborting...");
//					break;
//				}
//			}

			// Third process Data.
			if (Message.isPresent() && Message.get().IsData() && Message.get().GetPayload().isPresent()) {
				LOGGER.log(Level.INFO, "Received: " + Message.get().toString());
				// Check if within window.
				final int SequenceNumber = Message.get().GetSequenceNumber();
				if (IsWithinWindow(BaseSequenceNumber, SequenceNumber)) {
					// If so, send ACK.
					final Optional<UdpMessage> AckMessage = UdpMessage.ConstructAckNew(0, SequenceNumber,
							Message.get().GetAddress(), Message.get().GetPortNumber());
					if (AckMessage.isPresent()) {
						LOGGER.log(Level.INFO, "Sending : " + AckMessage.get().toString());
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, AckMessage.get());
						if (SequenceNumber == BaseSequenceNumber) {
							// If SEQ is base, move window and append data.
							ByteUtils.AppendTo(Bytes, Message.get().GetPayload().get());
							BaseSequenceNumber = (BaseSequenceNumber + 1) % (UdpMessage.NUMBER_MAX + 1);
							while (BufferedPackets.containsKey(BaseSequenceNumber)) {
								ByteUtils.AppendTo(Bytes, BufferedPackets.get(BaseSequenceNumber));
								BufferedPackets.remove(BaseSequenceNumber);
								BaseSequenceNumber = (BaseSequenceNumber + 1) % (UdpMessage.NUMBER_MAX + 1);
							}
						} else {
							// Otherwise, buffer data.
							if (!BufferedPackets.containsKey(SequenceNumber)) {
								// Clone works since byte is a primitive.
								BufferedPackets.put(SequenceNumber, Message.get().GetPayload().get().clone());
							}
						}
					} else {
						LOGGER.log(Level.WARNING, "Could not generate ACK packet.");
					}
				} else if (IsWithinWindow(BaseSequenceNumber - Constants.WINDOW_SIZE, SequenceNumber)) {
					// If in previous window, retransmit ACK.
					LOGGER.log(Level.INFO,
							"Received '" + SequenceNumber + "' which is in previous window. Resending ACK...");
					final Optional<UdpMessage> AckMessage = UdpMessage.ConstructAckNew(0, SequenceNumber,
							Message.get().GetAddress(), Message.get().GetPortNumber());
					if (AckMessage.isPresent()) {
						LOGGER.log(Level.INFO, "Sending : " + AckMessage.get().toString());
						DatagramChannelUtils.Send(Channel, Constants.ROUTER_ADDRESS, AckMessage.get());
					} else {
						LOGGER.log(Level.WARNING, "Could not generate ACK packet.");
					}
				} else {
					// Otherwise, ignore.
					LOGGER.log(Level.INFO, "Received '" + SequenceNumber
							+ "' which is neither in current window not in previous window. Ignoring...");
				}
			}

			// Fourth check if message is completed.
			// If there is no buffered packets, it might be done.
			// Check if it is a well formed HTML message.
			if (BufferedPackets.isEmpty()) {
				Optional<HttpMessage> PotentialHttpMessage = HttpMessage
						.TryToParse(ByteUtils.ToPrimitives(Bytes.toArray()));
				if (PotentialHttpMessage.isPresent()) {
					return PotentialHttpMessage;
				}
			}
		}
	}

	private static Boolean IsWithinWindow(int BaseSequenceNumber, int SequenceNumber) {
		BaseSequenceNumber = Math.floorMod(BaseSequenceNumber, UdpMessage.NUMBER_MAX + 1);
		if (BaseSequenceNumber <= SequenceNumber) {
			return SequenceNumber < BaseSequenceNumber + Constants.WINDOW_SIZE;
		} else {
			return UdpMessage.NUMBER_MAX + 1 + SequenceNumber < BaseSequenceNumber + Constants.WINDOW_SIZE;
		}
	}
}
