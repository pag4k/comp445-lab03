package Common;

public enum EUdpPacketType {
	Data(1), Ack(2), Syn(3), SynAck(4), Nak(5);

	private int Value;

	private EUdpPacketType(int Value) {
		this.Value = Value;
	}

	static EUdpPacketType FromValue(int Value) {
		for (EUdpPacketType Variant : EUdpPacketType.values()) {
			if (Variant.Value == Value) {
				return Variant;
			}
		}

		return null;
	}

	int GetValue() {
		return Value;
	}

	public String toString() {
		switch (Value) {
		case 1:
			return "Data";
		case 2:
			return "ACK";
		case 3:
			return "SYN";
		case 4:
			return "SYNACK";
		case 5:
			return "NAK";
		}
		return "ERROR";
	}
}
