package Common;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

public class UdpMessage {
	private static int HEADER_SIZE = 11;
	private static int PAYLOAD_MAX_SIZE = 1013;

	private EUdpPacketType PacketType; // 1 byte
	private Optional<Long> SequenceNumber; // 4 bytes big-endian
	private InetAddress Address; // 4 bytes IPv4
	private Optional<Integer> PortNumber; // 2 bytes big-endian
	private Optional<byte[]> PayLoad;

	public UdpMessage(EUdpPacketType PacketType, long SequenceNumber, InetAddress Address, int PortNumber,
			byte[] PayLoad) {
		this.PacketType = PacketType;
		if (SequenceNumber >= 0) {
			this.SequenceNumber = Optional.of(SequenceNumber);
		} else {
			this.SequenceNumber = Optional.empty();
		}
		this.Address = Address;
		if (0 < PortNumber || PortNumber > 65535) {
			this.PortNumber = Optional.of(PortNumber);
		} else {
			this.PortNumber = Optional.empty();
		}
		if (PayLoad != null && PayLoad.length < PAYLOAD_MAX_SIZE) {
			this.PayLoad = Optional.of(Arrays.copyOf(PayLoad, PayLoad.length));
		} else {
			this.PayLoad = Optional.empty();
		}
	}

	public Boolean IsValid() {
		if (SequenceNumber.isEmpty()) {
			return false;
		}
		if (PortNumber.isEmpty()) {
			return false;
		}
		if (PayLoad.isEmpty()) {
			return false;
		}
		return true;
	}

	public Optional<byte[]> GenerateRaw() {
		if (!IsValid()) {
			return Optional.empty();
		}
		byte[] RawBytes = new byte[HEADER_SIZE + PayLoad.get().length];

		byte[] Seq = ToBytes(SequenceNumber.get());

		byte[] Por = ToBytes(PortNumber.get());

		// TODO: Just need to cut the excess bytes on the left.

		return Optional.of(RawBytes);
	}

	private byte[] ToBytes(int IntValue) {
		ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE / 8);
		// Ignore BufferOverflowException.
		bb.putInt(IntValue);
		return bb.array();
	}

	private byte[] ToBytes(long LongValue) {
		ByteBuffer bb = ByteBuffer.allocate(Long.SIZE / 8);
		// Ignore BufferOverflowException.
		bb.putLong(LongValue);
		return bb.array();
	}
}
