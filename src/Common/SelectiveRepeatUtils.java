package Common;

public class SelectiveRepeatUtils {
	public static int GetSequenceNumber(int StartSequenceNumber, int PacketNumber) {
		return (PacketNumber + StartSequenceNumber) / (UdpMessage.NUMBER_MAX + 1);

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
