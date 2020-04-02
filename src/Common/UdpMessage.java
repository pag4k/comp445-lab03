package Common;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UdpMessage {
	private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	private static int HEADER_SIZE = 11;
	private static int PAYLOAD_MAX_SIZE = 1013;
	public static int UDP_MESSAGE_MAX_SIZE = HEADER_SIZE + PAYLOAD_MAX_SIZE;

	private EUdpPacketType PacketType; // 1 byte
	private Long SequenceNumber; // 4 bytes big-endian
	private InetAddress Address; // 4 bytes IPv4
	private Integer PortNumber; // 2 bytes big-endian
	private Optional<byte[]> PayLoad;

	private UdpMessage() {
		PacketType = null;
		SequenceNumber = (long) -1;
		Address = null;
		PortNumber = -1;
		PayLoad = Optional.empty();

	}

	public static Optional<UdpMessage> New(EUdpPacketType PacketType, long SequenceNumber, InetAddress Address,
			int PortNumber, byte[] PayLoad) {

		UdpMessage Message = new UdpMessage();

		Message.PacketType = PacketType;
		if (SequenceNumber >= 0) {
			Message.SequenceNumber = SequenceNumber;
		} else {
			LOGGER.log(Level.WARNING, "SequenceNumber should not be negative.");
			return Optional.empty();
		}
		Message.Address = Address;
		if (0 < PortNumber || PortNumber > 65535) {
			Message.PortNumber = PortNumber;
		} else {
			LOGGER.log(Level.WARNING, "PortNumber should be between 0 and 65535.");
			return Optional.empty();
		}
		if (PayLoad != null && PayLoad.length < PAYLOAD_MAX_SIZE) {
			Message.PayLoad = Optional.of(Arrays.copyOf(PayLoad, PayLoad.length));
		} else if (PayLoad == null) {
			LOGGER.log(Level.WARNING, "PayLoad is NULL.");
			return Optional.empty();
		} else {
			LOGGER.log(Level.WARNING, "PayLoad is too long: " + PayLoad.length + " bytes, but should be less than "
					+ PAYLOAD_MAX_SIZE + " bytes.");
			return Optional.empty();
		}

		return Optional.of(Message);
	}

	public static Optional<UdpMessage> ConstructFromBytes(byte[] Raw) {
		if (Raw.length < HEADER_SIZE) {
			LOGGER.log(Level.WARNING, "Byte array is too short. It does not contain all the header.");
			return Optional.empty();
		}

		UdpMessage Message = new UdpMessage();

		Message.PacketType = EUdpPacketType.FromValue(UnsignedByteToShort(Raw[0]));

		if (Message.PacketType == null) {
			LOGGER.log(Level.WARNING, "PacketType '" + UnsignedByteToShort(Raw[0]) + "' is not valid.");
			return Optional.empty();
		}

		Message.SequenceNumber = UnsignedByteToLong(Raw[1], Raw[2], Raw[3], Raw[4]);

		try {
			Message.Address = InetAddress.getByAddress(new byte[] { Raw[5], Raw[6], Raw[7], Raw[8] });
		} catch (UnknownHostException e) {
			LOGGER.log(Level.WARNING, "UnknownHostException.");
			return Optional.empty();
		}

		Message.PortNumber = UnsignedByteToInt(Raw[9], Raw[10]);

		if (Raw.length >= HEADER_SIZE) {
			int EndIndex = -1;
			for (int i = Raw.length - 1; i >= HEADER_SIZE; --i) {
				if (Raw[i] != 0) {
					EndIndex = i;
					break;
				}
			}
			if (EndIndex > 0) {
				Message.PayLoad = Optional.of(Arrays.copyOfRange(Raw, HEADER_SIZE, Raw.length));
			} else {
				Message.PayLoad = Optional.empty();
			}
		} else {
			Message.PayLoad = Optional.empty();
		}

		return Optional.of(Message);
	}

	public byte[] GenerateRaw() {
		final int PayLoadSize = PayLoad.isPresent() ? PayLoad.get().length : 0;

		ByteBuffer Buffer = ByteBuffer.allocate(HEADER_SIZE + PayLoadSize);

		Buffer.put((byte) PacketType.GetValue());

		Buffer.put(ToBytes(SequenceNumber));

		Buffer.put(Address.getAddress());

		Buffer.put(ToBytes(PortNumber));

		if (PayLoad.isPresent()) {
			Buffer.put(PayLoad.get());
		}

		return Buffer.array();
	}

	private static byte[] ToBytes(int IntValue) {
		final int Length = Integer.SIZE / 8;
		ByteBuffer Buffer = ByteBuffer.allocate(Length);
		// Ignore BufferOverflowException.
		Buffer.putInt(IntValue);
		return Arrays.copyOfRange(Buffer.array(), Length / 2, Length);
	}

	private static byte[] ToBytes(long LongValue) {
		final int Length = Long.SIZE / 8;
		ByteBuffer Buffer = ByteBuffer.allocate(Length);
		// Ignore BufferOverflowException.
		Buffer.putLong(LongValue);
		return Arrays.copyOfRange(Buffer.array(), Length / 2, Length);
	}

	private static short UnsignedByteToShort(byte b) {
		return (short) (b & 0xFF);
	}

	private static int UnsignedByteToInt(byte b0, byte b1) {
		return ((b0 & 0xFF) << 8) + ((b1 & 0xFF) << 0);
//		return (b0 << 8) + (b1 << 0);

	}

	private static long UnsignedByteToLong(byte b0, byte b1, byte b2, byte b3) {
		return ((b0 & 0xFF) << 24) + ((b1 & 0xFF) << 16) + ((b2 & 0xFF) << 8) + ((b3 & 0xFF) << 0);
//		return (b0 << 24) + (b1 << 16) + (b2 << 8) + (b3 << 0);
	}

	public void PrintAsUnsignedBytes() {
		final byte[] Raw = GenerateRaw();
		int i = 0;
		for (Byte b : Raw) {
			System.out.println(i++ + ": " + (short) (b & 0x00FF));
		}
	}

	public Boolean IsSyn() {
		return PacketType == EUdpPacketType.Syn;
	}

	public Boolean IsSynAck() {
		return PacketType == EUdpPacketType.SynAck;
	}

	public InetSocketAddress GetSocketAddress() {
		return new InetSocketAddress(Address, PortNumber);
	}

	public String toString() {
		String Output = "";
		Output += "PacketType: " + PacketType.GetValue() + "\n";
		Output += "SequenceNumber: " + SequenceNumber + "\n";
		Output += "Address: " + Address.toString() + "\n";
		Output += "PortNumber: " + PortNumber + "\n";
		if (PayLoad.isPresent()) {
			Output += "PayLoad:\n" + new String(PayLoad.get()) + "\n";
		} else {
			Output += "No PayLoad\n";
		}
		return Output;
	}
}
