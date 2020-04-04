package Common;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class Constants {

	public static SocketAddress ROUTER_ADDRESS = new InetSocketAddress("127.0.0.1", 3001);
	public static InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 8007);
	public static int DEFAULT_TIMEOUT = 1000;
	public static int RETRANSMISSION_ATTEMPTS = 10;
	public static int WINDOW_SIZE = 8;
	public static int INITIAL_RTT = 1000; // ms

}
