package Client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Optional;

import Common.EUdpPacketType;
import Common.HttpRequest;
import Common.HttpResponse;
import Common.UdpMessage;

public class HttpC {
	public static void main(String args[]) throws UnknownHostException {

		UdpMessage Msg = new UdpMessage(EUdpPacketType.Data, (long) 1,
				InetAddress.getByAddress(new byte[] { (byte) 127, (byte) 0, (byte) 0, (byte) 1 }), (int) 8007,
				new byte[] { (byte) 75, (byte) 105, (byte) 32, (byte) 83 });

		byte[] Raw = Msg.GenerateRaw().get();

		System.out.print(Msg.toString());

		// Msg.PrintAsUnsignedBytes();

		InetAddress Address = InetAddress.getByAddress(new byte[] { (byte) 0, (byte) 0, (byte) 0, (byte) 0 });

		try {
			DatagramChannel OutChannel = DatagramChannel.open();
			OutChannel.configureBlocking(false);
			// Port 0 will select any available one.
			OutChannel.bind(new InetSocketAddress(0));
			System.out.println("Sending address: " + OutChannel.getLocalAddress());
			SocketAddress Server = new InetSocketAddress("127.0.0.1", 3001);
			ByteBuffer Buffer = ByteBuffer.allocate(Raw.length);
			Buffer.put(Raw);
			Buffer.clear();
			OutChannel.send(Buffer, Server);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Print help.
		if (args.length > 0 && args[0].equals("help")) {
			if (args.length > 1) {
				PrintHelp(args[1]);
			} else {
				PrintHelp("");
			}
			return;
		}

		HttpRequest HttpRequest = new HttpRequest(args);

		if (!HttpRequest.IsValid()) {
			if (HttpRequest.GetError().isPresent()) {
				System.out.println(HttpRequest.GetError().get());
			} else {
				System.out.println("ERROR: Unknown issue with options.");
			}
			// PrintHelp("");
			return;
		}

		Optional<HttpResponse> Response;
		try {
			Response = HttpRequest.Send();
		} catch (IOException e) {
			System.out.println("ERROR: Could not send HTTP Request:");
			e.printStackTrace();
			return;
		}

		if (Response.isEmpty() || !Response.get().IsValid()) {
			// PrintHelp("");
			return;
		}

		while (Response.get().IsRedirect()) {
			final Optional<String> RedirectLocation = Response.get().GetRedirectLocation();
			if (RedirectLocation.isPresent()) {
				HttpRequest.Redirect(RedirectLocation.get());
			} else {
				System.out.println("ERROR: Could not redirect.");
			}
			if (!HttpRequest.IsValid()) {
				return;
			}
			try {
				Response = HttpRequest.Send();
			} catch (IOException e) {
				System.out.println("ERROR: Could not send HTTP Request:");
				e.printStackTrace();
				return;
			}
			if (Response.isEmpty() || !Response.get().IsValid()) {
				return;
			}
		}
	}

	public static void PrintHelp(String HttpOperation) {
		if (HttpOperation.toLowerCase().equals("get")) {
			System.out.println("usage: httpc get [-v] [-h key:value] URL\n"
					+ "Get executes a HTTP GET request for a given URL.\n"
					+ "    -v            Prints the detail of the response such as protocol, status, and headers.\n"
					+ "    -h key:value  Associates headers to HTTP Request with the format 'key:value'.\n"
					+ "    -o file       Write the body of the response to the specified file instead of concole.\n");
		} else if (HttpOperation.toLowerCase().equals("post")) {
			System.out.println("usage: httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL\n"
					+ "Post executes a HTTP POST request for a given URL with inline data or from file.\n"
					+ "    -v            Prints the detail of the response such as protocol, status, and headers.\n"
					+ "    -h key:value  Associates headers to HTTP Request with the format 'key:value'.\n"
					+ "    -d string     Associates an inline data to the body HTTP POST request.\n"
					+ "    -f file       Associates the content of a file to the body HTTP POST request.\n"
					+ "    -o file       Write the body of the response to the specified file instead of concole.\n"
					+ "Either [-d] or [-f] can be used but not both.\n");
		} else {
			System.out.println("httpc is a curl-like application but supports HTTP protocol only.\n" + "Usage:\n"
					+ "    httpc command [arguments]\n" + "The commands are:\n"
					+ "    get executes a HTTP GET request and prints the response.\n"
					+ "    post executes a HTTP POST request and prints the response.\n"
					+ "    help prints this screen. Use \"httpc help [command]\" for more information about a command.\n");
		}

	}
}